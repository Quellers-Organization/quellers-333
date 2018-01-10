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

package org.elasticsearch.index.engine;

import com.carrotsearch.hppc.LongArrayList;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.store.Directory;
import org.elasticsearch.index.seqno.SequenceNumbers;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.index.translog.TranslogDeletionPolicy;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.singletonList;
import static org.elasticsearch.index.engine.EngineConfig.OpenMode.OPEN_INDEX_AND_TRANSLOG;
import static org.elasticsearch.index.engine.EngineConfig.OpenMode.OPEN_INDEX_CREATE_TRANSLOG;
import static org.elasticsearch.index.translog.TranslogDeletionPolicies.createTranslogDeletionPolicy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.Mockito.mock;

public class CombinedDeletionPolicyTests extends ESTestCase {

    public void testKeepCommitsAfterGlobalCheckpoint() throws Exception {
        final AtomicLong globalCheckpoint = new AtomicLong();
        TranslogDeletionPolicy translogPolicy = createTranslogDeletionPolicy();
        CombinedDeletionPolicy indexPolicy = new CombinedDeletionPolicy(OPEN_INDEX_AND_TRANSLOG, translogPolicy, globalCheckpoint::get);

        final LongArrayList maxSeqNoList = new LongArrayList();
        final LongArrayList translogGenList = new LongArrayList();
        final List<MockIndexCommit> commitList = new ArrayList<>();
        int totalCommits = between(2, 20);
        long lastMaxSeqNo = 0;
        long lastTranslogGen = 0;
        final UUID translogUUID = UUID.randomUUID();
        for (int i = 0; i < totalCommits; i++) {
            lastMaxSeqNo += between(1, 10000);
            lastTranslogGen += between(1, 100);
            commitList.add(mockIndexCommit(lastMaxSeqNo, translogUUID, lastTranslogGen));
            maxSeqNoList.add(lastMaxSeqNo);
            translogGenList.add(lastTranslogGen);
        }

        int keptIndex = randomInt(commitList.size() - 1);
        final long lower = maxSeqNoList.get(keptIndex);
        final long upper = keptIndex == commitList.size() - 1 ?
            Long.MAX_VALUE : Math.max(maxSeqNoList.get(keptIndex), maxSeqNoList.get(keptIndex + 1) - 1);
        globalCheckpoint.set(randomLongBetween(lower, upper));
        indexPolicy.onCommit(commitList);

        for (int i = 0; i < commitList.size(); i++) {
            if (i < keptIndex) {
                assertThat(commitList.get(i).deleteTimes(), equalTo(1));
            } else {
                assertThat(commitList.get(i).deleteTimes(), equalTo(0));
            }
        }
        assertThat(translogPolicy.getMinTranslogGenerationForRecovery(), equalTo(translogGenList.get(keptIndex)));
        assertThat(translogPolicy.getTranslogGenerationOfLastCommit(), equalTo(lastTranslogGen));
    }

    public void testAcquireIndexCommit() throws Exception {
        final AtomicLong globalCheckpoint = new AtomicLong();
        final UUID translogUUID = UUID.randomUUID();
        TranslogDeletionPolicy translogPolicy = createTranslogDeletionPolicy();
        CombinedDeletionPolicy indexPolicy = new CombinedDeletionPolicy(OPEN_INDEX_AND_TRANSLOG, translogPolicy, globalCheckpoint::get);

        final long maxSeqNo1 = between(1, 1000);
        final long translogGen1 = between(1, 100);
        final MockIndexCommit c1 = mockIndexCommit(maxSeqNo1, translogUUID, translogGen1);
        final long maxSeqNo2 = maxSeqNo1 + between(1, 1000);
        final long translogGen2 = translogGen1 + between(1, 100);
        final MockIndexCommit c2 = mockIndexCommit(maxSeqNo2, translogUUID, translogGen2);
        globalCheckpoint.set(randomLongBetween(0, maxSeqNo2 - 1)); // Keep both c1 and c2.
        indexPolicy.onCommit(Arrays.asList(c1, c2));
        final IndexCommit ref1 = indexPolicy.acquireIndexCommit(true);
        assertThat(ref1, equalTo(c1));
        expectThrows(UnsupportedOperationException.class, ref1::delete);
        final IndexCommit ref2 = indexPolicy.acquireIndexCommit(false);
        assertThat(ref2, equalTo(c2));
        expectThrows(UnsupportedOperationException.class, ref2::delete);
        assertThat(translogPolicy.getMinTranslogGenerationForRecovery(), lessThanOrEqualTo(100L));

        globalCheckpoint.set(randomLongBetween(maxSeqNo2, Long.MAX_VALUE));
        indexPolicy.onCommit(Arrays.asList(c1, c2)); // Policy keeps c2 only, but c1 is snapshotted.
        assertThat(c1.deleteTimes(), equalTo(0));
        final IndexCommit ref3 = indexPolicy.acquireIndexCommit(true);
        assertThat(ref3, equalTo(c2));
        assertThat(translogPolicy.getMinTranslogGenerationForRecovery(), equalTo(translogGen1));
        indexPolicy.releaseCommit(ref1); // release acquired commit releases translog and commit
        indexPolicy.onCommit(Arrays.asList(c1, c2)); // Flush new commit deletes c1
        assertThat(c1.deleteTimes(), equalTo(1));
    }

    public void testLegacyIndex() throws Exception {
        final AtomicLong globalCheckpoint = new AtomicLong();
        final UUID translogUUID = UUID.randomUUID();

        TranslogDeletionPolicy translogPolicy = createTranslogDeletionPolicy();
        CombinedDeletionPolicy indexPolicy = new CombinedDeletionPolicy(OPEN_INDEX_AND_TRANSLOG, translogPolicy, globalCheckpoint::get);

        long legacyTranslogGen = randomNonNegativeLong();
        MockIndexCommit legacyCommit = mockLegacyIndexCommit(translogUUID, legacyTranslogGen);
        indexPolicy.onInit(singletonList(legacyCommit));
        assertThat(legacyCommit.deleteTimes(), equalTo(0));
        assertThat(translogPolicy.getMinTranslogGenerationForRecovery(), equalTo(legacyTranslogGen));
        assertThat(translogPolicy.getTranslogGenerationOfLastCommit(), equalTo(legacyTranslogGen));

        long safeTranslogGen = randomLongBetween(legacyTranslogGen, Long.MAX_VALUE);
        long maxSeqNo = randomLongBetween(1, Long.MAX_VALUE);
        final MockIndexCommit freshCommit = mockIndexCommit(maxSeqNo, translogUUID, safeTranslogGen);

        globalCheckpoint.set(randomLongBetween(0, maxSeqNo - 1));
        indexPolicy.onCommit(Arrays.asList(legacyCommit, freshCommit));
        assertThat(legacyCommit.deleteTimes(), equalTo(0));
        assertThat(freshCommit.deleteTimes(), equalTo(0));
        assertThat(translogPolicy.getMinTranslogGenerationForRecovery(), equalTo(legacyTranslogGen));
        assertThat(translogPolicy.getTranslogGenerationOfLastCommit(), equalTo(safeTranslogGen));

        // Make the fresh commit safe.
        globalCheckpoint.set(randomLongBetween(maxSeqNo, Long.MAX_VALUE));
        indexPolicy.onCommit(Arrays.asList(legacyCommit, freshCommit));
        assertThat(legacyCommit.deleteTimes(), equalTo(1));
        assertThat(freshCommit.deleteTimes(), equalTo(0));
        assertThat(translogPolicy.getMinTranslogGenerationForRecovery(), equalTo(safeTranslogGen));
        assertThat(translogPolicy.getTranslogGenerationOfLastCommit(), equalTo(safeTranslogGen));
    }

    public void testDeleteInvalidCommits() throws Exception {
        final AtomicLong globalCheckpoint = new AtomicLong(randomNonNegativeLong());
        TranslogDeletionPolicy translogPolicy = createTranslogDeletionPolicy();
        CombinedDeletionPolicy indexPolicy = new CombinedDeletionPolicy(OPEN_INDEX_CREATE_TRANSLOG, translogPolicy, globalCheckpoint::get);

        final int invalidCommits = between(1, 10);
        final List<MockIndexCommit> commitList = new ArrayList<>();
        for (int i = 0; i < invalidCommits; i++) {
            commitList.add(mockIndexCommit(randomNonNegativeLong(), UUID.randomUUID(), randomNonNegativeLong()));
        }

        final UUID expectedTranslogUUID = UUID.randomUUID();
        long lastTranslogGen = 0;
        final int validCommits = between(1, 10);
        for (int i = 0; i < validCommits; i++) {
            lastTranslogGen += between(1, 1000);
            commitList.add(mockIndexCommit(randomNonNegativeLong(), expectedTranslogUUID, lastTranslogGen));
        }

        // We should never keep invalid commits regardless of the value of the global checkpoint.
        indexPolicy.onCommit(commitList);
        for (int i = 0; i < invalidCommits - 1; i++) {
            assertThat(commitList.get(i).deleteTimes(), equalTo(1));
        }
    }

    MockIndexCommit mockIndexCommit(long maxSeqNo, UUID translogUUID, long translogGen) throws IOException {
        final Map<String, String> userData = new HashMap<>();
        userData.put(SequenceNumbers.MAX_SEQ_NO, Long.toString(maxSeqNo));
        userData.put(Translog.TRANSLOG_UUID_KEY, translogUUID.toString());
        userData.put(Translog.TRANSLOG_GENERATION_KEY, Long.toString(translogGen));
        return new MockIndexCommit(randomNonNegativeLong(), mock(Directory.class), userData);
    }

    MockIndexCommit mockLegacyIndexCommit(UUID translogUUID, long translogGen) throws IOException {
        final Map<String, String> userData = new HashMap<>();
        userData.put(Translog.TRANSLOG_UUID_KEY, translogUUID.toString());
        userData.put(Translog.TRANSLOG_GENERATION_KEY, Long.toString(translogGen));
        return new MockIndexCommit(randomNonNegativeLong(), mock(Directory.class), userData);
    }

    static class MockIndexCommit extends IndexCommit {
        private final long generation;
        private final Directory directory;
        private final Map<String, String> userData;
        private final AtomicInteger deleteTimes = new AtomicInteger();

        MockIndexCommit(long generation, Directory directory, Map<String, String> userData) {
            this.generation = generation;
            this.directory = directory;
            this.userData = userData;
        }

        @Override
        public String getSegmentsFileName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<String> getFileNames() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Directory getDirectory() {
            return directory;
        }

        @Override
        public void delete() {
            deleteTimes.getAndIncrement();
        }

        int deleteTimes(){
            return deleteTimes.get();
        }

        @Override
        public boolean isDeleted() {
            return deleteTimes.get() > 0;
        }

        @Override
        public int getSegmentCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getGeneration() {
            return generation;
        }

        @Override
        public Map<String, String> getUserData() throws IOException {
            return userData;
        }

        @Override
        public String toString() {
            return "MockIndexCommit{" + userData + "}";
        }
    }
}
