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

package org.elasticsearch.search.aggregations.metrics;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.packed.PackedInts;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.BitArray;
import org.elasticsearch.common.util.ByteArray;
import org.elasticsearch.common.util.ByteUtils;
import org.elasticsearch.common.util.IntArray;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Hyperloglog++ counter, implemented based on pseudo code from
 * http://static.googleusercontent.com/media/research.google.com/fr//pubs/archive/40671.pdf and its appendix
 * https://docs.google.com/document/d/1gyjfMHy43U9OWBXxfaeG-3MjGzejW1dlpyMwEYAAWEI/view?fullscreen
 *
 * This implementation is different from the original implementation in that it uses a hash table instead of a sorted list for linear
 * counting. Although this requires more space and makes hyperloglog (which is less accurate) used sooner, this is also considerably faster.
 *
 * Trying to understand what this class does without having read the paper is considered adventurous.
 *
 * The HyperLogLogPlusPlus contains two algorithms, one for linear counting and the HyperLogLog algorithm. Initially hashes added to the
 * data structure are processed using the linear counting until a threshold defined by the precision is reached where the data is replayed
 * to the HyperLogLog algorithm and then this is used.
 *
 * It supports storing several HyperLogLogPlusPlus structures which are identified by a bucket number.
 */
public final class HyperLogLogPlusPlus extends AbstractHyperLogLogPlusPlus {

    private static final float MAX_LOAD_FACTOR = 0.75f;

    public static final int DEFAULT_PRECISION = 14;

    private final BitArray algorithm;
    private final HyperLogLog hll;
    private final LinearCounting lc;

    /**
     * Compute the required precision so that <code>count</code> distinct entries would be counted with linear counting.
     */
    public static int precisionFromThreshold(long count) {
        final long hashTableEntries = (long) Math.ceil(count / MAX_LOAD_FACTOR);
        int precision = PackedInts.bitsRequired(hashTableEntries * Integer.BYTES);
        precision = Math.max(precision, AbstractHyperLogLog.MIN_PRECISION);
        precision = Math.min(precision, AbstractHyperLogLog.MAX_PRECISION);
        return precision;
    }

    /**
     * Return the expected per-bucket memory usage for the given precision.
     */
    public static long memoryUsage(int precision) {
        return 1L << precision;
    }

    public HyperLogLogPlusPlus(int precision, BigArrays bigArrays, long initialBucketCount) {
        super(precision);
        HyperLogLog hll = null;
        LinearCounting lc = null;
        BitArray algorithm = null;
        boolean success = false;
        try {
            hll = new HyperLogLog(bigArrays, initialBucketCount, precision);
            lc = new LinearCounting(bigArrays, initialBucketCount, precision, hll);
            algorithm = new BitArray(1, bigArrays);
            success = true;
        } finally {
            if (success == false) {
                Releasables.close(hll, lc, algorithm);
            }
        }
        this.hll = hll;
        this.lc = lc;
        this.algorithm = algorithm;
    }

    @Override
    public long maxOrd() {
        return hll.runLens.size() >>> hll.precision();
    }

    @Override
    public long cardinality(long bucketOrd) {
        if (getAlgorithm(bucketOrd) == LINEAR_COUNTING) {
            return lc.cardinality(bucketOrd);
        } else {
            return hll.cardinality(bucketOrd);
        }
    }

    @Override
    public boolean getAlgorithm(long bucketOrd) {
        return algorithm.get(bucketOrd);
    }

    @Override
    public AbstractLinearCounting.EncodedHashesIterator getLinearCounting(long bucketOrd) {
        return lc.values(bucketOrd);
    }

    @Override
    public AbstractHyperLogLog.RunLenIterator getHyperLogLog(long bucketOrd) {
        return hll.getRunLens(bucketOrd);
    }

    @Override
    public void collect(long bucket, long hash) {
        hll.ensureCapacity(bucket + 1);
        if (algorithm.get(bucket) == LINEAR_COUNTING) {
            final int newSize = lc.collect(bucket, hash);
            if (newSize > lc.threshold) {
                upgradeToHll(bucket);
            }
        } else {
            hll.collect(bucket, hash);
        }
    }

    @Override
    public void close() {
        Releasables.close(algorithm, hll, lc);
    }

    protected void addRunLen(long bucketOrd, int register, int runLen) {
        if (algorithm.get(bucketOrd) == LINEAR_COUNTING) {
            upgradeToHll(bucketOrd);
        }
        hll.addRunLen(0, register, runLen);
    }

    void upgradeToHll(long bucketOrd) {
        hll.ensureCapacity(bucketOrd + 1);
        final AbstractLinearCounting.EncodedHashesIterator hashes = lc.values(bucketOrd);
        // We need to copy values into an arrays as we will override
        // the values on the buffer
        final IntArray values = lc.bigArrays.newIntArray(hashes.size());
        try {
            int i = 0;
            while (hashes.next()) {
                values.set(i++, hashes.value());
            }
            assert i == hashes.size();
            hll.reset(bucketOrd);
            for (long j = 0; j < values.size(); ++j) {
                final int encoded = values.get(j);
                hll.collectEncoded(bucketOrd, encoded);
            }
            algorithm.set(bucketOrd);
        } finally {
            Releasables.close(values);
        }
    }

    public void merge(long thisBucket, AbstractHyperLogLogPlusPlus other, long otherBucket) {
        if (other.getAlgorithm(otherBucket) == LINEAR_COUNTING) {
            merge(thisBucket, other.getLinearCounting(otherBucket));
        } else {
            merge(thisBucket, other.getHyperLogLog(otherBucket));
        }
    }

    public void merge(long thisBucket, AbstractLinearCounting.EncodedHashesIterator values) {
        if (precision() == values.precision()) {
            mergeEqualPrecision(thisBucket, values);
        } else if (precision() < values.precision()) {
            mergeDifferentPrecision(thisBucket, values);
        } else {
            throw new IllegalArgumentException("Cannot merge a sketch of lower precision, provided got ["
                + values.precision() + "], this got [" + precision() + "]");
        }
    }

    private void mergeEqualPrecision(long thisBucket, AbstractLinearCounting.EncodedHashesIterator values) {
        hll.ensureCapacity(thisBucket + 1);
        while (values.next()) {
            final int encoded = values.value();
            if (algorithm.get(thisBucket) == LINEAR_COUNTING) {
                final int newSize = lc.addEncoded(thisBucket, encoded);
                if (newSize > lc.threshold) {
                    upgradeToHll(thisBucket);
                }
            } else {
                hll.collectEncoded(thisBucket, encoded);
            }
        }
    }

    private void mergeDifferentPrecision(long thisBucket, AbstractLinearCounting.EncodedHashesIterator values) {
        hll.ensureCapacity(thisBucket + 1);
        while (values.next()) {
            final int encoded = adjustEncodedHashPrecision(values.value(), values.precision(), precision());
            if (algorithm.get(thisBucket) == LINEAR_COUNTING) {
                final int newSize = lc.addEncoded(thisBucket, encoded);
                if (newSize > lc.threshold) {
                    upgradeToHll(thisBucket);
                }
            } else {
                hll.collectEncoded(thisBucket, encoded);
            }
        }
    }

    /**
     * Changes the precision of an encoded hash.
     *
     * @param encoded the encoded hash.
     * @param thisPrecision The precision of the given hash.
     * @param newPrecision  THe new precision. It must lower or equal to the precision of the hash.
     * @return The transformed encoded hash.
     */
    private static int adjustEncodedHashPrecision(int encoded, int thisPrecision, int newPrecision) {
        assert thisPrecision >= newPrecision;
        if (Integer.numberOfTrailingZeros(encoded) > 0 ||
            (encoded & LinearCounting.mask(32 - thisPrecision)) == (encoded & LinearCounting.mask(32 - newPrecision))) {
            // If the trailing bit is not set or masking the leading bits for the given precision
            // does not change the value, then there is nothing to do
            return encoded;
        } else {
            // Any of the bits for the new precision is set so we remove the trailing bits the
            // encoding has added
            return encoded >>> 6;
        }

    }

    public void merge(long thisBucket, AbstractHyperLogLog.RunLenIterator runLens) {
        if (precision() == runLens.precision()) {
            mergeEqualPrecision(thisBucket, runLens);
        } else if (precision() < runLens.precision()) {
            mergeDifferentPrecision(thisBucket, runLens);
        } else {
            throw new IllegalArgumentException("Cannot merge a sketch of lower precision, provided got ["
                + runLens.precision() + "], this got [" + precision() + "]");
        }
    }

    private void mergeEqualPrecision(long thisBucket, AbstractHyperLogLog.RunLenIterator runLens) {
        hll.ensureCapacity(thisBucket + 1);
        if (algorithm.get(thisBucket) != HYPERLOGLOG) {
            upgradeToHll(thisBucket);
        }
        for (int i = 0; i < hll.m; ++i) {
            runLens.next();
            hll.addRunLen(thisBucket, i, runLens.value());
        }
    }

    private void mergeDifferentPrecision(long thisBucket, AbstractHyperLogLog.RunLenIterator runLens) {
        hll.ensureCapacity(thisBucket + 1);
        if (algorithm.get(thisBucket) != HYPERLOGLOG) {
            upgradeToHll(thisBucket);
        }
        final int precisionDiff = runLens.precision() - precision();
        final int registersToMerge = 1 << precisionDiff;
        for (int i = 0; i < hll.m; ++i) {
            final byte value = mergeRegisters(runLens, precisionDiff, registersToMerge);
            hll.addRunLen(thisBucket, i, value);
        }
    }

    /**
     * Advance and merge the next {@code registersToMerge} values and return the resulting value.
     *
     * @param precisionDiff  The precision difference related to the merge
     * @param registersToMerge number of register to merge. This value should be equal to 1 &lt;&lt; precisionDiff
     * @return the merged value
     */
    private static byte mergeRegisters(AbstractHyperLogLog.RunLenIterator iterator, int precisionDiff, int registersToMerge) {
        assert (1 << precisionDiff) == registersToMerge;
        for (int i = 0; i < registersToMerge; i++) {
            iterator.next();
            final byte runLen = iterator.value();
            if (runLen != 0) {
                // skip any other register
                iterator.skip(registersToMerge - i - 1);
                if (i == 0) {
                    // If the first element is set, then runLen is the current runLen plus the change in precision
                    return (byte) (runLen + precisionDiff);
                } else {
                    // If any other register is set, the runLen is computed from the register position
                    return (byte) (precisionDiff - (int) (Math.log(i) / Math.log(2)));
                }
            }
        }
        // No value for this register
        return 0;
    }

    private static class HyperLogLog extends AbstractHyperLogLog implements Releasable {
        private final BigArrays bigArrays;
        private final HyperLogLogIterator iterator;
        // array for holding the runlens.
        private ByteArray runLens;


        HyperLogLog(BigArrays bigArrays, long initialBucketCount, int precision) {
            super(precision);
            this.runLens =  bigArrays.newByteArray(initialBucketCount << precision);
            this.bigArrays = bigArrays;
            this.iterator = new HyperLogLogIterator(this, precision, m);
        }

        @Override
        protected void addRunLen(long bucketOrd, int register, int encoded) {
            final long bucketIndex = (bucketOrd << p) + register;
            runLens.set(bucketIndex, (byte) Math.max(encoded, runLens.get(bucketIndex)));
        }

        @Override
        public RunLenIterator getRunLens(long bucketOrd) {
            iterator.reset(bucketOrd);
            return iterator;
        }

        protected void reset(long bucketOrd) {
            runLens.fill(bucketOrd << p, (bucketOrd << p) + m, (byte) 0);
        }

        protected void ensureCapacity(long numBuckets) {
            runLens = bigArrays.grow(runLens, numBuckets << p);
        }

        @Override
        public void close() {
            Releasables.close(runLens);
        }
    }

    private static class HyperLogLogIterator implements AbstractHyperLogLog.RunLenIterator {

        private final HyperLogLog hll;
        private final int m, p;
        int pos;
        long start;
        private byte value;

        HyperLogLogIterator(HyperLogLog hll, int p, int m) {
            this.hll = hll;
            this.m = m;
            this.p = p;
        }

        @Override
        public int precision() {
            return p;
        }

        void reset(long bucket) {
            pos = 0;
            start = bucket << p;
        }

        @Override
        public boolean next() {
            if (pos < m) {
                value = hll.runLens.get(start + pos);
                pos++;
                return true;
            }
            return false;
        }

        @Override
        public byte value() {
            return value;
        }

        @Override
        public void skip(int registers) {
            pos += registers;
        }
    }

    private static class LinearCounting extends AbstractLinearCounting implements Releasable {

        protected final int threshold;
        private final int mask;
        private final BytesRef readSpare;
        private final ByteBuffer writeSpare;
        private final BigArrays bigArrays;
        private final LinearCountingIterator iterator;
        // We are actually using HyperLogLog's runLens array but interpreting it as a hash set for linear counting.
        private final HyperLogLog hll;
        // Number of elements stored.
        private IntArray sizes;

        LinearCounting(BigArrays bigArrays, long initialBucketCount, int p, HyperLogLog hll) {
            super(p);
            this.bigArrays = bigArrays;
            this.hll = hll;
            final int capacity = (1 << p) / 4; // because ints take 4 bytes
            threshold = (int) (capacity * MAX_LOAD_FACTOR);
            mask = capacity - 1;
            sizes = bigArrays.newIntArray(initialBucketCount);
            readSpare = new BytesRef();
            writeSpare = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            iterator = new LinearCountingIterator(this, capacity);
        }

        @Override
        protected int addEncoded(long bucketOrd, int encoded) {
            sizes = bigArrays.grow(sizes, bucketOrd + 1);
            assert encoded != 0;
            for (int i = (encoded & mask);; i = (i + 1) & mask) {
                final int v = get(bucketOrd, i);
                if (v == 0) {
                    // means unused, take it!
                    set(bucketOrd, i, encoded);
                    return sizes.increment(bucketOrd, 1);
                } else if (v == encoded) {
                    // k is already in the set
                    return -1;
                }
            }
        }

        @Override
        protected int size(long bucketOrd) {
            if (bucketOrd >= sizes.size()) {
                return 0;
            }
            final int size = sizes.get(bucketOrd);
            assert size == recomputedSize(bucketOrd);
            return size;
        }

        @Override
        public EncodedHashesIterator values(long bucketOrd) {
            iterator.reset(bucketOrd, size(bucketOrd));
            return iterator;
        }

        private long index(long bucketOrd, int index) {
            return (bucketOrd << p) + (index << 2);
        }

        private int get(long bucketOrd, int index) {
            hll.runLens.get(index(bucketOrd, index), 4, readSpare);
            return ByteUtils.readIntLE(readSpare.bytes, readSpare.offset);
        }

        private void set(long bucketOrd, int index, int value) {
            writeSpare.putInt(0, value);
            hll.runLens.set(index(bucketOrd, index), writeSpare.array(), 0, 4);
        }

        private int recomputedSize(long bucketOrd) {
            int size = 0;
            for (int i = 0; i <= mask; ++i) {
                final int v = get(bucketOrd, i);
                if (v != 0) {
                    ++size;
                }
            }
            return size;
        }

        @Override
        public void close() {
            Releasables.close(sizes);
        }
    }

    private static class LinearCountingIterator implements AbstractLinearCounting.EncodedHashesIterator {

        private final LinearCounting lc;
        private final int capacity;
        private int pos, size;
        private long bucketOrd;
        private int value;

        LinearCountingIterator(LinearCounting lc, int capacity) {
            this.lc = lc;
            this.capacity = capacity;
        }

        void reset(long bucketOrd, int size) {
            this.bucketOrd = bucketOrd;
            this.size = size;
            this.pos = size == 0 ? capacity : 0;
        }

        @Override
        public int precision() {
            return lc.precision();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean next() {
            if (pos < capacity) {
                for (; pos < capacity; ++pos) {
                    final int k = lc.get(bucketOrd, pos);
                    if (k != 0) {
                        ++pos;
                        value = k;
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public int value() {
            return value;
        }
    }
}
