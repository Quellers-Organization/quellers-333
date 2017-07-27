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

package org.elasticsearch.index.translog;

import org.apache.lucene.util.Counter;
import org.elasticsearch.Assertions;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class TranslogDeletionPolicy {

    private final Map<Object, RuntimeException> openTranslogRef;

    public void assertNoOpenTranslogRefs() {
        ExceptionsHelper.rethrowAndSuppress(openTranslogRef.values());
    }

    /**
     * Records how many views are held against each
     * translog generation
     */
    private final Map<Long, Counter> translogRefCounts = new HashMap<>();

    /**
     * the translog generation that is requires to properly recover from the oldest non deleted
     * {@link org.apache.lucene.index.IndexCommit}.
     */
    private long minTranslogGenerationForRecovery = 1;

    private long retentionSizeInBytes;

    private long retentionAgeInMillis;

    public TranslogDeletionPolicy(long retentionSizeInBytes, long retentionAgeInMillis) {
        this.retentionSizeInBytes = retentionSizeInBytes;
        this.retentionAgeInMillis = retentionAgeInMillis;
        if (Assertions.ENABLED) {
            openTranslogRef = ConcurrentCollections.newConcurrentMap();
        } else {
            openTranslogRef = null;
        }
    }

    public synchronized void setMinTranslogGenerationForRecovery(long newGen) {
        if (newGen < minTranslogGenerationForRecovery) {
            throw new IllegalArgumentException("minTranslogGenerationForRecovery can't go backwards. new [" + newGen + "] current [" +
                minTranslogGenerationForRecovery + "]");
        }
        minTranslogGenerationForRecovery = newGen;
    }

    public synchronized void setRetentionSizeInBytes(long bytes) {
        retentionSizeInBytes = bytes;
    }

    public synchronized void setRetentionAgeInMillis(long ageInMillis) {
        retentionAgeInMillis = ageInMillis;
    }

    /**
     * acquires the basis generation for a new view or snapshot. Any translog generation above, and including, the returned generation
     * will not be deleted until the returned {@link Releasable} is closed.
     */
    synchronized Releasable acquireTranslogGen(final long genForView) {
        translogRefCounts.computeIfAbsent(genForView, l -> Counter.newCounter(false)).addAndGet(1);
        final AtomicBoolean closed = new AtomicBoolean();
        assert assertAddTranslogRef(closed);
        return () -> {
            if (closed.compareAndSet(false, true)) {
                releaseTranslogGenView(genForView);
                assert assertRemoveTranslogRef(closed);
            }
        };
    }

    private boolean assertAddTranslogRef(Object reference) {
        return openTranslogRef.put(reference, new RuntimeException()) == null;
    }

    private boolean assertRemoveTranslogRef(Object reference) {
        return openTranslogRef.remove(reference) != null;
    }

    /** returns the number of generations that were acquired for snapshots */
    synchronized int pendingTranslogRefCount() {
        return translogRefCounts.size();
    }

    /**
     * releases a generation that was acquired by {@link #acquireTranslogGen(long)}
     */
    private synchronized void releaseTranslogGenView(long translogGen) {
        Counter current = translogRefCounts.get(translogGen);
        if (current == null || current.get() <= 0) {
            throw new IllegalArgumentException("translog gen [" + translogGen + "] wasn't acquired");
        }
        if (current.addAndGet(-1) == 0) {
            translogRefCounts.remove(translogGen);
        }
    }

    /**
     * returns the minimum translog generation that is still required by the system. Any generation below
     * the returned value may be safely deleted
     *
     * @param readers current translog readers
     * @param writer  current translog writer
     */
    synchronized long minTranslogGenRequired(List<TranslogReader> readers, TranslogWriter writer) throws IOException {
        long minByView = getMinTranslogGenRequiredByLocks();
        long minByAge = getMinTranslogGenByAge(readers, writer, retentionAgeInMillis, currentTime());
        long minBySize = getMinTranslogGenBySize(readers, writer, retentionSizeInBytes);
        final long minByAgeAndSize;
        if (minBySize == Long.MIN_VALUE && minByAge == Long.MIN_VALUE) {
            // both size and age are disabled;
            minByAgeAndSize = Long.MAX_VALUE;
        } else {
            minByAgeAndSize = Math.max(minByAge, minBySize);
        }
        return Math.min(minByAgeAndSize, Math.min(minByView, minTranslogGenerationForRecovery));
    }

    static long getMinTranslogGenBySize(List<TranslogReader> readers, TranslogWriter writer, long retentionSizeInBytes) {
        if (retentionSizeInBytes >= 0) {
            long totalSize = writer.sizeInBytes();
            long minGen = writer.getGeneration();
            for (int i = readers.size() - 1; i >= 0 && totalSize < retentionSizeInBytes; i--) {
                final TranslogReader reader = readers.get(i);
                totalSize += reader.sizeInBytes();
                minGen = reader.getGeneration();
            }
            return minGen;
        } else {
            return Long.MIN_VALUE;
        }
    }

    static long getMinTranslogGenByAge(List<TranslogReader> readers, TranslogWriter writer, long maxRetentionAgeInMillis, long now)
        throws IOException {
        if (maxRetentionAgeInMillis >= 0) {
            for (TranslogReader reader: readers) {
                if (now - reader.getLastModifiedTime() <= maxRetentionAgeInMillis) {
                    return reader.getGeneration();
                }
            }
            return writer.getGeneration();
        } else {
            return Long.MIN_VALUE;
        }
    }

    protected long currentTime() {
        return System.currentTimeMillis();
    }

    private long getMinTranslogGenRequiredByLocks() {
        return translogRefCounts.keySet().stream().reduce(Math::min).orElse(Long.MAX_VALUE);
    }

    /** returns the translog generation that will be used as a basis of a future store/peer recovery */
    public synchronized long getMinTranslogGenerationForRecovery() {
        return minTranslogGenerationForRecovery;
    }

    synchronized long getTranslogRefCount(long gen) {
        final Counter counter = translogRefCounts.get(gen);
        return counter == null ? 0 : counter.get();
    }
}
