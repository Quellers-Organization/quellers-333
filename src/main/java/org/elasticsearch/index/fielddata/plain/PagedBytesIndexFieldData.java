/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.index.fielddata.plain;

import org.apache.lucene.codecs.BlockTreeTermsReader;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.PagedBytes;
import org.apache.lucene.util.packed.MonotonicAppendingLongBuffer;
import org.elasticsearch.common.breaker.MemoryCircuitBreaker;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.fielddata.FieldDataType;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.IndexFieldDataCache;
import org.elasticsearch.index.fielddata.RamAccountingTermsEnum;
import org.elasticsearch.index.fielddata.ordinals.GlobalOrdinalsBuilder;
import org.elasticsearch.index.fielddata.ordinals.Ordinals;
import org.elasticsearch.index.fielddata.ordinals.OrdinalsBuilder;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.settings.IndexSettings;
import org.elasticsearch.indices.fielddata.breaker.CircuitBreakerService;

import java.io.IOException;

/**
 */
public class PagedBytesIndexFieldData extends AbstractBytesIndexFieldData<PagedBytesAtomicFieldData> {


    public static class Builder implements IndexFieldData.Builder {

        @Override
        public IndexFieldData<PagedBytesAtomicFieldData> build(Index index, @IndexSettings Settings indexSettings, FieldMapper<?> mapper,
                                                               IndexFieldDataCache cache, CircuitBreakerService breakerService, MapperService mapperService,
                                                               GlobalOrdinalsBuilder globalOrdinalBuilder) {
            return new PagedBytesIndexFieldData(index, indexSettings, mapper.names(), mapper.fieldDataType(), cache, breakerService, globalOrdinalBuilder);
        }
    }

    public PagedBytesIndexFieldData(Index index, @IndexSettings Settings indexSettings, FieldMapper.Names fieldNames,
                                    FieldDataType fieldDataType, IndexFieldDataCache cache, CircuitBreakerService breakerService,
                                    GlobalOrdinalsBuilder globalOrdinalsBuilder) {
        super(index, indexSettings, fieldNames, fieldDataType, cache, globalOrdinalsBuilder, breakerService);
    }

    @Override
    public PagedBytesAtomicFieldData loadDirect(AtomicReaderContext context) throws Exception {
        AtomicReader reader = context.reader();

        PagedBytesEstimator estimator = new PagedBytesEstimator(context, breakerService.getBreaker(), getFieldNames().fullName());
        Terms terms = reader.terms(getFieldNames().indexName());
        if (terms == null) {
            PagedBytesAtomicFieldData emptyData = PagedBytesAtomicFieldData.empty();
            estimator.adjustForNoTerms(emptyData.getMemorySizeInBytes());
            return emptyData;
        }

        final PagedBytes bytes = new PagedBytes(15);

        final MonotonicAppendingLongBuffer termOrdToBytesOffset = new MonotonicAppendingLongBuffer();
        termOrdToBytesOffset.add(0); // first ord is reserved for missing values
        final long numTerms;
        if (regex == null && frequency == null) {
            numTerms = terms.size();
        } else {
            numTerms = -1;
        }
        final float acceptableTransientOverheadRatio = fieldDataType.getSettings().getAsFloat(
                FilterSettingFields.ACCEPTABLE_TRANSIENT_OVERHEAD_RATIO, OrdinalsBuilder.DEFAULT_ACCEPTABLE_OVERHEAD_RATIO);

        // Wrap the context in an estimator and use it to either estimate
        // the entire set, or wrap the TermsEnum so it can be calculated
        // per-term
        PagedBytesAtomicFieldData data = null;
        TermsEnum termsEnum = estimator.beforeLoad(terms);
        boolean success = false;

        try (OrdinalsBuilder builder = new OrdinalsBuilder(numTerms, reader.maxDoc(), acceptableTransientOverheadRatio)) {
            // 0 is reserved for "unset"
            bytes.copyUsingLengthPrefix(new BytesRef());

            DocsEnum docsEnum = null;
            for (BytesRef term = termsEnum.next(); term != null; term = termsEnum.next()) {
                final long termOrd = builder.nextOrdinal();
                assert termOrd == termOrdToBytesOffset.size();
                termOrdToBytesOffset.add(bytes.copyUsingLengthPrefix(term));
                docsEnum = termsEnum.docs(null, docsEnum, DocsEnum.FLAG_NONE);
                for (int docId = docsEnum.nextDoc(); docId != DocsEnum.NO_MORE_DOCS; docId = docsEnum.nextDoc()) {
                    builder.addDoc(docId);
                }
            }
            final long sizePointer = bytes.getPointer();
            PagedBytes.Reader bytesReader = bytes.freeze(true);
            final Ordinals ordinals = builder.build(fieldDataType.getSettings());

            data = new PagedBytesAtomicFieldData(bytesReader, sizePointer, termOrdToBytesOffset, ordinals);
            success = true;
            return data;
        } finally {
            if (!success) {
                // If something went wrong, unwind any current estimations we've made
                estimator.afterLoad(termsEnum, 0);
            } else {
                // Call .afterLoad() to adjust the breaker now that we have an exact size
                estimator.afterLoad(termsEnum, data.getMemorySizeInBytes());
            }

        }
    }

    /**
     * Estimator that wraps string field data by either using
     * BlockTreeTermsReader, or wrapping the data in a RamAccountingTermsEnum
     * if the BlockTreeTermsReader cannot be used.
     */
    public class PagedBytesEstimator implements PerValueEstimator {

        private final AtomicReaderContext context;
        private final MemoryCircuitBreaker breaker;
        private final String fieldName;
        private long estimatedBytes;

        PagedBytesEstimator(AtomicReaderContext context, MemoryCircuitBreaker breaker, String fieldName) {
            this.breaker = breaker;
            this.context = context;
            this.fieldName = fieldName;
        }

        /**
         * @return the number of bytes for the term based on the length and ordinal overhead
         */
        public long bytesPerValue(BytesRef term) {
            if (term == null) {
                return 0;
            }
            long bytes = term.length;
            // 64 bytes for miscellaneous overhead
            bytes += 64;
            // Seems to be about a 1.5x compression per term/ord, plus 1 for some wiggle room
            bytes = (long) ((double) bytes / 1.5) + 1;
            return bytes;
        }

        /**
         * @return the estimate for loading the entire term set into field data, or 0 if unavailable
         */
        public long estimateStringFieldData() {
            try {
                AtomicReader reader = context.reader();
                Terms terms = reader.terms(getFieldNames().indexName());

                Fields fields = reader.fields();
                final Terms fieldTerms = fields.terms(getFieldNames().indexName());

                if (fieldTerms instanceof BlockTreeTermsReader.FieldReader) {
                    final BlockTreeTermsReader.Stats stats = ((BlockTreeTermsReader.FieldReader) fieldTerms).computeStats();
                    long totalTermBytes = stats.totalTermBytes;
                    if (logger.isTraceEnabled()) {
                        logger.trace("totalTermBytes: {}, terms.size(): {}, terms.getSumDocFreq(): {}",
                                totalTermBytes, terms.size(), terms.getSumDocFreq());
                    }
                    long totalBytes = totalTermBytes + (2 * terms.size()) + (4 * terms.getSumDocFreq());
                    return totalBytes;
                }
            } catch (Exception e) {
                logger.warn("Unable to estimate memory overhead", e);
            }
            return 0;
        }

        /**
         * Determine whether the BlockTreeTermsReader.FieldReader can be used
         * for estimating the field data, adding the estimate to the circuit
         * breaker if it can, otherwise wrapping the terms in a
         * RamAccountingTermsEnum to be estimated on a per-term basis.
         *
         * @param terms terms to be estimated
         * @return A possibly wrapped TermsEnum for the terms
         * @throws IOException
         */
        public TermsEnum beforeLoad(Terms terms) throws IOException {
            final float acceptableTransientOverheadRatio = fieldDataType.getSettings().getAsFloat(
                    FilterSettingFields.ACCEPTABLE_TRANSIENT_OVERHEAD_RATIO,
                    OrdinalsBuilder.DEFAULT_ACCEPTABLE_OVERHEAD_RATIO);

            AtomicReader reader = context.reader();
            // Check if one of the following is present:
            // - The OrdinalsBuilder overhead has been tweaked away from the default
            // - A field data filter is present
            // - A regex filter is present
            if (acceptableTransientOverheadRatio != OrdinalsBuilder.DEFAULT_ACCEPTABLE_OVERHEAD_RATIO ||
                    fieldDataType.getSettings().getAsDouble(FilterSettingFields.FREQUENCY_MIN, 0d) != 0d ||
                    fieldDataType.getSettings().getAsDouble(FilterSettingFields.FREQUENCY_MAX, 0d) != 0d ||
                    fieldDataType.getSettings().getAsDouble(FilterSettingFields.FREQUENCY_MIN_SEGMENT_SIZE, 0d) != 0d ||
                    fieldDataType.getSettings().get(FilterSettingFields.REGEX_PATTERN) != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Filter exists, can't circuit break normally, using RamAccountingTermsEnum");
                }
                return new RamAccountingTermsEnum(filter(terms, reader), breaker, this, this.fieldName);
            } else {
                estimatedBytes = this.estimateStringFieldData();
                // If we weren't able to estimate, wrap in the RamAccountingTermsEnum
                if (estimatedBytes == 0) {
                    return new RamAccountingTermsEnum(filter(terms, reader), breaker, this, this.fieldName);
                }

                breaker.addEstimateBytesAndMaybeBreak(estimatedBytes, fieldName);
                return filter(terms, reader);
            }
        }

        /**
         * Adjust the circuit breaker now that terms have been loaded, getting
         * the actual used either from the parameter (if estimation worked for
         * the entire set), or from the TermsEnum if it has been wrapped in a
         * RamAccountingTermsEnum.
         *
         * @param termsEnum  terms that were loaded
         * @param actualUsed actual field data memory usage
         */
        public void afterLoad(TermsEnum termsEnum, long actualUsed) {
            if (termsEnum instanceof RamAccountingTermsEnum) {
                estimatedBytes = ((RamAccountingTermsEnum) termsEnum).getTotalBytes();
            }
            breaker.addWithoutBreaking(-(estimatedBytes - actualUsed));
        }

        /**
         * Adjust the breaker when no terms were actually loaded, but the field
         * data takes up space regardless. For instance, when ordinals are
         * used.
         * @param actualUsed bytes actually used
         */
        public void adjustForNoTerms(long actualUsed) {
            breaker.addWithoutBreaking(actualUsed);
        }
    }

    static final class FilterSettingFields {
        static final String ACCEPTABLE_TRANSIENT_OVERHEAD_RATIO = "acceptable_transient_overhead_ratio";
        static final String FREQUENCY_MIN = "filter.frequency.min";
        static final String FREQUENCY_MAX = "filter.frequency.max";
        static final String FREQUENCY_MIN_SEGMENT_SIZE = "filter.frequency.min_segment_size";
        static final String REGEX_PATTERN = "filter.regex.pattern";
    }
}
