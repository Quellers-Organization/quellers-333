/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.profiling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.action.support.ThreadedActionListener;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.client.internal.ParentTaskAssigningClient;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.InternalNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.MinAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Sum;
import org.elasticsearch.search.aggregations.metrics.SumAggregationBuilder;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xcontent.ObjectPath;
import org.elasticsearch.xpack.countedkeyword.CountedTermsAggregationBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class TransportGetStackTracesAction extends HandledTransportAction<GetStackTracesRequest, GetStackTracesResponse> {
    private static final Logger log = LogManager.getLogger(TransportGetStackTracesAction.class);

    public static final Setting<Integer> PROFILING_MAX_STACKTRACE_QUERY_SLICES = Setting.intSetting(
        "xpack.profiling.query.stacktrace.max_slices",
        16,
        1,
        Setting.Property.NodeScope
    );

    public static final Setting<Integer> PROFILING_MAX_DETAIL_QUERY_SLICES = Setting.intSetting(
        "xpack.profiling.query.details.max_slices",
        16,
        1,
        Setting.Property.NodeScope
    );

    public static final Setting<Boolean> PROFILING_QUERY_REALTIME = Setting.boolSetting(
        "xpack.profiling.query.realtime",
        true,
        Setting.Property.NodeScope
    );

    /**
     * K/V indices (such as profiling-stacktraces) are assumed to contain data from their creation date until the creation date
     * of the next index that is created by rollover. Due to client-side caching of K/V data we need to extend the validity period
     * of the prior index by this time. This means that for queries that cover a time period around the time when a new index has
     * been created we will query not only the new index but also the prior one. The client-side parameters that influence cache duration
     * are <code>elfInfoCacheTTL</code> for executables (default: 6 hours) and <code>traceExpirationTimeout</code> for stack
     * traces (default: 3 hours).
     */
    public static final Setting<TimeValue> PROFILING_KV_INDEX_OVERLAP = Setting.positiveTimeSetting(
        "xpack.profiling.kv_index.overlap",
        TimeValue.timeValueHours(6),
        Setting.Property.NodeScope
    );

    private final NodeClient nodeClient;
    private final ProfilingLicenseChecker licenseChecker;
    private final CostsService costsService;
    private final ClusterService clusterService;
    private final TransportService transportService;
    private final Executor responseExecutor;

    private final KvIndexResolver resolver;
    private final int desiredSlices;
    private final int desiredDetailSlices;
    private final boolean realtime;
    private static final Map<String, HostMetadata> hostsTable = new ConcurrentHashMap<>();

    @Inject
    public TransportGetStackTracesAction(
        Settings settings,
        ThreadPool threadPool,
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        NodeClient nodeClient,
        ProfilingLicenseChecker licenseChecker,
        CostsService costsService,
        IndexNameExpressionResolver resolver
    ) {
        super(GetStackTracesAction.NAME, transportService, actionFilters, GetStackTracesRequest::new, EsExecutors.DIRECT_EXECUTOR_SERVICE);
        this.nodeClient = nodeClient;
        this.licenseChecker = licenseChecker;
        this.costsService = costsService;
        this.clusterService = clusterService;
        this.transportService = transportService;
        this.responseExecutor = threadPool.executor(ProfilingPlugin.PROFILING_THREAD_POOL_NAME);
        this.resolver = new KvIndexResolver(resolver, PROFILING_KV_INDEX_OVERLAP.get(settings));
        this.desiredSlices = PROFILING_MAX_STACKTRACE_QUERY_SLICES.get(settings);
        this.desiredDetailSlices = PROFILING_MAX_DETAIL_QUERY_SLICES.get(settings);
        this.realtime = PROFILING_QUERY_REALTIME.get(settings);
    }

    @Override
    protected void doExecute(Task submitTask, GetStackTracesRequest request, ActionListener<GetStackTracesResponse> submitListener) {
        licenseChecker.requireSupportedLicense();
        Client client = new ParentTaskAssigningClient(this.nodeClient, transportService.getLocalNode(), submitTask);
        if (request.getIndices() == null) {
            searchProfilingEvents(request, submitListener, client);
        } else {
            searchGenericEvents(request, submitListener, client);
        }
    }

    private void searchProfilingEvents(
        GetStackTracesRequest request,
        ActionListener<GetStackTracesResponse> submitListener,
        Client client
    ) {
        StopWatch watch = new StopWatch("getResampledIndex");
        EventsIndex mediumDownsampled = EventsIndex.MEDIUM_DOWNSAMPLED;
        client.prepareSearch(mediumDownsampled.getName())
            .setSize(0)
            .setQuery(request.getQuery())
            .setTrackTotalHits(true)
            .execute(ActionListener.wrap(searchResponse -> {
                long sampleCount = searchResponse.getHits().getTotalHits().value;
                EventsIndex resampledIndex = mediumDownsampled.getResampledIndex(request.getSampleSize(), sampleCount);
                log.debug(
                    "User requested [{}] samples, [{}] samples matched in [{}]. Picking [{}]",
                    request.getSampleSize(),
                    sampleCount,
                    mediumDownsampled,
                    resampledIndex
                );
                log.debug(watch::report);
                searchEventGroupedByStackTrace(client, request, resampledIndex, submitListener);
            }, e -> {
                // All profiling-events data streams are created lazily. In a relatively empty cluster it can happen that there are so few
                // data that we need to resort to the "full" events stream. As this is an edge case we'd rather fail instead of prematurely
                // checking for existence in all cases.
                if (e instanceof IndexNotFoundException) {
                    String missingIndex = ((IndexNotFoundException) e).getIndex().getName();
                    EventsIndex fullIndex = EventsIndex.FULL_INDEX;
                    log.debug("Index [{}] does not exist. Using [{}] instead.", missingIndex, fullIndex.getName());
                    searchEventGroupedByStackTrace(client, request, fullIndex, submitListener);
                } else {
                    submitListener.onFailure(e);
                }
            }));
    }

    private void searchGenericEvents(GetStackTracesRequest request, ActionListener<GetStackTracesResponse> submitListener, Client client) {
        GetStackTracesResponseBuilder responseBuilder = new GetStackTracesResponseBuilder();
        responseBuilder.setSamplingRate(1.0d);
        client.prepareSearch(request.indices())
            .setTrackTotalHits(false)
            .setSize(0)
            .setQuery(request.getQuery())
            .addAggregation(new MinAggregationBuilder("min_time").field("@timestamp"))
            .addAggregation(new MaxAggregationBuilder("max_time").field("@timestamp"))
            .addAggregation(
                new CountedTermsAggregationBuilder("group_by")
                    // 'size' should be max 100k, but might be slightly more. Better be on the safe side.
                    .size(150_000)
                    .field(request.getStackTraceIds())
            )
            .execute(handleEventsGroupedByStackTrace(client, responseBuilder, submitListener, searchResponse -> {
                long totalSamples = 0;
                StringTerms stacktraces = searchResponse.getAggregations().get("group_by");
                List<HostEventCount> hostEventCounts = new ArrayList<>(102400); // Preparation for future host.id aggregation
                Map<String, TraceEvent> stackTraceEvents = new TreeMap<>();
                for (StringTerms.Bucket stacktraceBucket : stacktraces.getBuckets()) {
                    long count = stacktraceBucket.getDocCount();
                    totalSamples += count;

                    String stackTraceID = stacktraceBucket.getKeyAsString();
                    TraceEvent event = stackTraceEvents.get(stackTraceID);
                    if (event == null) {
                        event = new TraceEvent(stackTraceID);
                        stackTraceEvents.put(stackTraceID, event);
                    }
                    event.count += count;
                }
                responseBuilder.setTotalSamples(totalSamples);
                responseBuilder.setHostEventCounts(hostEventCounts);
                return stackTraceEvents;
            }));
    }

    private void searchEventGroupedByStackTrace(
        Client client,
        GetStackTracesRequest request,
        EventsIndex eventsIndex,
        ActionListener<GetStackTracesResponse> submitListener
    ) {
        GetStackTracesResponseBuilder responseBuilder = new GetStackTracesResponseBuilder();
        responseBuilder.setSamplingRate(eventsIndex.getSampleRate());
        responseBuilder.setRequestedDuration(request.getRequestedDuration());
        client.prepareSearch(eventsIndex.getName())
            .setTrackTotalHits(false)
            .setSize(0)
            .setQuery(request.getQuery())
            .addAggregation(new MinAggregationBuilder("min_time").field("@timestamp"))
            .addAggregation(new MaxAggregationBuilder("max_time").field("@timestamp"))
            .addAggregation(
                new TermsAggregationBuilder("group_by")
                    // 'size' should be max 100k, but might be slightly more. Better be on the safe side.
                    .size(150_000)
                    .field("host.id")
                    // 'execution_hint: map' skips the slow building of ordinals that we don't need.
                    // Especially with high cardinality fields, this makes aggregations really slow.
                    .executionHint("map")
                    .subAggregation(
                        new TermsAggregationBuilder("group_by").size(150_000)
                            .field("Stacktrace.id")
                            // 'execution_hint: map' skips the slow building of ordinals that we don't need.
                            // Especially with high cardinality fields, this makes aggregations really slow.
                            .executionHint("map")
                            .subAggregation(new SumAggregationBuilder("count").field("Stacktrace.count"))
                    )
            )
            .addAggregation(new SumAggregationBuilder("total_count").field("Stacktrace.count"))
            .execute(handleEventsGroupedByStackTrace(client, responseBuilder, submitListener, searchResponse -> {
                long totalCount = getLongFromResponse(searchResponse, "total_count");

                Resampler resampler = new Resampler(request, responseBuilder.getSamplingRate(), totalCount);
                StringTerms hosts = searchResponse.getAggregations().get("group_by");

                // Sort items lexicographically to access Lucene's term dictionary more efficiently when issuing an mget request.
                // The term dictionary is lexicographically sorted and using the same order reduces the number of page faults
                // needed to load it.
                long totalFinalCount = 0;
                List<HostEventCount> hostEventCounts = new ArrayList<>(102400);
                Map<String, TraceEvent> stackTraceEvents = new TreeMap<>();
                for (StringTerms.Bucket hostBucket : hosts.getBuckets()) {
                    String hostid = hostBucket.getKeyAsString();

                    StringTerms stacktraces = hostBucket.getAggregations().get("group_by");
                    for (StringTerms.Bucket stacktraceBucket : stacktraces.getBuckets()) {
                        Sum count = stacktraceBucket.getAggregations().get("count");
                        int finalCount = resampler.adjustSampleCount((int) count.value());
                        if (finalCount <= 0) {
                            continue;
                        }
                        totalFinalCount += finalCount;

                        /*
                        The same stacktraces may come from different hosts (eventually from different datacenters).
                        We make a list of the triples here. As soon as we have the host metadata, we can calculate
                        the CO2 emission and the costs for each TraceEvent.
                         */
                        String stackTraceID = stacktraceBucket.getKeyAsString();
                        hostEventCounts.add(new HostEventCount(hostid, stackTraceID, finalCount));

                        TraceEvent event = stackTraceEvents.get(stackTraceID);
                        if (event == null) {
                            event = new TraceEvent(stackTraceID);
                            stackTraceEvents.put(stackTraceID, event);
                        }
                        event.count += finalCount;
                    }
                }
                responseBuilder.setTotalSamples(totalFinalCount);
                responseBuilder.setHostEventCounts(hostEventCounts);
                log.debug(
                    "Found [{}] stacktrace events, resampled with sample rate [{}] to [{}] events ([{}] unique stack traces).",
                    totalCount,
                    responseBuilder.getSamplingRate(),
                    totalFinalCount,
                    stackTraceEvents.size()
                );
                return stackTraceEvents;
            }));
    }

    private ActionListener<SearchResponse> handleEventsGroupedByStackTrace(
        Client client,
        GetStackTracesResponseBuilder responseBuilder,
        ActionListener<GetStackTracesResponse> submitListener,
        Function<SearchResponse, Map<String, TraceEvent>> stacktraceCollector
    ) {
        StopWatch watch = new StopWatch("eventsGroupedByStackTrace");
        return ActionListener.wrap(searchResponse -> {
            long minTime = getLongFromResponse(searchResponse, "min_time");
            long maxTime = getLongFromResponse(searchResponse, "max_time");

            Map<String, TraceEvent> stackTraceEvents = stacktraceCollector.apply(searchResponse);

            log.debug(watch::report);
            if (stackTraceEvents.isEmpty() == false) {
                long totalSamples = stackTraceEvents.values().stream().mapToLong(v -> v.count).sum();
                responseBuilder.setTotalSamples(totalSamples);
                responseBuilder.setStart(Instant.ofEpochMilli(minTime));
                responseBuilder.setEnd(Instant.ofEpochMilli(maxTime));
                responseBuilder.setStackTraceEvents(stackTraceEvents);
                retrieveStackTraces(client, responseBuilder, submitListener);
            } else {
                submitListener.onResponse(responseBuilder.build());
            }
        }, e -> {
            // Data streams are created lazily; if even the "full" index does not exist no data have been indexed yet.
            if (e instanceof IndexNotFoundException) {
                log.debug("Index [{}] does not exist. Returning empty response.", ((IndexNotFoundException) e).getIndex());
                submitListener.onResponse(responseBuilder.build());
            } else {
                submitListener.onFailure(e);
            }
        });
    }

    private static long getLongFromResponse(SearchResponse searchResponse, String field) {
        InternalNumericMetricsAggregation.SingleValue x = searchResponse.getAggregations().get(field);
        return Math.round(x.value());
    }

    private void retrieveStackTraces(
        Client client,
        GetStackTracesResponseBuilder responseBuilder,
        ActionListener<GetStackTracesResponse> submitListener
    ) {
        List<String> eventIds = new ArrayList<>(responseBuilder.getStackTraceEvents().keySet());
        List<List<String>> slicedEventIds = sliced(eventIds, desiredSlices);
        ClusterState clusterState = clusterService.state();
        List<Index> indices = resolver.resolve(clusterState, "profiling-stacktraces", responseBuilder.getStart(), responseBuilder.getEnd());
        StackTraceHandler handler = new StackTraceHandler(
            clusterState,
            client,
            responseBuilder,
            submitListener,
            eventIds.size(),
            // we need to expect a set of slices for each resolved index
            slicedEventIds.size() * indices.size()
        );
        for (List<String> slice : slicedEventIds) {
            mget(client, indices, slice, ActionListener.wrap(handler::onResponse, submitListener::onFailure));
        }

        // TODO: query only the relevant host metadata for the given list of host IDs (mget won't work here).
        // Retrieve the host metadata in parallel. Assume low-cardinality and do not split the query.
        client.prepareSearch("profiling-hosts")
            .setSize(10000)
            .setTrackTotalHits(false)
            .execute(ActionListener.wrap(handler::onHostsResponse, submitListener::onFailure));
    }

    // package private for testing
    static <T> List<List<T>> sliced(List<T> c, int slices) {
        if (c.size() <= slices) {
            return List.of(c);
        }
        List<List<T>> slicedList = new ArrayList<>();
        int batchSize = c.size() / slices;
        for (int slice = 0; slice < slices; slice++) {
            int upperIndex = (slice + 1 < slices) ? (slice + 1) * batchSize : c.size();
            List<T> ids = c.subList(slice * batchSize, upperIndex);
            slicedList.add(ids);
        }
        return Collections.unmodifiableList(slicedList);
    }

    private class StackTraceHandler {
        private final AtomicInteger expectedSlices;
        private final ClusterState clusterState;
        private final Client client;
        private final GetStackTracesResponseBuilder responseBuilder;
        private final ActionListener<GetStackTracesResponse> submitListener;
        private final Map<String, StackTrace> stackTracePerId;
        // sort items lexicographically to access Lucene's term dictionary more efficiently when issuing an mget request.
        // The term dictionary is lexicographically sorted and using the same order reduces the number of page faults
        // needed to load it.
        private final Set<String> stackFrameIds = new ConcurrentSkipListSet<>();
        private final Set<String> executableIds = new ConcurrentSkipListSet<>();
        private final AtomicInteger totalFrames = new AtomicInteger();
        private final StopWatch watch = new StopWatch("retrieveStackTraces");
        private final StopWatch hostsWatch = new StopWatch("retrieveHostMetadata");

        private StackTraceHandler(
            ClusterState clusterState,
            Client client,
            GetStackTracesResponseBuilder responseBuilder,
            ActionListener<GetStackTracesResponse> submitListener,
            int stackTraceCount,
            int slices
        ) {
            this.clusterState = clusterState;
            this.stackTracePerId = new ConcurrentHashMap<>(stackTraceCount);
            this.expectedSlices = new AtomicInteger(slices + 1); // +1 for onHostsResponse
            this.client = client;
            this.responseBuilder = responseBuilder;
            this.submitListener = submitListener;
        }

        public void onResponse(MultiGetResponse multiGetItemResponses) {
            for (MultiGetItemResponse trace : multiGetItemResponses) {
                if (trace.isFailed()) {
                    submitListener.onFailure(trace.getFailure().getFailure());
                    return;
                }
                if (trace.getResponse().isExists()) {
                    String id = trace.getId();
                    // Duplicates are expected as we query multiple indices - do a quick pre-check before we deserialize a response
                    if (stackTracePerId.containsKey(id) == false) {
                        StackTrace stacktrace = StackTrace.fromSource(trace.getResponse().getSource());
                        // Guard against concurrent access and ensure we only handle each item once
                        if (stackTracePerId.putIfAbsent(id, stacktrace) == null) {
                            totalFrames.addAndGet(stacktrace.frameIds.size());
                            stackFrameIds.addAll(stacktrace.frameIds);
                            executableIds.addAll(stacktrace.fileIds);
                        }
                    }
                }
            }
            mayFinish();
        }

        public void onHostsResponse(SearchResponse searchResponse) {
            SearchHit[] hits = searchResponse.getHits().getHits();
            for (SearchHit hit : hits) {
                HostMetadata host = HostMetadata.fromSource(hit.getSourceAsMap());
                hostsTable.putIfAbsent(host.hostID, host);
            }
            log.debug(hostsWatch::report);
            log.debug("Got [{}] host metadata items", hostsTable.size());

            // Do the CO2 and cost calculation in parallel to waiting for frame metadata.
            StopWatch watch = new StopWatch("calculateCO2AndCosts");
            CO2Calculator co2Calculator = new CO2Calculator(costsService, hostsTable, responseBuilder.requestedDuration);
            CostCalculator costCalculator = new CostCalculator(costsService, hostsTable, responseBuilder.requestedDuration);
            Map<String, TraceEvent> events = responseBuilder.stackTraceEvents;
            for (HostEventCount hec : responseBuilder.hostEventCounts) {
                TraceEvent event = events.get(hec.stacktraceID);
                if (event == null) {
                    // this should never happen
                    log.error("CO2/cost calculator: missing trace events for StackTraceID " + hec.stacktraceID);
                    continue;
                }
                event.annualCO2Tons += co2Calculator.getAnnualCO2Tons(hec.hostID, hec.count);
                event.annualCostsUSD += costCalculator.annualCostsUSD(hec.hostID, hec.count);
            }
            log.debug(watch::report);

            mayFinish();
        }

        public void mayFinish() {
            if (expectedSlices.decrementAndGet() == 0) {
                responseBuilder.setStackTraces(stackTracePerId);
                responseBuilder.setTotalFrames(totalFrames.get());
                log.debug(
                    "retrieveStackTraces found [{}] stack traces, [{}] frames, [{}] executables.",
                    stackTracePerId.size(),
                    stackFrameIds.size(),
                    executableIds.size()
                );
                log.debug(watch::report);
                retrieveStackTraceDetails(
                    clusterState,
                    client,
                    responseBuilder,
                    new ArrayList<>(stackFrameIds),
                    new ArrayList<>(executableIds),
                    submitListener
                );
            }
        }
    }

    private void retrieveStackTraceDetails(
        ClusterState clusterState,
        Client client,
        GetStackTracesResponseBuilder responseBuilder,
        List<String> stackFrameIds,
        List<String> executableIds,
        ActionListener<GetStackTracesResponse> submitListener
    ) {
        List<List<String>> slicedStackFrameIds = sliced(stackFrameIds, desiredDetailSlices);
        List<List<String>> slicedExecutableIds = sliced(executableIds, desiredDetailSlices);
        List<Index> stackFrameIndices = resolver.resolve(
            clusterState,
            "profiling-stackframes",
            responseBuilder.getStart(),
            responseBuilder.getEnd()
        );
        List<Index> executableIndices = resolver.resolve(
            clusterState,
            "profiling-executables",
            responseBuilder.getStart(),
            responseBuilder.getEnd()
        );
        DetailsHandler handler = new DetailsHandler(
            responseBuilder,
            submitListener,
            executableIds.size(),
            stackFrameIds.size(),
            slicedExecutableIds.size() * executableIndices.size(),
            slicedStackFrameIds.size() * stackFrameIndices.size()
        );

        if (stackFrameIds.isEmpty()) {
            handler.onStackFramesResponse(new MultiGetResponse(new MultiGetItemResponse[0]));
        } else {
            for (List<String> slice : slicedStackFrameIds) {
                mget(client, stackFrameIndices, slice, ActionListener.wrap(handler::onStackFramesResponse, submitListener::onFailure));
            }
        }
        // no data dependency - we can do this concurrently
        if (executableIds.isEmpty()) {
            handler.onExecutableDetailsResponse(new MultiGetResponse(new MultiGetItemResponse[0]));
        } else {
            for (List<String> slice : slicedExecutableIds) {
                mget(
                    client,
                    executableIndices,
                    slice,
                    ActionListener.wrap(handler::onExecutableDetailsResponse, submitListener::onFailure)
                );
            }
        }
    }

    /**
     * Collects stack trace details which are retrieved concurrently and sends a response only when all details are known.
     */
    private static class DetailsHandler {
        private final GetStackTracesResponseBuilder builder;
        private final ActionListener<GetStackTracesResponse> submitListener;
        private final Map<String, String> executables;
        private final Map<String, StackFrame> stackFrames;
        private final AtomicInteger expectedSlices;
        private final StopWatch watch = new StopWatch("retrieveStackTraceDetails");

        private DetailsHandler(
            GetStackTracesResponseBuilder builder,
            ActionListener<GetStackTracesResponse> submitListener,
            int executableCount,
            int stackFrameCount,
            int expectedExecutableSlices,
            int expectedStackFrameSlices
        ) {
            this.builder = builder;
            this.submitListener = submitListener;
            this.executables = new ConcurrentHashMap<>(executableCount);
            this.stackFrames = new ConcurrentHashMap<>(stackFrameCount);
            // for deciding when we're finished it is irrelevant where a slice originated so we can
            // simplify state handling by treating them equally.
            this.expectedSlices = new AtomicInteger(expectedExecutableSlices + expectedStackFrameSlices);
        }

        public void onStackFramesResponse(MultiGetResponse multiGetItemResponses) {
            for (MultiGetItemResponse frame : multiGetItemResponses) {
                if (frame.isFailed()) {
                    submitListener.onFailure(frame.getFailure().getFailure());
                    return;
                }
                if (frame.getResponse().isExists()) {
                    // Duplicates are expected as we query multiple indices - do a quick pre-check before we deserialize a response
                    if (stackFrames.containsKey(frame.getId()) == false) {
                        StackFrame stackFrame = StackFrame.fromSource(frame.getResponse().getSource());
                        if (stackFrame.isEmpty() == false) {
                            stackFrames.putIfAbsent(frame.getId(), stackFrame);
                        } else {
                            log.trace("Stack frame with id [{}] has no properties.", frame.getId());
                        }
                    }
                }
            }
            mayFinish();
        }

        public void onExecutableDetailsResponse(MultiGetResponse multiGetItemResponses) {
            for (MultiGetItemResponse executable : multiGetItemResponses) {
                if (executable.isFailed()) {
                    submitListener.onFailure(executable.getFailure().getFailure());
                    return;
                }
                if (executable.getResponse().isExists()) {
                    // Duplicates are expected as we query multiple indices - do a quick pre-check before we deserialize a response
                    if (executables.containsKey(executable.getId()) == false) {
                        String fileName = ObjectPath.eval("Executable.file.name", executable.getResponse().getSource());
                        if (fileName != null) {
                            executables.putIfAbsent(executable.getId(), fileName);
                        } else {
                            String priorKey = executables.putIfAbsent(executable.getId(), "<missing>");
                            // avoid spurious logging by checking whether we have actually inserted the 'missing' key
                            if (priorKey == null) {
                                log.trace("Executable with id [{}] has no file name.", executable.getId());
                            }
                        }
                    }
                }
            }
            mayFinish();
        }

        public void mayFinish() {
            if (expectedSlices.decrementAndGet() == 0) {
                builder.setExecutables(executables);
                builder.setStackFrames(stackFrames);
                log.debug("retrieveStackTraceDetails found [{}] stack frames, [{}] executables.", stackFrames.size(), executables.size());
                log.debug(watch::report);
                submitListener.onResponse(builder.build());
            }
        }
    }

    private void mget(Client client, List<Index> indices, List<String> slice, ActionListener<MultiGetResponse> listener) {
        for (Index index : indices) {
            client.prepareMultiGet()
                .addIds(index.getName(), slice)
                .setRealtime(realtime)
                .execute(new ThreadedActionListener<>(responseExecutor, listener));
        }
    }

    private static class GetStackTracesResponseBuilder {
        private Map<String, StackTrace> stackTraces;
        private Instant start;
        private Instant end;
        private int totalFrames;
        private Map<String, StackFrame> stackFrames;
        private Map<String, String> executables;
        private Map<String, TraceEvent> stackTraceEvents;
        private List<HostEventCount> hostEventCounts;
        private double samplingRate;
        private long totalSamples;
        private double requestedDuration;

        public void setStackTraces(Map<String, StackTrace> stackTraces) {
            this.stackTraces = stackTraces;
        }

        public Instant getStart() {
            return start;
        }

        public void setStart(Instant start) {
            this.start = start;
        }

        public Instant getEnd() {
            return end;
        }

        public void setEnd(Instant end) {
            this.end = end;
        }

        public void setTotalFrames(int totalFrames) {
            this.totalFrames = totalFrames;
        }

        public void setStackFrames(Map<String, StackFrame> stackFrames) {
            this.stackFrames = stackFrames;
        }

        public void setExecutables(Map<String, String> executables) {
            this.executables = executables;
        }

        public void setStackTraceEvents(Map<String, TraceEvent> stackTraceEvents) {
            this.stackTraceEvents = stackTraceEvents;
        }

        public void setHostEventCounts(List<HostEventCount> hostEventCounts) {
            this.hostEventCounts = hostEventCounts;
        }

        public Map<String, TraceEvent> getStackTraceEvents() {
            return stackTraceEvents;
        }

        public void setSamplingRate(double rate) {
            this.samplingRate = rate;
        }

        public double getSamplingRate() {
            return samplingRate;
        }

        public void setRequestedDuration(double requestedDuration) {
            this.requestedDuration = requestedDuration;
        }

        public void setTotalSamples(long totalSamples) {
            this.totalSamples = totalSamples;
        }

        public GetStackTracesResponse build() {
            // Merge the TraceEvent data into the StackTraces.
            if (stackTraces != null) {
                for (Map.Entry<String, StackTrace> entry : stackTraces.entrySet()) {
                    String stacktraceID = entry.getKey();
                    TraceEvent event = stackTraceEvents.get(stacktraceID);
                    if (event != null) {
                        StackTrace stackTrace = entry.getValue();
                        stackTrace.count = event.count;
                        stackTrace.annualCO2Tons = event.annualCO2Tons;
                        stackTrace.annualCostsUSD = event.annualCostsUSD;
                    }
                }
            }
            return new GetStackTracesResponse(
                stackTraces,
                stackFrames,
                executables,
                stackTraceEvents,
                totalFrames,
                samplingRate,
                totalSamples
            );
        }
    }
}
