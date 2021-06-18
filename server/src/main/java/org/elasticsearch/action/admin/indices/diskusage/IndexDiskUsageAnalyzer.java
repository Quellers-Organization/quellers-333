/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.indices.diskusage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.codecs.DocValuesProducer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.codecs.PointsReader;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.codecs.TermVectorsReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefIterator;
import org.elasticsearch.common.lucene.FilterIndexCommit;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.index.store.LuceneFilesExtensions;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Analyze the disk usage of each field in the index.
 */
 final class IndexDiskUsageAnalyzer {
    private static final Logger LOGGER = LogManager.getLogger(IndexDiskUsageAnalyzer.class);

    private final IndexCommit commit;
    private final TrackingReadBytesDirectory directory;
    private final CancellationChecker cancellationChecker;

    private IndexDiskUsageAnalyzer(IndexCommit commit, Runnable checkForCancellation) {
        this.directory = new TrackingReadBytesDirectory(commit.getDirectory());
        this.commit = new FilterIndexCommit(commit) {
            @Override
            public Directory getDirectory() {
                return directory;
            }
        };
        this.cancellationChecker = new CancellationChecker(checkForCancellation);
    }

    static IndexDiskUsageStats analyze(IndexCommit commit, Runnable checkForCancellation) throws IOException {
        final IndexDiskUsageAnalyzer analyzer = new IndexDiskUsageAnalyzer(commit, checkForCancellation);
        final IndexDiskUsageStats stats = new IndexDiskUsageStats(getIndexSize(commit));
        analyzer.doAnalyze(stats);
        return stats;
    }

    void doAnalyze(IndexDiskUsageStats stats) throws IOException {
        long startTime;
        final ExecutionTime executionTime = new ExecutionTime();
        try (DirectoryReader directoryReader = DirectoryReader.open(commit)) {
            directory.resetBytesRead();
            for (LeafReaderContext leaf : directoryReader.leaves()) {
                cancellationChecker.checkForCancellation();
                final SegmentReader reader = Lucene.segmentReader(leaf.reader());

                startTime = System.currentTimeMillis();
                analyzePostings(reader, stats);
                executionTime.postingsTimeInMillis += System.currentTimeMillis() - startTime;

                startTime = System.currentTimeMillis();
                analyzeStoredFields(reader, stats);
                executionTime.storedFieldsTimeInMillis += System.currentTimeMillis() - startTime;

                startTime = System.currentTimeMillis();
                analyzeDocValues(reader, stats);
                executionTime.docValuesTimeInMillis += System.currentTimeMillis() - startTime;

                startTime = System.currentTimeMillis();
                analyzePoints(reader, stats);
                executionTime.pointsTimeInMills += System.currentTimeMillis() - startTime;

                startTime = System.currentTimeMillis();
                analyzeNorms(reader, stats);
                executionTime.normsTimeInMillis += System.currentTimeMillis() - startTime;

                startTime = System.currentTimeMillis();
                analyzeTermVectors(reader, stats);
                executionTime.termVectorsTimeInMillis += System.currentTimeMillis() - startTime;
            }
        }
        LOGGER.debug("analyzing the disk usage took {}ms, postings: {}ms, stored fields: {}ms, doc values: {}ms, " +
                "points: {}ms, norms: {}ms, term vectors: {}ms\nstats: {}",
            executionTime.totalInMillis(), executionTime.postingsTimeInMillis, executionTime.storedFieldsTimeInMillis,
            executionTime.docValuesTimeInMillis, executionTime.pointsTimeInMills, executionTime.normsTimeInMillis,
            executionTime.termVectorsTimeInMillis, stats);
    }

    void analyzeStoredFields(SegmentReader reader, IndexDiskUsageStats stats) throws IOException {
        // We can't record the disk usages of stored fields in Lucene Codec as we need to record them for each chunk,
        // which is expensive; otherwise, bulk merge won't work.
        final StoredFieldsReader storedFieldsReader = reader.getFieldsReader().getMergeInstance();
        directory.resetBytesRead();
        final TrackingSizeStoredFieldVisitor visitor = new TrackingSizeStoredFieldVisitor();
        for (int docID = 0; docID < reader.maxDoc(); docID++) {
            cancellationChecker.logEvent();
            storedFieldsReader.visitDocument(docID, visitor);
        }
        if (visitor.fields.isEmpty() == false) {
            // Computing the compression ratio for each chunk would provide a better estimate for each field individually.
            // But it's okay to do this entire segment because source and _id are the only two stored fields in ES most the cases.
            final long totalBytes = visitor.fields.values().stream().mapToLong(v -> v).sum();
            final double ratio = (double) directory.getBytesRead() / (double) totalBytes;
            final FieldInfos fieldInfos = reader.getFieldInfos();
            for (Map.Entry<Integer, Long> field : visitor.fields.entrySet()) {
                final String fieldName = fieldInfos.fieldInfo(field.getKey()).name;
                final long fieldSize = (long) Math.ceil(field.getValue() * ratio);
                stats.addStoredField(fieldName, fieldSize);
            }
        }
    }

    private static class TrackingSizeStoredFieldVisitor extends StoredFieldVisitor {
        private final Map<Integer, Long> fields = new HashMap<>();

        private void trackField(FieldInfo fieldInfo, int fieldLength) {
            final int totalBytes = fieldLength + Long.BYTES; // a Long for bitsAndInfo
            fields.compute(fieldInfo.number, (k, v) -> v == null ? totalBytes : v + totalBytes);
        }

        @Override
        public void binaryField(FieldInfo fieldInfo, byte[] value) throws IOException {
            trackField(fieldInfo, Integer.BYTES + value.length);
        }

        @Override
        public void stringField(FieldInfo fieldInfo, byte[] value) throws IOException {
            trackField(fieldInfo, Integer.BYTES + value.length);
        }

        @Override
        public void intField(FieldInfo fieldInfo, int value) throws IOException {
            trackField(fieldInfo, Integer.BYTES);
        }

        @Override
        public void longField(FieldInfo fieldInfo, long value) throws IOException {
            trackField(fieldInfo, Long.BYTES);
        }

        @Override
        public void floatField(FieldInfo fieldInfo, float value) throws IOException {
            trackField(fieldInfo, Float.BYTES);
        }

        @Override
        public void doubleField(FieldInfo fieldInfo, double value) throws IOException {
            trackField(fieldInfo, Double.BYTES);
        }

        @Override
        public Status needsField(FieldInfo fieldInfo) throws IOException {
            return Status.YES;
        }
    }

    void analyzeDocValues(SegmentReader reader, IndexDiskUsageStats stats) throws IOException {
        // TODO: We can extract these stats from Lucene80DocValuesProducer without iterating all docValues
        // or track the new sliced IndexInputs.
        DocValuesProducer docValuesReader = reader.getDocValuesReader();
        if (docValuesReader == null) {
            return;
        }
        docValuesReader = reader.getDocValuesReader().getMergeInstance();
        for (FieldInfo field : reader.getFieldInfos()) {
            final DocValuesType dvType = field.getDocValuesType();
            if (dvType == DocValuesType.NONE) {
                continue;
            }
            cancellationChecker.checkForCancellation();
            directory.resetBytesRead();
            long numericValues = 0;
            switch (dvType) {
                case NUMERIC:
                    final NumericDocValues numeric = docValuesReader.getNumeric(field);
                    while (numeric.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        cancellationChecker.logEvent();
                        numeric.longValue();
                        numericValues++;
                    }
                    break;
                case SORTED_NUMERIC:
                    final SortedNumericDocValues sortedNumeric = docValuesReader.getSortedNumeric(field);
                    while (sortedNumeric.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        for (int i = 0; i < sortedNumeric.docValueCount(); i++) {
                            cancellationChecker.logEvent();
                            sortedNumeric.nextValue();
                            numericValues++;
                        }
                    }
                    break;
                case BINARY:
                    final BinaryDocValues binary = docValuesReader.getBinary(field);
                    while (binary.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        cancellationChecker.logEvent();
                        binary.binaryValue();
                    }
                    break;
                case SORTED:
                    final SortedDocValues sorted = docValuesReader.getSorted(field);
                    while (sorted.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        cancellationChecker.logEvent();
                        sorted.ordValue();
                    }
                    sorted.lookupOrd(0);
                    sorted.lookupOrd(sorted.getValueCount() - 1);
                    break;
                case SORTED_SET:
                    final SortedSetDocValues sortedSet = docValuesReader.getSortedSet(field);
                    while (sortedSet.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        while (sortedSet.nextOrd() != SortedSetDocValues.NO_MORE_ORDS) {
                            cancellationChecker.logEvent();
                        }
                    }
                    sortedSet.lookupOrd(0);
                    sortedSet.lookupOrd(sortedSet.getValueCount() - 1);
                    break;
                default:
                    assert false : "Unknown docValues type [" + dvType + "]";
                    throw new IllegalStateException("Unknown docValues type [" + dvType + "]");
            }
            long bytesRead = directory.getBytesRead();
            // A small NumericDV field can be loaded into memory when opening an index.
            if (bytesRead == 0 && (dvType == DocValuesType.NUMERIC || dvType == DocValuesType.SORTED_NUMERIC)) {
                assert numericValues <= 256 : "Too many numeric values [" + numericValues + "] are loaded into memory";
                bytesRead = numericValues * Long.BYTES;
            }
            stats.addDocValues(field.name, bytesRead);
        }
    }

    void analyzePostings(SegmentReader reader, IndexDiskUsageStats stats) throws IOException {
        // TODO: FieldsReader has stats() which might contain the disk usage infos
        // Also, can we track the byte reader per field extension to avoid visiting terms multiple times?
        FieldsProducer postingsReader = reader.getPostingsReader();
        if (postingsReader == null) {
            return;
        }
        postingsReader = postingsReader.getMergeInstance();
        PostingsEnum postings = null;
        for (FieldInfo field : reader.getFieldInfos()) {
            if (field.getIndexOptions() == IndexOptions.NONE) {
                continue;
            }
            cancellationChecker.checkForCancellation();
            directory.resetBytesRead();
            final Terms terms = postingsReader.terms(field.name);
            if (terms == null) {
                continue;
            }

            // Visit terms index and term dictionary and postings
            final BytesRefIterator termsIterator = terms.iterator();
            TermsEnum termsEnum = terms.iterator();
            BytesRef bytesRef;
            while ((bytesRef = termsIterator.next()) != null) {
                cancellationChecker.logEvent();
                termsEnum.seekExact(bytesRef); // it's expensive to look up every term
                termsEnum.totalTermFreq();
                for (long idx = 0; idx <= 8; idx++) {
                    cancellationChecker.logEvent();
                    final int skipDocID = Math.toIntExact(idx * reader.maxDoc() / 8);
                    postings = termsEnum.postings(postings, PostingsEnum.NONE);
                    if (postings.advance(skipDocID) != DocIdSetIterator.NO_MORE_DOCS) {
                        postings.freq();
                        postings.nextDoc();
                    } else {
                        break;
                    }
                }
            }
            final long termsBytes = directory.getBytesRead();
            stats.addTerms(field.name, termsBytes);

            // Visit positions, offsets, and payloads
            termsEnum = terms.iterator();
            while (termsEnum.next() != null) {
                cancellationChecker.logEvent();
                termsEnum.docFreq();
                termsEnum.totalTermFreq();
                postings = termsEnum.postings(postings, PostingsEnum.ALL);
                while (postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    if (terms.hasPositions()) {
                        for (int pos = 0; pos < postings.freq(); pos++) {
                            postings.nextPosition();
                            postings.startOffset();
                            postings.endOffset();
                            postings.getPayload();
                        }
                    }
                }
            }
            final long proximityBytes = directory.getBytesRead() - termsBytes;
            stats.addProximity(field.name, proximityBytes);
        }
    }

    void analyzePoints(SegmentReader reader, IndexDiskUsageStats stats) throws IOException {
        PointsReader pointsReader = reader.getPointsReader();
        if (pointsReader == null) {
            return;
        }
        pointsReader = pointsReader.getMergeInstance();
        final PointValues.IntersectVisitor visitor = new PointValues.IntersectVisitor() {
            @Override
            public void visit(int docID) {
                assert false : "Must never be called";
                throw new UnsupportedOperationException();
            }

            @Override
            public void visit(int docID, byte[] packedValue) {
                cancellationChecker.logEvent();
            }

            @Override
            public PointValues.Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
                return PointValues.Relation.CELL_CROSSES_QUERY;
            }
        };
        for (FieldInfo field : reader.getFieldInfos()) {
            cancellationChecker.checkForCancellation();
            directory.resetBytesRead();
            if (field.getPointDimensionCount() > 0) {
                final PointValues values = pointsReader.getValues(field.name);
                values.intersect(visitor);
                stats.addPoints(field.name, directory.getBytesRead());
            }
        }
    }

    void analyzeNorms(SegmentReader reader, IndexDiskUsageStats stats) throws IOException {
        NormsProducer normsReader = reader.getNormsReader();
        if (normsReader == null) {
            return;
        }
        normsReader = normsReader.getMergeInstance();
        for (FieldInfo field : reader.getFieldInfos()) {
            if (field.hasNorms()) {
                cancellationChecker.checkForCancellation();
                directory.resetBytesRead();
                final NumericDocValues norms = normsReader.getNorms(field);
                while (norms.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    cancellationChecker.logEvent();
                    norms.longValue();
                }
                stats.addNorms(field.name, directory.getBytesRead());
            }
        }
    }

    void analyzeTermVectors(SegmentReader reader, IndexDiskUsageStats stats) throws IOException {
        TermVectorsReader termVectorsReader = reader.getTermVectorsReader();
        if (termVectorsReader == null) {
            return;
        }
        termVectorsReader = termVectorsReader.getMergeInstance();
        directory.resetBytesRead();
        final TermVectorsVisitor visitor = new TermVectorsVisitor();
        for (int docID = 0; docID < reader.numDocs(); docID++) {
            cancellationChecker.logEvent();
            final Fields vectors = termVectorsReader.get(docID);
            if (vectors != null) {
                for (String field : vectors) {
                    cancellationChecker.logEvent();
                    visitor.visitField(vectors, field);
                }
            }
        }
        if (visitor.fields.isEmpty() == false) {
            final long totalBytes = visitor.fields.values().stream().mapToLong(v -> v).sum();
            final double ratio = (double) (directory.getBytesRead()) / (double) (totalBytes);
            for (Map.Entry<String, Long> field : visitor.fields.entrySet()) {
                final long fieldBytes = (long) Math.ceil(field.getValue() * ratio);
                stats.addTermVectors(field.getKey(), fieldBytes);
            }
        }
    }

    private class TermVectorsVisitor {
        final Map<String, Long> fields = new HashMap<>();
        private PostingsEnum docsAndPositions; // to reuse

        void visitField(Fields vectors, String fieldName) throws IOException {
            final Terms terms = vectors.terms(fieldName);
            if (terms == null) {
                return;
            }
            final boolean hasPositions = terms.hasPositions();
            final boolean hasOffsets = terms.hasOffsets();
            final boolean hasPayloads = terms.hasPayloads();
            assert hasPayloads == false || hasPositions;
            long fieldLength = 1; // flags
            final TermsEnum termsEnum = terms.iterator();
            BytesRef bytesRef;
            while ((bytesRef = termsEnum.next()) != null) {
                cancellationChecker.logEvent();
                fieldLength += Integer.BYTES + bytesRef.length; // term
                final int freq = (int) termsEnum.totalTermFreq();
                fieldLength += Integer.BYTES; // freq
                if (hasPositions || hasOffsets) {
                    docsAndPositions = termsEnum.postings(docsAndPositions, PostingsEnum.OFFSETS | PostingsEnum.PAYLOADS);
                    assert docsAndPositions != null;
                    while (docsAndPositions.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        cancellationChecker.logEvent();
                        assert docsAndPositions.freq() == freq;
                        for (int posUpTo = 0; posUpTo < freq; posUpTo++) {
                            final int pos = docsAndPositions.nextPosition();
                            fieldLength += Integer.BYTES; // position
                            docsAndPositions.startOffset();
                            fieldLength += Integer.BYTES; // start offset
                            docsAndPositions.endOffset();
                            fieldLength += Integer.BYTES; // end offset
                            final BytesRef payload = docsAndPositions.getPayload();
                            if (payload != null) {
                                fieldLength += Integer.BYTES + payload.length; // payload
                            }
                            assert hasPositions == false || pos >= 0;
                        }
                    }
                }
            }
            final long finalLength = fieldLength;
            fields.compute(fieldName, (k, v) -> v == null ? finalLength : v + finalLength);
        }
    }

    private static class TrackingReadBytesDirectory extends FilterDirectory {
        private final Map<String, BytesReadTracker> trackers = new HashMap<>();

        TrackingReadBytesDirectory(Directory in) {
            super(in);
        }

        long getBytesRead() {
            return trackers.values().stream().mapToLong(BytesReadTracker::getBytesRead).sum();
        }

        void resetBytesRead() {
            trackers.values().forEach(BytesReadTracker::resetBytesRead);
        }

        @Override
        public IndexInput openInput(String name, IOContext context) throws IOException {
            IndexInput in = super.openInput(name, context);
            try {
                final BytesReadTracker tracker = trackers.computeIfAbsent(name, k -> {
                    if (LuceneFilesExtensions.CFS.getExtension().equals(IndexFileNames.getExtension(name))) {
                        return new CompoundFileBytesReaderTracker();
                    } else {
                        return new BytesReadTracker();
                    }
                });
                final TrackingReadBytesIndexInput wrapped = new TrackingReadBytesIndexInput(in, 0L, tracker);
                in = null;
                return wrapped;
            } finally {
                IOUtils.close(in);
            }
        }
    }

    private static class TrackingReadBytesIndexInput extends IndexInput {
        final IndexInput in;
        final BytesReadTracker bytesReadTracker;
        final long fileOffset;

        TrackingReadBytesIndexInput(IndexInput in, long fileOffset, BytesReadTracker bytesReadTracker) {
            super(in.toString());
            this.in = in;
            this.fileOffset = fileOffset;
            this.bytesReadTracker = bytesReadTracker;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }

        @Override
        public long getFilePointer() {
            return in.getFilePointer();
        }

        @Override
        public void seek(long pos) throws IOException {
            in.seek(pos);
        }

        @Override
        public long length() {
            return in.length();
        }

        @Override
        public IndexInput slice(String sliceDescription, long offset, long length) throws IOException {
            final IndexInput slice = in.slice(sliceDescription, offset, length);
            return new TrackingReadBytesIndexInput(slice, fileOffset + offset, bytesReadTracker.createSliceTracker(offset));
        }

        @Override
        public IndexInput clone() {
            return new TrackingReadBytesIndexInput(in.clone(), fileOffset, bytesReadTracker);
        }

        @Override
        public byte readByte() throws IOException {
            bytesReadTracker.trackPositions(fileOffset + getFilePointer(), 1);
            return in.readByte();
        }

        @Override
        public void readBytes(byte[] b, int offset, int len) throws IOException {
            bytesReadTracker.trackPositions(fileOffset + getFilePointer(), len);
            in.readBytes(b, offset, len);
        }
    }

    /**
     * Lucene Codec organizes data field by field for doc values, points, postings, and norms; and document by document
     * for stored fields and term vectors. BytesReadTracker then can simply track the min and max read positions.
     * This would allow us to traverse only two ends of each partition.
     */
    private static class BytesReadTracker {
        private long minPosition = Long.MAX_VALUE; // inclusive
        private long maxPosition = Long.MIN_VALUE; // exclusive

        BytesReadTracker createSliceTracker(long offset) {
            return this;
        }

        void trackPositions(long position, int length) {
            minPosition = Math.min(minPosition, position);
            maxPosition = Math.max(maxPosition, position + length -1);
        }


        void resetBytesRead() {
            minPosition = Long.MAX_VALUE;
            maxPosition = Long.MIN_VALUE;
        }

        long getBytesRead() {
            if (minPosition <= maxPosition) {
                return maxPosition - minPosition;
            } else {
                return 0L;
            }
        }
    }

    private static class CompoundFileBytesReaderTracker extends BytesReadTracker {
        private final Map<Long, BytesReadTracker> slicedTrackers = new HashMap<>();

        @Override
        BytesReadTracker createSliceTracker(long offset) {
            return slicedTrackers.computeIfAbsent(offset, k -> new BytesReadTracker());
        }

        @Override
        void trackPositions(long position, int length) {
            // already tracked by a child tracker except for the header and footer, but we can ignore them.
        }

        @Override
        void resetBytesRead() {
            slicedTrackers.values().forEach(BytesReadTracker::resetBytesRead);
        }

        @Override
        long getBytesRead() {
            return slicedTrackers.values().stream().mapToLong(BytesReadTracker::getBytesRead).sum();
        }
    }

    static long getIndexSize(IndexCommit commit) throws IOException {
        long total = 0;
        for (String file : commit.getFileNames()) {
            total += commit.getDirectory().fileLength(file);
        }
        return total;
    }

    /**
     * Periodically checks if the task was cancelled so the analyzing process can abort quickly.
     */
    private static class CancellationChecker {
        static final long THRESHOLD = 10_000;
        private long iterations;
        private final Runnable checkForCancellationRunner;

        CancellationChecker(Runnable checkForCancellationRunner) {
            this.checkForCancellationRunner = checkForCancellationRunner;
        }

        void logEvent() {
            if (iterations == THRESHOLD) {
                checkForCancellation();
            } else {
                iterations++;
            }
        }

        void checkForCancellation() {
            iterations = 0;
            checkForCancellationRunner.run();
        }
    }

    private static class ExecutionTime {
        long postingsTimeInMillis;
        long storedFieldsTimeInMillis;
        long docValuesTimeInMillis;
        long pointsTimeInMills;
        long normsTimeInMillis;
        long termVectorsTimeInMillis;

        long totalInMillis() {
            return postingsTimeInMillis + storedFieldsTimeInMillis + docValuesTimeInMillis
                + pointsTimeInMills + normsTimeInMillis + termVectorsTimeInMillis;
        }
    }
}
