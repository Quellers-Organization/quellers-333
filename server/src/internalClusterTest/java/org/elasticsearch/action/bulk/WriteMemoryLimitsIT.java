/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.action.bulk;

import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.action.admin.indices.stats.ShardStats;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.InternalSettingsPlugin;
import org.elasticsearch.test.transport.MockTransportService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.SUITE, numDataNodes = 2, numClientNodes = 1)
public class WriteMemoryLimitsIT extends ESIntegTestCase {

    public static final String INDEX_NAME = "test";

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(MockTransportService.TestPlugin.class, InternalSettingsPlugin.class);
    }

    @Override
    protected int numberOfReplicas() {
        return 1;
    }

    @Override
    protected int numberOfShards() {
        return 1;
    }

    public void testWriteBytesAreIncremented() throws Exception {
        assertAcked(prepareCreate(INDEX_NAME, Settings.builder()
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 1)));
        ensureGreen(INDEX_NAME);

        Tuple<String, String> primaryReplicaNodeNames = getPrimaryReplicaNodeNames();
        String primaryName = primaryReplicaNodeNames.v1();
        String replicaName = primaryReplicaNodeNames.v2();
        String coordinatingOnlyNode = getCoordinatingOnlyNode();

        final CountDownLatch replicationSendPointReached = new CountDownLatch(1);
        final CountDownLatch latchBlockingReplicationSend = new CountDownLatch(1);

        TransportService primaryService = internalCluster().getInstance(TransportService.class, primaryName);
        final MockTransportService primaryTransportService = (MockTransportService) primaryService;
        TransportService replicaService = internalCluster().getInstance(TransportService.class, replicaName);
        final MockTransportService replicaTransportService = (MockTransportService) replicaService;

        primaryTransportService.addSendBehavior((connection, requestId, action, request, options) -> {
            if (action.equals(TransportShardBulkAction.ACTION_NAME + "[r]")) {
                try {
                    replicationSendPointReached.countDown();
                    latchBlockingReplicationSend.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
            connection.sendRequest(requestId, action, request, options);
        });

        final ThreadPool replicaThreadPool = replicaTransportService.getThreadPool();
        final Releasable replicaRelease = blockReplicas(replicaThreadPool);

        final BulkRequest bulkRequest = new BulkRequest();
        int totalRequestSize = 0;
        for (int i = 0; i < 80; ++i) {
            IndexRequest request = new IndexRequest(INDEX_NAME).id(UUIDs.base64UUID())
                .source(Collections.singletonMap("key", randomAlphaOfLength(50)));
            totalRequestSize += request.ramBytesUsed();
            assertTrue(request.ramBytesUsed() > request.source().length());
            bulkRequest.add(request);
        }

        final long bulkRequestSize = bulkRequest.ramBytesUsed();
        final long bulkShardRequestSize = totalRequestSize;

        try {
            final ActionFuture<BulkResponse> successFuture = client(coordinatingOnlyNode).bulk(bulkRequest);
            replicationSendPointReached.await();

            WriteMemoryLimits primaryWriteLimits = internalCluster().getInstance(WriteMemoryLimits.class, primaryName);
            WriteMemoryLimits replicaWriteLimits = internalCluster().getInstance(WriteMemoryLimits.class, replicaName);
            WriteMemoryLimits coordinatingWriteLimits = internalCluster().getInstance(WriteMemoryLimits.class, coordinatingOnlyNode);

            assertThat(primaryWriteLimits.getWriteBytes(), greaterThan(bulkShardRequestSize));
            assertEquals(0, primaryWriteLimits.getReplicaWriteBytes());
            assertEquals(0, replicaWriteLimits.getWriteBytes());
            assertEquals(0, replicaWriteLimits.getReplicaWriteBytes());
            assertEquals(bulkRequestSize, coordinatingWriteLimits.getWriteBytes());
            assertEquals(0, coordinatingWriteLimits.getReplicaWriteBytes());

            latchBlockingReplicationSend.countDown();

            IndexRequest request = new IndexRequest(INDEX_NAME).id(UUIDs.base64UUID())
                .source(Collections.singletonMap("key", randomAlphaOfLength(50)));
            final BulkRequest secondBulkRequest = new BulkRequest();
            secondBulkRequest.add(request);

            // Use the primary or the replica data node as the coordinating node this time
            boolean usePrimaryAsCoordinatingNode = randomBoolean();
            final ActionFuture<BulkResponse> secondFuture;
            if (usePrimaryAsCoordinatingNode) {
                secondFuture = client(primaryName).bulk(secondBulkRequest);
            } else {
                secondFuture = client(replicaName).bulk(secondBulkRequest);
            }

            final long secondBulkRequestSize = secondBulkRequest.ramBytesUsed();
            final long secondBulkShardRequestSize = request.ramBytesUsed();

            if (usePrimaryAsCoordinatingNode) {
                assertThat(primaryWriteLimits.getWriteBytes(), greaterThan(bulkShardRequestSize + secondBulkRequestSize));
                assertEquals(0, replicaWriteLimits.getWriteBytes());
            } else {
                assertThat(primaryWriteLimits.getWriteBytes(), greaterThan(bulkShardRequestSize));
                assertEquals(secondBulkRequestSize, replicaWriteLimits.getWriteBytes());
            }
            assertEquals(bulkRequestSize, coordinatingWriteLimits.getWriteBytes());
            assertBusy(() -> assertThat(replicaWriteLimits.getReplicaWriteBytes(),
                greaterThan(bulkShardRequestSize + secondBulkShardRequestSize)));

            replicaRelease.close();

            successFuture.actionGet();
            secondFuture.actionGet();

            assertEquals(0, primaryWriteLimits.getWriteBytes());
            assertEquals(0, primaryWriteLimits.getReplicaWriteBytes());
            assertEquals(0, replicaWriteLimits.getWriteBytes());
            assertEquals(0, replicaWriteLimits.getReplicaWriteBytes());
            assertEquals(0, coordinatingWriteLimits.getWriteBytes());
            assertEquals(0, coordinatingWriteLimits.getReplicaWriteBytes());
        } finally {
            if (replicationSendPointReached.getCount() > 0) {
                replicationSendPointReached.countDown();
            }
            replicaRelease.close();
            if (latchBlockingReplicationSend.getCount() > 0) {
                latchBlockingReplicationSend.countDown();
            }
            replicaRelease.close();
            primaryTransportService.clearAllRules();
        }
    }

    public void testWriteCanBeRejectedAtCoordinatingLevel() throws Exception {
        assertAcked(prepareCreate(INDEX_NAME, Settings.builder()
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 1)));
        ensureGreen(INDEX_NAME);

        Tuple<String, String> primaryReplicaNodeNames = getPrimaryReplicaNodeNames();
        String primaryName = primaryReplicaNodeNames.v1();
        String replicaName = primaryReplicaNodeNames.v2();
        String coordinatingOnlyNode = getCoordinatingOnlyNode();

        final BulkRequest bulkRequest = new BulkRequest();
        int totalRequestSize = 0;
        for (int i = 0; i < 80; ++i) {
            IndexRequest request = new IndexRequest(INDEX_NAME).id(UUIDs.base64UUID())
                .source(Collections.singletonMap("key", randomAlphaOfLength(50)));
            totalRequestSize += request.ramBytesUsed();
            assertTrue(request.ramBytesUsed() > request.source().length());
            bulkRequest.add(request);
        }

        final long bulkRequestSize = bulkRequest.ramBytesUsed();
        final long bulkShardRequestSize = totalRequestSize;

        final ThreadPool replicaThreadPool = internalCluster().getInstance(ThreadPool.class, replicaName);

        try (Releasable replicaRelease = blockReplicas(replicaThreadPool)) {
            final ActionFuture<BulkResponse> successFuture = client(coordinatingOnlyNode).bulk(bulkRequest);

            WriteMemoryLimits primaryWriteLimits = internalCluster().getInstance(WriteMemoryLimits.class, primaryName);
            WriteMemoryLimits replicaWriteLimits = internalCluster().getInstance(WriteMemoryLimits.class, replicaName);
            WriteMemoryLimits coordinatingWriteLimits = internalCluster().getInstance(WriteMemoryLimits.class, coordinatingOnlyNode);

            assertBusy(() -> {
                assertThat(primaryWriteLimits.getWriteBytes(), greaterThan(bulkShardRequestSize));
                assertEquals(0, primaryWriteLimits.getReplicaWriteBytes());
                assertEquals(0, replicaWriteLimits.getWriteBytes());
                assertThat(replicaWriteLimits.getReplicaWriteBytes(), greaterThan(bulkShardRequestSize));
                assertEquals(bulkRequestSize, coordinatingWriteLimits.getWriteBytes());
                assertEquals(0, coordinatingWriteLimits.getReplicaWriteBytes());
            });

            Settings.Builder settings = Settings.builder().put(WriteMemoryLimits.MAX_INDEXING_BYTES.getKey(), bulkShardRequestSize + "B");
            client().admin().cluster().prepareUpdateSettings().setPersistentSettings(settings).get();

            IndexRequest request = new IndexRequest(INDEX_NAME).id(UUIDs.base64UUID())
                .source(Collections.singletonMap("key", randomAlphaOfLength(50)));
            final BulkRequest rejectedBulkRequest = new BulkRequest();
            rejectedBulkRequest.add(request);

            expectThrows(EsRejectedExecutionException.class, () -> {
                if (randomBoolean()) {
                    client(coordinatingOnlyNode).bulk(rejectedBulkRequest).actionGet();
                } else if (randomBoolean()) {
                    client(primaryName).bulk(rejectedBulkRequest).actionGet();
                } else {
                    client(replicaName).bulk(rejectedBulkRequest).actionGet();
                }
            });

            replicaRelease.close();

            successFuture.actionGet();

            assertEquals(0, primaryWriteLimits.getWriteBytes());
            assertEquals(0, primaryWriteLimits.getReplicaWriteBytes());
            assertEquals(0, replicaWriteLimits.getWriteBytes());
            assertEquals(0, replicaWriteLimits.getReplicaWriteBytes());
            assertEquals(0, coordinatingWriteLimits.getWriteBytes());
            assertEquals(0, coordinatingWriteLimits.getReplicaWriteBytes());
        } finally {
            Settings.Builder settings = Settings.builder().put(WriteMemoryLimits.MAX_INDEXING_BYTES.getKey(), (String) null);
            client().admin().cluster().prepareUpdateSettings().setPersistentSettings(settings).get();
        }
    }

    public void testWriteCanBeRejectedAtPrimaryLevel() throws Exception {
        assertAcked(prepareCreate(INDEX_NAME, Settings.builder()
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 1)));
        ensureGreen(INDEX_NAME);

        Tuple<String, String> primaryReplicaNodeNames = getPrimaryReplicaNodeNames();
        String primaryName = primaryReplicaNodeNames.v1();
        String replicaName = primaryReplicaNodeNames.v2();
        String coordinatingOnlyNode = getCoordinatingOnlyNode();

        final BulkRequest bulkRequest = new BulkRequest();
        int totalRequestSize = 0;
        for (int i = 0; i < 80; ++i) {
            IndexRequest request = new IndexRequest(INDEX_NAME).id(UUIDs.base64UUID())
                .source(Collections.singletonMap("key", randomAlphaOfLength(50)));
            totalRequestSize += request.ramBytesUsed();
            assertTrue(request.ramBytesUsed() > request.source().length());
            bulkRequest.add(request);
        }

        final long bulkShardRequestSize = totalRequestSize;

        final ThreadPool replicaThreadPool = internalCluster().getInstance(ThreadPool.class, replicaName);

        try (Releasable replicaRelease = blockReplicas(replicaThreadPool)) {
            final ActionFuture<BulkResponse> successFuture = client(primaryName).bulk(bulkRequest);

            WriteMemoryLimits primaryWriteLimits = internalCluster().getInstance(WriteMemoryLimits.class, primaryName);
            WriteMemoryLimits replicaWriteLimits = internalCluster().getInstance(WriteMemoryLimits.class, replicaName);
            WriteMemoryLimits coordinatingWriteLimits = internalCluster().getInstance(WriteMemoryLimits.class, coordinatingOnlyNode);

            assertBusy(() -> {
                assertThat(primaryWriteLimits.getWriteBytes(), greaterThan(bulkShardRequestSize));
                assertEquals(0, primaryWriteLimits.getReplicaWriteBytes());
                assertEquals(0, replicaWriteLimits.getWriteBytes());
                assertThat(replicaWriteLimits.getReplicaWriteBytes(), greaterThan(bulkShardRequestSize));
                assertEquals(0, coordinatingWriteLimits.getWriteBytes());
                assertEquals(0, coordinatingWriteLimits.getReplicaWriteBytes());
            });

            Settings.Builder settings = Settings.builder().put(WriteMemoryLimits.MAX_INDEXING_BYTES.getKey(), bulkShardRequestSize + "B");
            client().admin().cluster().prepareUpdateSettings().setPersistentSettings(settings).get();

            IndexRequest request = new IndexRequest(INDEX_NAME).id(UUIDs.base64UUID())
                .source(Collections.singletonMap("key", randomAlphaOfLength(50)));
            final BulkRequest rejectedBulkRequest = new BulkRequest();
            rejectedBulkRequest.add(request);

            BulkResponse responses = client(coordinatingOnlyNode).bulk(rejectedBulkRequest).actionGet();
            assertTrue(responses.hasFailures());
            assertThat(responses.getItems()[0].getFailure().getCause().getCause(), instanceOf(EsRejectedExecutionException.class));

            replicaRelease.close();

            successFuture.actionGet();

            assertEquals(0, primaryWriteLimits.getWriteBytes());
            assertEquals(0, primaryWriteLimits.getReplicaWriteBytes());
            assertEquals(0, replicaWriteLimits.getWriteBytes());
            assertEquals(0, replicaWriteLimits.getReplicaWriteBytes());
            assertEquals(0, coordinatingWriteLimits.getWriteBytes());
            assertEquals(0, coordinatingWriteLimits.getReplicaWriteBytes());
        } finally {
            Settings.Builder settings = Settings.builder().put(WriteMemoryLimits.MAX_INDEXING_BYTES.getKey(), (String) null);
            client().admin().cluster().prepareUpdateSettings().setPersistentSettings(settings).get();
        }
    }

    private String getCoordinatingOnlyNode() {
        return client().admin().cluster().prepareState().get().getState().nodes().getCoordinatingOnlyNodes().iterator().next()
            .value.getName();
    }

    private Tuple<String, String> getPrimaryReplicaNodeNames() {
        IndicesStatsResponse response = client().admin().indices().prepareStats(INDEX_NAME).get();
        String primaryId = Stream.of(response.getShards())
            .map(ShardStats::getShardRouting)
            .filter(ShardRouting::primary)
            .findAny()
            .get()
            .currentNodeId();
        String replicaId = Stream.of(response.getShards())
            .map(ShardStats::getShardRouting)
            .filter(sr -> sr.primary() == false)
            .findAny()
            .get()
            .currentNodeId();
        DiscoveryNodes nodes = client().admin().cluster().prepareState().get().getState().nodes();
        String primaryName = nodes.get(primaryId).getName();
        String replicaName = nodes.get(replicaId).getName();
        return new Tuple<>(primaryName, replicaName);
    }

    private Releasable blockReplicas(ThreadPool threadPool) {
        final CountDownLatch blockReplication = new CountDownLatch(1);
        final int threads = threadPool.info(ThreadPool.Names.WRITE).getMax();
        final CountDownLatch pointReached = new CountDownLatch(threads);
        for (int i = 0; i< threads; ++i) {
            threadPool.executor(ThreadPool.Names.WRITE).execute(() -> {
                try {
                    pointReached.countDown();
                    blockReplication.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        return () -> {
            if (blockReplication.getCount() > 0) {
                blockReplication.countDown();
            }
        };
    }
}
