/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.datafeed;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentElasticsearchExtension;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.core.internal.io.Streams;
import org.elasticsearch.index.mapper.DateFieldMapper;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.xpack.core.ml.action.FlushJobAction;
import org.elasticsearch.xpack.core.ml.action.PersistJobAction;
import org.elasticsearch.xpack.core.ml.action.PostDataAction;
import org.elasticsearch.xpack.core.ml.annotations.Annotation;
import org.elasticsearch.xpack.core.ml.annotations.AnnotationIndex;
import org.elasticsearch.xpack.core.ml.datafeed.extractor.DataExtractor;
import org.elasticsearch.xpack.core.ml.job.config.DataDescription;
import org.elasticsearch.xpack.core.ml.job.messages.Messages;
import org.elasticsearch.xpack.core.ml.job.persistence.ElasticsearchMappings;
import org.elasticsearch.xpack.core.ml.job.process.autodetect.state.DataCounts;
import org.elasticsearch.xpack.core.ml.job.results.Bucket;
import org.elasticsearch.xpack.core.security.user.XPackUser;
import org.elasticsearch.xpack.ml.datafeed.delayeddatacheck.DelayedDataDetector;
import org.elasticsearch.xpack.ml.datafeed.delayeddatacheck.DelayedDataDetectorFactory.BucketWithMissingData;
import org.elasticsearch.xpack.ml.datafeed.extractor.DataExtractorFactory;
import org.elasticsearch.xpack.ml.notifications.Auditor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.elasticsearch.xpack.core.ClientHelper.ML_ORIGIN;
import static org.elasticsearch.xpack.core.ClientHelper.stashWithOrigin;

class DatafeedJob {

    private static final Logger LOGGER = LogManager.getLogger(DatafeedJob.class);
    private static final int NEXT_TASK_DELAY_MS = 100;
    static final long MISSING_DATA_CHECK_INTERVAL_MS = 900_000; //15 minutes in ms

    private final Auditor auditor;
    private final String jobId;
    private final DataDescription dataDescription;
    private final long frequencyMs;
    private final long queryDelayMs;
    private final Client client;
    private final DataExtractorFactory dataExtractorFactory;
    private final Supplier<Long> currentTimeSupplier;
    private final DelayedDataDetector delayedDataDetector;

    private volatile long lookbackStartTimeMs;
    private volatile long latestFinalBucketEndTimeMs;
    private volatile long lastDataCheckTimeMs;
    private volatile String lastDataCheckAnnotationId;
    private volatile Annotation lastDataCheckAnnotation;
    private volatile Long lastEndTimeMs;
    private AtomicBoolean running = new AtomicBoolean(true);
    private volatile boolean isIsolated;

    DatafeedJob(String jobId, DataDescription dataDescription, long frequencyMs, long queryDelayMs,
                DataExtractorFactory dataExtractorFactory, Client client, Auditor auditor, Supplier<Long> currentTimeSupplier,
                DelayedDataDetector delayedDataDetector, long latestFinalBucketEndTimeMs, long latestRecordTimeMs) {
        this.jobId = jobId;
        this.dataDescription = Objects.requireNonNull(dataDescription);
        this.frequencyMs = frequencyMs;
        this.queryDelayMs = queryDelayMs;
        this.dataExtractorFactory = dataExtractorFactory;
        this.client = client;
        this.auditor = auditor;
        this.currentTimeSupplier = currentTimeSupplier;
        this.delayedDataDetector = delayedDataDetector;
        this.latestFinalBucketEndTimeMs = latestFinalBucketEndTimeMs;
        long lastEndTime = Math.max(latestFinalBucketEndTimeMs, latestRecordTimeMs);
        if (lastEndTime > 0) {
            lastEndTimeMs = lastEndTime;
        }
    }

    void isolate() {
        isIsolated = true;
    }

    boolean isIsolated() {
        return isIsolated;
    }

    public String getJobId() {
        return jobId;
    }

    Long runLookBack(long startTime, Long endTime) throws Exception {
        lookbackStartTimeMs = skipToStartTime(startTime);
        Optional<Long> endMs = Optional.ofNullable(endTime);
        long lookbackEnd = endMs.orElse(currentTimeSupplier.get() - queryDelayMs);
        boolean isLookbackOnly = endMs.isPresent();
        if (lookbackEnd <= lookbackStartTimeMs) {
            if (isLookbackOnly) {
                return null;
            } else {
                auditor.info(jobId, Messages.getMessage(Messages.JOB_AUDIT_DATAFEED_STARTED_REALTIME));
                return nextRealtimeTimestamp();
            }
        }

        String msg = Messages.getMessage(Messages.JOB_AUDIT_DATAFEED_STARTED_FROM_TO,
                DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER.formatMillis(lookbackStartTimeMs),
                endTime == null ? "real-time" : DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER.formatMillis(lookbackEnd),
                TimeValue.timeValueMillis(frequencyMs).getStringRep());
        auditor.info(jobId, msg);
        LOGGER.info("[{}] {}", jobId, msg);


        FlushJobAction.Request request = new FlushJobAction.Request(jobId);
        request.setCalcInterim(true);
        run(lookbackStartTimeMs, lookbackEnd, request);
        if (shouldPersistAfterLookback(isLookbackOnly)) {
            sendPersistRequest();
        }

        if (isRunning() && !isIsolated) {
            auditor.info(jobId, Messages.getMessage(Messages.JOB_AUDIT_DATAFEED_LOOKBACK_COMPLETED));
            LOGGER.info("[{}] Lookback has finished", jobId);
            if (isLookbackOnly) {
                return null;
            } else {
                auditor.info(jobId, Messages.getMessage(Messages.JOB_AUDIT_DATAFEED_CONTINUED_REALTIME));
                return nextRealtimeTimestamp();
            }
        }
        if (!isIsolated) {
            LOGGER.debug("[{}] Lookback finished after being stopped", jobId);
        }
        return null;
    }

    private long skipToStartTime(long startTime) {
        if (lastEndTimeMs != null) {
            if (lastEndTimeMs + 1 > startTime) {
                // start time is before last checkpoint, thus continue from checkpoint
                return lastEndTimeMs + 1;
            }
            // start time is after last checkpoint, thus we need to skip time
            FlushJobAction.Request request = new FlushJobAction.Request(jobId);
            request.setSkipTime(String.valueOf(startTime));
            FlushJobAction.Response flushResponse = flushJob(request);
            LOGGER.info("[{}] Skipped to time [{}]", jobId, flushResponse.getLastFinalizedBucketEnd().getTime());
            return flushResponse.getLastFinalizedBucketEnd().getTime();
        }
        return startTime;
    }

    long runRealtime() throws Exception {
        long start = lastEndTimeMs == null ? lookbackStartTimeMs : Math.max(lookbackStartTimeMs, lastEndTimeMs + 1);
        long nowMinusQueryDelay = currentTimeSupplier.get() - queryDelayMs;
        long end = toIntervalStartEpochMs(nowMinusQueryDelay);
        FlushJobAction.Request request = new FlushJobAction.Request(jobId);
        request.setCalcInterim(true);
        request.setAdvanceTime(String.valueOf(end));
        run(start, end, request);
        checkForMissingDataIfNecessary();
        return nextRealtimeTimestamp();
    }

    private void checkForMissingDataIfNecessary() {
        if (isRunning() && !isIsolated && checkForMissingDataTriggered()) {

            // Keep track of the last bucket time for which we did a missing data check
            this.lastDataCheckTimeMs = this.currentTimeSupplier.get();
            List<BucketWithMissingData> missingDataBuckets = delayedDataDetector.detectMissingData(latestFinalBucketEndTimeMs);
            if (missingDataBuckets.isEmpty() == false) {
                long totalRecordsMissing = missingDataBuckets.stream()
                    .mapToLong(BucketWithMissingData::getMissingDocumentCount)
                    .sum();
                Bucket lastBucket = missingDataBuckets.get(missingDataBuckets.size() - 1).getBucket();
                // Get the end of the last bucket and make it milliseconds
                Date endTime = new Date((lastBucket.getEpoch() + lastBucket.getBucketSpan()) * 1000);

                String msg = Messages.getMessage(Messages.JOB_AUDIT_DATAFEED_MISSING_DATA, totalRecordsMissing,
                    XContentElasticsearchExtension.DEFAULT_DATE_PRINTER.print(lastBucket.getTimestamp().getTime()));

                Annotation annotation = createAnnotation(missingDataBuckets.get(0).getBucket().getTimestamp(), endTime, msg);

                // Have we an annotation that covers the same area with the same message?
                // Cannot use annotation.equals(other) as that checks createTime
                if (lastDataCheckAnnotation != null
                    && annotation.getAnnotation().equals(lastDataCheckAnnotation.getAnnotation())
                    && annotation.getTimestamp().equals(lastDataCheckAnnotation.getTimestamp())
                    && annotation.getEndTimestamp().equals(lastDataCheckAnnotation.getEndTimestamp())) {
                    return;
                }

                // Creating a warning in addition to updating/creating our annotation. This allows the issue to be plainly visible
                // in the job list page.
                auditor.warning(jobId, msg);

                if (lastDataCheckAnnotationId != null) {
                    updateAnnotation(annotation);
                } else {
                    lastDataCheckAnnotationId = addAndSetDelayedDataAnnotation(annotation);
                }
            }
        }
    }

    private Annotation createAnnotation(Date startTime, Date endTime, String msg) {
       Date currentTime = new Date(currentTimeSupplier.get());
       return new Annotation(msg,
           currentTime,
           XPackUser.NAME,
           startTime,
           endTime,
           jobId,
           currentTime,
           XPackUser.NAME,
           "annotation");
    }

    private String addAndSetDelayedDataAnnotation(Annotation annotation) {
        try (XContentBuilder xContentBuilder = annotation.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS)) {
            IndexRequest request = new IndexRequest(AnnotationIndex.WRITE_ALIAS_NAME, ElasticsearchMappings.DOC_TYPE);
            request.source(xContentBuilder);
            try (ThreadContext.StoredContext ignore = stashWithOrigin(client.threadPool().getThreadContext(), ML_ORIGIN)) {
                IndexResponse response = client.index(request).actionGet();
                lastDataCheckAnnotation = annotation;
                return response.getId();
            }
        } catch (IOException ex) {
            String errorMessage = "[" + jobId + "] failed to create annotation for delayed data checker.";
            LOGGER.error(errorMessage, ex);
            auditor.error(jobId, errorMessage);
            return null;
        }
    }

    private void updateAnnotation(Annotation annotation) {
        Annotation updatedAnnotation = new Annotation(lastDataCheckAnnotation);
        updatedAnnotation.setModifiedUsername(XPackUser.NAME);
        updatedAnnotation.setModifiedTime(new Date(currentTimeSupplier.get()));
        updatedAnnotation.setAnnotation(annotation.getAnnotation());
        updatedAnnotation.setTimestamp(annotation.getTimestamp());
        updatedAnnotation.setEndTimestamp(annotation.getEndTimestamp());
        try (XContentBuilder xContentBuilder = updatedAnnotation.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS)) {
            IndexRequest indexRequest = new IndexRequest(AnnotationIndex.WRITE_ALIAS_NAME, ElasticsearchMappings.DOC_TYPE);
            indexRequest.id(lastDataCheckAnnotationId);
            indexRequest.source(xContentBuilder);
            try (ThreadContext.StoredContext ignore = stashWithOrigin(client.threadPool().getThreadContext(), ML_ORIGIN)) {
                client.index(indexRequest).actionGet();
                lastDataCheckAnnotation = updatedAnnotation;
            }
        } catch (IOException ex) {
            String errorMessage = "[" + jobId + "] failed to update annotation for delayed data checker.";
            LOGGER.error(errorMessage, ex);
            auditor.error(jobId, errorMessage);
        }
    }

    /**
     * We wait a static interval of 15 minutes till the next missing data check.
     *
     * However, if our delayed data window is smaller than that, we will probably want to check at every available window (if freq. allows).
     * This is to help to miss as few buckets in the delayed data check as possible.
     *
     * If our frequency/query delay are longer then our default interval or window size, we will end up looking for missing data on
     * every real-time trigger. This should be OK as the we are pulling from the Index as such a slow pace, another query will
     * probably not even be noticeable at such a large timescale.
     */
    private boolean checkForMissingDataTriggered() {
        return this.currentTimeSupplier.get() > this.lastDataCheckTimeMs
            + Math.min(MISSING_DATA_CHECK_INTERVAL_MS, delayedDataDetector.getWindow());
    }

    /**
     * Stops the datafeed job
     *
     * @return <code>true</code> when the datafeed was running and this method invocation stopped it,
     *         otherwise <code>false</code> is returned
     */
    public boolean stop() {
        if (running.compareAndSet(true, false)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void run(long start, long end, FlushJobAction.Request flushRequest) throws IOException {
        if (end <= start) {
            return;
        }

        LOGGER.trace("[{}] Searching data in: [{}, {})", jobId, start, end);

        // A storage for errors that should only be thrown after advancing time
        RuntimeException error = null;

        long recordCount = 0;
        DataExtractor dataExtractor = dataExtractorFactory.newExtractor(start, end);
        while (dataExtractor.hasNext()) {
            if ((isIsolated || !isRunning()) && !dataExtractor.isCancelled()) {
                dataExtractor.cancel();
            }
            if (isIsolated) {
                return;
            }

            Optional<InputStream> extractedData;
            try {
                extractedData = dataExtractor.next();
            } catch (Exception e) {
                LOGGER.debug("[" + jobId + "] error while extracting data", e);
                // When extraction problems are encountered, we do not want to advance time.
                // Instead, it is preferable to retry the given interval next time an extraction
                // is triggered.

                // For aggregated datafeeds it is possible for our users to use fields without doc values.
                // In that case, it is really useful to display an error message explaining exactly that.
                // Unfortunately, there are no great ways to identify the issue but search for 'doc values'
                // deep in the exception.
                if (e.toString().contains("doc values")) {
                    throw new ExtractionProblemException(nextRealtimeTimestamp(), new IllegalArgumentException(
                            "One or more fields do not have doc values; please enable doc values for all analysis fields for datafeeds" +
                                    " using aggregations"));
                }
                throw new ExtractionProblemException(nextRealtimeTimestamp(), e);
            }
            if (isIsolated) {
                return;
            }
            if (extractedData.isPresent()) {
                DataCounts counts;
                try (InputStream in = extractedData.get()) {
                    counts = postData(in, XContentType.JSON);
                    LOGGER.trace("[{}] Processed another {} records", jobId, counts.getProcessedRecordCount());
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    if (isIsolated) {
                        return;
                    }
                    LOGGER.debug("[" + jobId + "] error while posting data", e);

                    // a conflict exception means the job state is not open any more.
                    // we should therefore stop the datafeed.
                    boolean shouldStop = isConflictException(e);

                    // When an analysis problem occurs, it means something catastrophic has
                    // happened to the c++ process. We sent a batch of data to the c++ process
                    // yet we do not know how many of those were processed. It is better to
                    // advance time in order to avoid importing duplicate data.
                    error = new AnalysisProblemException(nextRealtimeTimestamp(), shouldStop, e);
                    break;
                }
                recordCount += counts.getProcessedRecordCount();
                if (counts.getLatestRecordTimeStamp() != null) {
                    lastEndTimeMs = counts.getLatestRecordTimeStamp().getTime();
                }
            }
        }

        lastEndTimeMs = Math.max(lastEndTimeMs == null ? 0 : lastEndTimeMs, end - 1);
        LOGGER.debug("[{}] Complete iterating data extractor [{}], [{}], [{}], [{}], [{}]", jobId, error, recordCount,
                lastEndTimeMs, isRunning(), dataExtractor.isCancelled());

        // We can now throw any stored error as we have updated time.
        if (error != null) {
            throw error;
        }

        // If the datafeed was stopped, then it is possible that by the time
        // we call flush the job is closed. Thus, we don't flush unless the
        // datafeed is still running.
        if (isRunning() && !isIsolated) {
            Date lastFinalizedBucketEnd = flushJob(flushRequest).getLastFinalizedBucketEnd();
            if (lastFinalizedBucketEnd != null) {
                this.latestFinalBucketEndTimeMs = lastFinalizedBucketEnd.getTime();
            }
        }

        if (recordCount == 0) {
            throw new EmptyDataCountException(nextRealtimeTimestamp());
        }
    }

    private DataCounts postData(InputStream inputStream, XContentType xContentType)
            throws IOException {
        PostDataAction.Request request = new PostDataAction.Request(jobId);
        request.setDataDescription(dataDescription);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Streams.copy(inputStream, outputStream);
        request.setContent(new BytesArray(outputStream.toByteArray()), xContentType);
        try (ThreadContext.StoredContext ignore = stashWithOrigin(client.threadPool().getThreadContext(), ML_ORIGIN)) {
            PostDataAction.Response response = client.execute(PostDataAction.INSTANCE, request).actionGet();
            return response.getDataCounts();
        }
    }

    private boolean isConflictException(Exception e) {
        return e instanceof ElasticsearchStatusException
                && ((ElasticsearchStatusException) e).status() == RestStatus.CONFLICT;
    }

    private long nextRealtimeTimestamp() {
        // We find the timestamp of the start of the next frequency interval.
        // The goal is to minimize any lag. To do so,
        // we offset the time by the query delay modulo frequency.
        // For example, if frequency is 60s and query delay 90s,
        // we run 30s past the minute. If frequency is 1s and query delay 10s,
        // we don't add anything and we'll run every second.
        long next = currentTimeSupplier.get() + frequencyMs;
        return toIntervalStartEpochMs(next) + queryDelayMs % frequencyMs + NEXT_TASK_DELAY_MS;
    }

    private long toIntervalStartEpochMs(long epochMs) {
        return (epochMs / frequencyMs) * frequencyMs;
    }

    private FlushJobAction.Response flushJob(FlushJobAction.Request flushRequest) {
        try {
            LOGGER.trace("[" + jobId + "] Sending flush request");
            try (ThreadContext.StoredContext ignore = stashWithOrigin(client.threadPool().getThreadContext(), ML_ORIGIN)) {
                return client.execute(FlushJobAction.INSTANCE, flushRequest).actionGet();
            }
        } catch (Exception e) {
            LOGGER.debug("[" + jobId + "] error while flushing job", e);

            // a conflict exception means the job state is not open any more.
            // we should therefore stop the datafeed.
            boolean shouldStop = isConflictException(e);

            // When an analysis problem occurs, it means something catastrophic has
            // happened to the c++ process. We sent a batch of data to the c++ process
            // yet we do not know how many of those were processed. It is better to
            // advance time in order to avoid importing duplicate data.
            throw new AnalysisProblemException(nextRealtimeTimestamp(), shouldStop, e);
        }
    }

    private boolean shouldPersistAfterLookback(boolean isLookbackOnly) {
        return isLookbackOnly == false && isIsolated == false && isRunning();
    }

    private void sendPersistRequest() {
        try {
            LOGGER.trace("[" + jobId + "] Sending persist request");
            try (ThreadContext.StoredContext ignore = stashWithOrigin(client.threadPool().getThreadContext(), ML_ORIGIN)) {
                client.execute(PersistJobAction.INSTANCE, new PersistJobAction.Request(jobId));
            }
        } catch (Exception e) {
            LOGGER.debug("[" + jobId + "] error while persisting job", e);
        }
    }

    /**
     * Visible for testing
     */
    Long lastEndTimeMs() {
        return lastEndTimeMs;
    }

    static class AnalysisProblemException extends RuntimeException {

        final boolean shouldStop;
        final long nextDelayInMsSinceEpoch;

        AnalysisProblemException(long nextDelayInMsSinceEpoch, boolean shouldStop, Throwable cause) {
            super(cause);
            this.shouldStop = shouldStop;
            this.nextDelayInMsSinceEpoch = nextDelayInMsSinceEpoch;
        }
    }

    static class ExtractionProblemException extends RuntimeException {

        final long nextDelayInMsSinceEpoch;

        ExtractionProblemException(long nextDelayInMsSinceEpoch, Throwable cause) {
            super(cause);
            this.nextDelayInMsSinceEpoch = nextDelayInMsSinceEpoch;
        }
    }

    static class EmptyDataCountException extends RuntimeException {

        final long nextDelayInMsSinceEpoch;

        EmptyDataCountException(long nextDelayInMsSinceEpoch) {
            this.nextDelayInMsSinceEpoch = nextDelayInMsSinceEpoch;
        }
    }

}
