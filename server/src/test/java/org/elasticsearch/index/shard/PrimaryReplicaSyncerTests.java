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
package org.elasticsearch.index.shard;

import org.apache.lucene.store.AlreadyClosedException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.resync.ResyncReplicationRequest;
import org.elasticsearch.action.resync.ResyncReplicationResponse;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.cluster.routing.IndexShardRoutingTable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.io.stream.ByteBufferStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.lucene.uid.Versions;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.mapper.SourceToParse;
import org.elasticsearch.index.seqno.SequenceNumbers;
import org.elasticsearch.tasks.TaskManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

public class PrimaryReplicaSyncerTests extends IndexShardTestCase {

    public void testSyncerSendsOffCorrectDocuments() throws Exception {
        IndexShard shard = newStartedShard(true);
        TaskManager taskManager = new TaskManager(Settings.EMPTY, threadPool, Collections.emptySet());
        AtomicBoolean syncActionCalled = new AtomicBoolean();
        List<ResyncReplicationRequest> resyncRequests = new ArrayList<>();
        PrimaryReplicaSyncer.SyncAction syncAction =
            (request, parentTask, allocationId, primaryTerm, listener) -> {
                logger.info("Sending off {} operations", request.getOperations().length);
                syncActionCalled.set(true);
                resyncRequests.add(request);
                assertThat(parentTask, instanceOf(PrimaryReplicaSyncer.ResyncTask.class));
                listener.onResponse(new ResyncReplicationResponse());
            };
        PrimaryReplicaSyncer syncer = new PrimaryReplicaSyncer(Settings.EMPTY, taskManager, syncAction);
        syncer.setChunkSize(new ByteSizeValue(randomIntBetween(1, 10)));

        int numDocs = randomInt(10);
        for (int i = 0; i < numDocs; i++) {
            // Index doc but not advance local checkpoint.
            shard.applyIndexOperationOnPrimary(Versions.MATCH_ANY, VersionType.INTERNAL,
                SourceToParse.source(shard.shardId().getIndexName(), "_doc", Integer.toString(i), new BytesArray("{}"), XContentType.JSON),
                IndexRequest.UNSET_AUTO_GENERATED_TIMESTAMP, false);
        }

        long globalCheckPoint = numDocs > 0 ? randomIntBetween(0, numDocs - 1) : 0;
        boolean syncNeeded = numDocs > 0 && globalCheckPoint < numDocs - 1;

        String allocationId = shard.routingEntry().allocationId().getId();
        shard.updateShardState(shard.routingEntry(), shard.getPrimaryTerm(), null, 1000L, Collections.singleton(allocationId),
            new IndexShardRoutingTable.Builder(shard.shardId()).addShard(shard.routingEntry()).build(), Collections.emptySet());
        shard.updateLocalCheckpointForShard(allocationId, globalCheckPoint);
        assertEquals(globalCheckPoint, shard.getGlobalCheckpoint());

        logger.info("Total ops: {}, global checkpoint: {}", numDocs, globalCheckPoint);

        PlainActionFuture<PrimaryReplicaSyncer.ResyncTask> fut = new PlainActionFuture<>();
        syncer.resync(shard, fut);
        fut.get();

        if (numDocs > 0) {
            assertTrue("Sync action was not called", syncActionCalled.get());
            ResyncReplicationRequest resyncRequest = resyncRequests.remove(0);
            assertThat(resyncRequest.getTrimAboveSeqNo(), equalTo(numDocs - 1L));

            assertThat("trimAboveSeqNo has to be specified in request #0 only", resyncRequests.stream()
                    .mapToLong(ResyncReplicationRequest::getTrimAboveSeqNo)
                    .filter(seqNo -> seqNo != SequenceNumbers.UNASSIGNED_SEQ_NO)
                    .findFirst()
                    .isPresent(),
                is(false));
        }

        assertEquals(globalCheckPoint == numDocs - 1 ? 0 : numDocs, fut.get().getTotalOperations());
        if (syncNeeded) {
            long skippedOps = globalCheckPoint + 1; // everything up to global checkpoint included
            assertEquals(skippedOps, fut.get().getSkippedOperations());
            assertEquals(numDocs - skippedOps, fut.get().getResyncedOperations());
        } else {
            assertEquals(0, fut.get().getSkippedOperations());
            assertEquals(0, fut.get().getResyncedOperations());
        }

        closeShards(shard);
    }

    public void testSyncerSendsMaxSeqNo() throws Exception {
        IndexShard shard = newStartedShard(true);
        TaskManager taskManager = new TaskManager(Settings.EMPTY, threadPool, Collections.emptySet());
        AtomicReference<ResyncReplicationRequest> resyncRequest = new AtomicReference<>();
        PrimaryReplicaSyncer.SyncAction syncAction =
            (request, parentTask, allocationId, primaryTerm, listener) -> {
                assertThat("has to be called once", resyncRequest.compareAndSet(null, request), is(true));
                assertThat(parentTask, instanceOf(PrimaryReplicaSyncer.ResyncTask.class));
                listener.onResponse(new ResyncReplicationResponse());
            };
        PrimaryReplicaSyncer syncer = new PrimaryReplicaSyncer(Settings.EMPTY, taskManager, syncAction);

        int numDocs = randomInt(10);
        for (int i = 0; i < numDocs; i++) {
            // Index doc but not advance local checkpoint.
            shard.applyIndexOperationOnPrimary(Versions.MATCH_ANY, VersionType.INTERNAL,
                SourceToParse.source(shard.shardId().getIndexName(), "_doc", Integer.toString(i), new BytesArray("{}"), XContentType.JSON),
                IndexRequest.UNSET_AUTO_GENERATED_TIMESTAMP, false);
        }

        String allocationId = shard.routingEntry().allocationId().getId();
        shard.updateShardState(shard.routingEntry(), shard.getPrimaryTerm(), null, 1000L, Collections.singleton(allocationId),
            new IndexShardRoutingTable.Builder(shard.shardId()).addShard(shard.routingEntry()).build(), Collections.emptySet());

        // move globalCheckPoint to maxSeqNo
        long globalCheckPoint = numDocs;
        shard.updateLocalCheckpointForShard(allocationId, globalCheckPoint);
        assertEquals(globalCheckPoint, shard.getGlobalCheckpoint());

        PlainActionFuture<PrimaryReplicaSyncer.ResyncTask> fut = new PlainActionFuture<>();
        syncer.resync(shard, fut);
        fut.get();

        if (numDocs > 0) {
            assertThat("Sync action was not called", resyncRequest.get(), is(notNullValue()));
            assertThat("sync action has to be call to send trimAboveSeqNo even if there is no ops to sync",
                fut.get().getTotalOperations(), equalTo(0));
            assertThat(resyncRequest.get().getTrimAboveSeqNo(), equalTo(numDocs - 1L));
            assertThat(fut.get().getSkippedOperations(), equalTo(0));
            assertThat(fut.get().getResyncedOperations(), equalTo(0));
        } else {
            assertThat("Sync action should not be called, trimAboveSeqNo is unassigned",
                resyncRequest.get(), is(nullValue()));
        }

        closeShards(shard);
    }

    public void testSyncerOnClosingShard() throws Exception {
        IndexShard shard = newStartedShard(true);
        AtomicBoolean syncActionCalled = new AtomicBoolean();
        CountDownLatch syncCalledLatch = new CountDownLatch(1);
        PrimaryReplicaSyncer.SyncAction syncAction =
            (request, parentTask, allocationId, primaryTerm, listener) -> {
                logger.info("Sending off {} operations", request.getOperations().length);
                syncActionCalled.set(true);
                syncCalledLatch.countDown();
                threadPool.generic().execute(() -> listener.onResponse(new ResyncReplicationResponse()));
            };
        PrimaryReplicaSyncer syncer = new PrimaryReplicaSyncer(Settings.EMPTY,
            new TaskManager(Settings.EMPTY, threadPool, Collections.emptySet()), syncAction);
        syncer.setChunkSize(new ByteSizeValue(1)); // every document is sent off separately

        int numDocs = 10;
        for (int i = 0; i < numDocs; i++) {
            // Index doc but not advance local checkpoint.
            shard.applyIndexOperationOnPrimary(Versions.MATCH_ANY, VersionType.INTERNAL,
                SourceToParse.source(shard.shardId().getIndexName(), "_doc", Integer.toString(i), new BytesArray("{}"), XContentType.JSON),
                IndexRequest.UNSET_AUTO_GENERATED_TIMESTAMP, false);
        }

        String allocationId = shard.routingEntry().allocationId().getId();
        shard.updateShardState(shard.routingEntry(), shard.getPrimaryTerm(), null, 1000L, Collections.singleton(allocationId),
            new IndexShardRoutingTable.Builder(shard.shardId()).addShard(shard.routingEntry()).build(), Collections.emptySet());

        PlainActionFuture<PrimaryReplicaSyncer.ResyncTask> fut = new PlainActionFuture<>();
        threadPool.generic().execute(() -> {
            try {
                syncer.resync(shard, fut);
            } catch (AlreadyClosedException ace) {
                fut.onFailure(ace);
            }
        });
        if (randomBoolean()) {
            syncCalledLatch.await();
        }
        closeShards(shard);
        try {
            fut.actionGet();
            assertTrue("Sync action was not called", syncActionCalled.get());
        } catch (AlreadyClosedException | IndexShardClosedException ignored) {
            // ignore
        }
    }

    public void testStatusSerialization() throws IOException {
        PrimaryReplicaSyncer.ResyncTask.Status status = new PrimaryReplicaSyncer.ResyncTask.Status(randomAlphaOfLength(10),
            randomIntBetween(0, 1000), randomIntBetween(0, 1000), randomIntBetween(0, 1000));
        final BytesStreamOutput out = new BytesStreamOutput();
        status.writeTo(out);
        final ByteBufferStreamInput in = new ByteBufferStreamInput(ByteBuffer.wrap(out.bytes().toBytesRef().bytes));
        PrimaryReplicaSyncer.ResyncTask.Status serializedStatus = new PrimaryReplicaSyncer.ResyncTask.Status(in);
        assertEquals(status, serializedStatus);
    }

    public void testStatusEquals() throws IOException {
        PrimaryReplicaSyncer.ResyncTask task =
            new PrimaryReplicaSyncer.ResyncTask(0, "type", "action", "desc", null, Collections.emptyMap());
        task.setPhase(randomAlphaOfLength(10));
        task.setResyncedOperations(randomIntBetween(0, 1000));
        task.setTotalOperations(randomIntBetween(0, 1000));
        task.setSkippedOperations(randomIntBetween(0, 1000));
        PrimaryReplicaSyncer.ResyncTask.Status status = task.getStatus();
        PrimaryReplicaSyncer.ResyncTask.Status sameStatus = task.getStatus();
        assertNotSame(status, sameStatus);
        assertEquals(status, sameStatus);
        assertEquals(status.hashCode(), sameStatus.hashCode());

        switch (randomInt(3)) {
            case 0: task.setPhase("otherPhase"); break;
            case 1: task.setResyncedOperations(task.getResyncedOperations() + 1); break;
            case 2: task.setSkippedOperations(task.getSkippedOperations() + 1); break;
            case 3: task.setTotalOperations(task.getTotalOperations() + 1); break;
        }

        PrimaryReplicaSyncer.ResyncTask.Status differentStatus = task.getStatus();
        assertNotEquals(status, differentStatus);
    }

    public void testStatusReportsCorrectNumbers() throws IOException {
        PrimaryReplicaSyncer.ResyncTask task =
            new PrimaryReplicaSyncer.ResyncTask(0, "type", "action", "desc", null, Collections.emptyMap());
        task.setPhase(randomAlphaOfLength(10));
        task.setResyncedOperations(randomIntBetween(0, 1000));
        task.setTotalOperations(randomIntBetween(0, 1000));
        task.setSkippedOperations(randomIntBetween(0, 1000));
        PrimaryReplicaSyncer.ResyncTask.Status status = task.getStatus();
        XContentBuilder jsonBuilder = XContentFactory.jsonBuilder();
        status.toXContent(jsonBuilder, ToXContent.EMPTY_PARAMS);
        String jsonString = Strings.toString(jsonBuilder);
        assertThat(jsonString, containsString("\"phase\":\"" + task.getPhase() + "\""));
        assertThat(jsonString, containsString("\"totalOperations\":" + task.getTotalOperations()));
        assertThat(jsonString, containsString("\"resyncedOperations\":" + task.getResyncedOperations()));
        assertThat(jsonString, containsString("\"skippedOperations\":" + task.getSkippedOperations()));
    }
}
