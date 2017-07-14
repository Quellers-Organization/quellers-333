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

package org.elasticsearch.action.support.replication;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.support.WriteResponse;
import org.elasticsearch.action.support.replication.ReplicationOperation.ReplicaResponse;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.action.shard.ShardStateAction;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.routing.IndexShardRoutingTable;
import org.elasticsearch.cluster.routing.RoutingNode;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.ShardRoutingState;
import org.elasticsearch.cluster.routing.TestShardRouting;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.IndexShardState;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.shard.ShardNotFoundException;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.node.NodeClosedException;
import org.elasticsearch.test.ClusterServiceUtils;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.transport.CapturingTransport;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportException;
import org.elasticsearch.transport.TransportResponse;
import org.elasticsearch.transport.TransportService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.elasticsearch.test.ClusterServiceUtils.createClusterService;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TransportWriteActionTests extends ESTestCase {

    private static ThreadPool threadPool;

    private ClusterService clusterService;
    private IndexShard indexShard;
    private Translog.Location location;

    @BeforeClass
    public static void beforeClass() {
        threadPool = new TestThreadPool("ShardReplicationTests");
    }

    @Before
    public void initCommonMocks() {
        indexShard = mock(IndexShard.class);
        location = mock(Translog.Location.class);
        clusterService = createClusterService(threadPool);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        clusterService.close();
    }

    @AfterClass
    public static void afterClass() {
        ThreadPool.terminate(threadPool, 30, TimeUnit.SECONDS);
        threadPool = null;
    }

    <T> void assertListenerThrows(String msg, PlainActionFuture<T> listener, Class<?> klass) throws InterruptedException {
        try {
            listener.get();
            fail(msg);
        } catch (ExecutionException ex) {
            assertThat(ex.getCause(), instanceOf(klass));
        }
    }

    public void testPrimaryNoRefreshCall() throws Exception {
        TestRequest request = new TestRequest();
        request.setRefreshPolicy(RefreshPolicy.NONE); // The default, but we'll set it anyway just to be explicit
        TestAction testAction = new TestAction();
        TransportWriteAction.WritePrimaryResult<TestRequest, TestResponse> result =
                testAction.shardOperationOnPrimary(request, indexShard);
        CapturingActionListener<TestResponse> listener = new CapturingActionListener<>();
        result.respond(listener);
        assertNotNull(listener.response);
        assertNull(listener.failure);
        verify(indexShard, never()).refresh(any());
        verify(indexShard, never()).addRefreshListener(any(), any());
    }

    public void testReplicaNoRefreshCall() throws Exception {
        TestRequest request = new TestRequest();
        request.setRefreshPolicy(RefreshPolicy.NONE); // The default, but we'll set it anyway just to be explicit
        TestAction testAction = new TestAction();
        TransportWriteAction.WriteReplicaResult<TestRequest> result =
                testAction.shardOperationOnReplica(request, indexShard);
        CapturingActionListener<TransportResponse.Empty> listener = new CapturingActionListener<>();
        result.respond(listener);
        assertNotNull(listener.response);
        assertNull(listener.failure);
        verify(indexShard, never()).refresh(any());
        verify(indexShard, never()).addRefreshListener(any(), any());
    }

    public void testPrimaryImmediateRefresh() throws Exception {
        TestRequest request = new TestRequest();
        request.setRefreshPolicy(RefreshPolicy.IMMEDIATE);
        TestAction testAction = new TestAction();
        TransportWriteAction.WritePrimaryResult<TestRequest, TestResponse> result =
                testAction.shardOperationOnPrimary(request, indexShard);
        CapturingActionListener<TestResponse> listener = new CapturingActionListener<>();
        result.respond(listener);
        assertNotNull(listener.response);
        assertNull(listener.failure);
        assertTrue(listener.response.forcedRefresh);
        verify(indexShard).refresh("refresh_flag_index");
        verify(indexShard, never()).addRefreshListener(any(), any());
    }

    public void testReplicaImmediateRefresh() throws Exception {
        TestRequest request = new TestRequest();
        request.setRefreshPolicy(RefreshPolicy.IMMEDIATE);
        TestAction testAction = new TestAction();
        TransportWriteAction.WriteReplicaResult<TestRequest> result =
                testAction.shardOperationOnReplica(request, indexShard);
        CapturingActionListener<TransportResponse.Empty> listener = new CapturingActionListener<>();
        result.respond(listener);
        assertNotNull(listener.response);
        assertNull(listener.failure);
        verify(indexShard).refresh("refresh_flag_index");
        verify(indexShard, never()).addRefreshListener(any(), any());
    }

    public void testPrimaryWaitForRefresh() throws Exception {
        TestRequest request = new TestRequest();
        request.setRefreshPolicy(RefreshPolicy.WAIT_UNTIL);

        TestAction testAction = new TestAction();
        TransportWriteAction.WritePrimaryResult<TestRequest, TestResponse> result =
                testAction.shardOperationOnPrimary(request, indexShard);
        CapturingActionListener<TestResponse> listener = new CapturingActionListener<>();
        result.respond(listener);
        assertNull(listener.response); // Haven't reallresponded yet

        @SuppressWarnings({ "unchecked", "rawtypes" })
        ArgumentCaptor<Consumer<Boolean>> refreshListener = ArgumentCaptor.forClass((Class) Consumer.class);
        verify(indexShard, never()).refresh(any());
        verify(indexShard).addRefreshListener(any(), refreshListener.capture());

        // Now we can fire the listener manually and we'll get a response
        boolean forcedRefresh = randomBoolean();
        refreshListener.getValue().accept(forcedRefresh);
        assertNotNull(listener.response);
        assertNull(listener.failure);
        assertEquals(forcedRefresh, listener.response.forcedRefresh);
    }

    public void testReplicaWaitForRefresh() throws Exception {
        TestRequest request = new TestRequest();
        request.setRefreshPolicy(RefreshPolicy.WAIT_UNTIL);
        TestAction testAction = new TestAction();
        TransportWriteAction.WriteReplicaResult<TestRequest> result = testAction.shardOperationOnReplica(request, indexShard);
        CapturingActionListener<TransportResponse.Empty> listener = new CapturingActionListener<>();
        result.respond(listener);
        assertNull(listener.response); // Haven't responded yet
        @SuppressWarnings({ "unchecked", "rawtypes" })
        ArgumentCaptor<Consumer<Boolean>> refreshListener = ArgumentCaptor.forClass((Class) Consumer.class);
        verify(indexShard, never()).refresh(any());
        verify(indexShard).addRefreshListener(any(), refreshListener.capture());

        // Now we can fire the listener manually and we'll get a response
        boolean forcedRefresh = randomBoolean();
        refreshListener.getValue().accept(forcedRefresh);
        assertNotNull(listener.response);
        assertNull(listener.failure);
    }

    public void testDocumentFailureInShardOperationOnPrimary() throws Exception {
        TestRequest request = new TestRequest();
        TestAction testAction = new TestAction(true, true);
        TransportWriteAction.WritePrimaryResult<TestRequest, TestResponse> writePrimaryResult =
                testAction.shardOperationOnPrimary(request, indexShard);
        CapturingActionListener<TestResponse> listener = new CapturingActionListener<>();
        writePrimaryResult.respond(listener);
        assertNull(listener.response);
        assertNotNull(listener.failure);
    }

    public void testDocumentFailureInShardOperationOnReplica() throws Exception {
        TestRequest request = new TestRequest();
        TestAction testAction = new TestAction(randomBoolean(), true);
        TransportWriteAction.WriteReplicaResult<TestRequest> writeReplicaResult =
                testAction.shardOperationOnReplica(request, indexShard);
        CapturingActionListener<TransportResponse.Empty> listener = new CapturingActionListener<>();
        writeReplicaResult.respond(listener);
        assertNull(listener.response);
        assertNotNull(listener.failure);
    }

    public void testReplicaProxy() throws InterruptedException, ExecutionException {
        CapturingTransport transport = new CapturingTransport();
        TransportService transportService = new TransportService(clusterService.getSettings(), transport, threadPool,
                TransportService.NOOP_TRANSPORT_INTERCEPTOR, x -> clusterService.localNode(), null);
        transportService.start();
        transportService.acceptIncomingRequests();
        ShardStateAction shardStateAction = new ShardStateAction(Settings.EMPTY, clusterService, transportService, null, null, threadPool);
        TestAction action = new TestAction(Settings.EMPTY, "testAction", transportService,
                clusterService, shardStateAction, threadPool);
        ReplicationOperation.Replicas proxy = action.newReplicasProxy();
        final String index = "test";
        final ShardId shardId = new ShardId(index, "_na_", 0);
        ClusterState state = ClusterStateCreationUtils.stateWithActivePrimary(index, true, 1 + randomInt(3), randomInt(2));
        logger.info("using state: {}", state);
        ClusterServiceUtils.setState(clusterService, state);

        // check that at unknown node fails
        PlainActionFuture<ReplicaResponse> listener = new PlainActionFuture<>();
        ShardRoutingState routingState = randomFrom(ShardRoutingState.INITIALIZING, ShardRoutingState.STARTED,
            ShardRoutingState.RELOCATING);
        proxy.performOn(
            TestShardRouting.newShardRouting(shardId, "NOT THERE",
                routingState == ShardRoutingState.RELOCATING ? state.nodes().iterator().next().getId() : null, false, routingState),
                new TestRequest(),
                randomNonNegativeLong(), listener);
        assertTrue(listener.isDone());
        assertListenerThrows("non existent node should throw a NoNodeAvailableException", listener, NoNodeAvailableException.class);

        final IndexShardRoutingTable shardRoutings = state.routingTable().shardRoutingTable(shardId);
        final ShardRouting replica = randomFrom(shardRoutings.replicaShards().stream()
            .filter(ShardRouting::assignedToNode).collect(Collectors.toList()));
        listener = new PlainActionFuture<>();
        proxy.performOn(replica, new TestRequest(), randomNonNegativeLong(), listener);
        assertFalse(listener.isDone());

        CapturingTransport.CapturedRequest[] captures = transport.getCapturedRequestsAndClear();
        assertThat(captures, arrayWithSize(1));
        if (randomBoolean()) {
            final TransportReplicationAction.ReplicaResponse response = new TransportReplicationAction.ReplicaResponse(randomLong());
            transport.handleResponse(captures[0].requestId, response);
            assertTrue(listener.isDone());
            assertThat(listener.get(), equalTo(response));
        } else if (randomBoolean()) {
            transport.handleRemoteError(captures[0].requestId, new ElasticsearchException("simulated"));
            assertTrue(listener.isDone());
            assertListenerThrows("listener should reflect remote error", listener, ElasticsearchException.class);
        } else {
            transport.handleError(captures[0].requestId, new TransportException("simulated"));
            assertTrue(listener.isDone());
            assertListenerThrows("listener should reflect remote error", listener, TransportException.class);
        }

        AtomicReference<Object> failure = new AtomicReference<>();
        AtomicReference<Object> ignoredFailure = new AtomicReference<>();
        AtomicBoolean success = new AtomicBoolean();
        proxy.failShardIfNeeded(replica, randomIntBetween(1, 10), "test", new ElasticsearchException("simulated"),
                () -> success.set(true), failure::set, ignoredFailure::set
        );
        CapturingTransport.CapturedRequest[] shardFailedRequests = transport.getCapturedRequestsAndClear();
        // A write replication action proxy should fail the shard
        assertEquals(1, shardFailedRequests.length);
        CapturingTransport.CapturedRequest shardFailedRequest = shardFailedRequests[0];
        ShardStateAction.ShardEntry shardEntry = (ShardStateAction.ShardEntry) shardFailedRequest.request;
        // the shard the request was sent to and the shard to be failed should be the same
        assertEquals(shardEntry.getShardId(), replica.shardId());
        assertEquals(shardEntry.getAllocationId(), replica.allocationId().getId());
        if (randomBoolean()) {
            // simulate success
            transport.handleResponse(shardFailedRequest.requestId, TransportResponse.Empty.INSTANCE);
            assertTrue(success.get());
            assertNull(failure.get());
            assertNull(ignoredFailure.get());

        } else if (randomBoolean()) {
            // simulate the primary has been demoted
            transport.handleRemoteError(shardFailedRequest.requestId,
                new ShardStateAction.NoLongerPrimaryShardException(replica.shardId(),
                    "shard-failed-test"));
            assertFalse(success.get());
            assertNotNull(failure.get());
            assertNull(ignoredFailure.get());

        } else {
            // simulated an "ignored" exception
            transport.handleRemoteError(shardFailedRequest.requestId,
                new NodeClosedException(state.nodes().getLocalNode()));
            assertFalse(success.get());
            assertNull(failure.get());
            assertNotNull(ignoredFailure.get());
        }
    }

    private class TestAction extends TransportWriteAction<TestRequest, TestRequest, TestResponse> {

        private final boolean withDocumentFailureOnPrimary;
        private final boolean withDocumentFailureOnReplica;

        protected TestAction() {
            this(false, false);
        }

        protected TestAction(boolean withDocumentFailureOnPrimary, boolean withDocumentFailureOnReplica) {
            super(Settings.EMPTY, "test",
                    new TransportService(Settings.EMPTY, null, null, TransportService.NOOP_TRANSPORT_INTERCEPTOR, x -> null, null), null,
                    null, null, null, new ActionFilters(new HashSet<>()), new IndexNameExpressionResolver(Settings.EMPTY), TestRequest::new,
                    TestRequest::new, ThreadPool.Names.SAME);
            this.withDocumentFailureOnPrimary = withDocumentFailureOnPrimary;
            this.withDocumentFailureOnReplica = withDocumentFailureOnReplica;
        }

        protected TestAction(Settings settings, String actionName, TransportService transportService,
                             ClusterService clusterService, ShardStateAction shardStateAction, ThreadPool threadPool) {
            super(settings, actionName, transportService, clusterService,
                    mockIndicesService(clusterService), threadPool, shardStateAction,
                    new ActionFilters(new HashSet<>()), new IndexNameExpressionResolver(Settings.EMPTY),
                    TestRequest::new, TestRequest::new, ThreadPool.Names.SAME);
            this.withDocumentFailureOnPrimary = false;
            this.withDocumentFailureOnReplica = false;
        }


        @Override
        protected TestResponse newResponseInstance() {
            return new TestResponse();
        }

        @Override
        protected WritePrimaryResult<TestRequest, TestResponse> shardOperationOnPrimary(
                TestRequest request, IndexShard primary) throws Exception {
            final WritePrimaryResult<TestRequest, TestResponse> primaryResult;
            if (withDocumentFailureOnPrimary) {
                primaryResult = new WritePrimaryResult<>(request, null, null, new RuntimeException("simulated"), primary, logger);
            } else {
                primaryResult = new WritePrimaryResult<>(request, new TestResponse(), location, null, primary, logger);
            }
            return primaryResult;
        }

        @Override
        protected WriteReplicaResult<TestRequest> shardOperationOnReplica(TestRequest request, IndexShard replica) throws Exception {
            final WriteReplicaResult<TestRequest> replicaResult;
            if (withDocumentFailureOnReplica) {
                replicaResult = new WriteReplicaResult<>(request, null, new RuntimeException("simulated"), replica, logger);
            } else {
                replicaResult = new WriteReplicaResult<>(request, location, null, replica, logger);
            }
            return replicaResult;
        }
    }

    final IndexService mockIndexService(final IndexMetaData indexMetaData, ClusterService clusterService) {
        final IndexService indexService = mock(IndexService.class);
        when(indexService.getShard(anyInt())).then(invocation -> {
            int shard = (Integer) invocation.getArguments()[0];
            final ShardId shardId = new ShardId(indexMetaData.getIndex(), shard);
            if (shard > indexMetaData.getNumberOfShards()) {
                throw new ShardNotFoundException(shardId);
            }
            return mockIndexShard(shardId, clusterService);
        });
        return indexService;
    }

    final IndicesService mockIndicesService(ClusterService clusterService) {
        final IndicesService indicesService = mock(IndicesService.class);
        when(indicesService.indexServiceSafe(any(Index.class))).then(invocation -> {
            Index index = (Index)invocation.getArguments()[0];
            final ClusterState state = clusterService.state();
            final IndexMetaData indexSafe = state.metaData().getIndexSafe(index);
            return mockIndexService(indexSafe, clusterService);
        });
        when(indicesService.indexService(any(Index.class))).then(invocation -> {
            Index index = (Index) invocation.getArguments()[0];
            final ClusterState state = clusterService.state();
            if (state.metaData().hasIndex(index.getName())) {
                final IndexMetaData indexSafe = state.metaData().getIndexSafe(index);
                return mockIndexService(clusterService.state().metaData().getIndexSafe(index), clusterService);
            } else {
                return null;
            }
        });
        return indicesService;
    }

    private final AtomicInteger count = new AtomicInteger(0);

    private final AtomicBoolean isRelocated = new AtomicBoolean(false);

    private IndexShard mockIndexShard(ShardId shardId, ClusterService clusterService) {
        final IndexShard indexShard = mock(IndexShard.class);
        doAnswer(invocation -> {
            ActionListener<Releasable> callback = (ActionListener<Releasable>) invocation.getArguments()[0];
            count.incrementAndGet();
            callback.onResponse(count::decrementAndGet);
            return null;
        }).when(indexShard).acquirePrimaryOperationPermit(any(ActionListener.class), anyString());
        doAnswer(invocation -> {
            long term = (Long)invocation.getArguments()[0];
            ActionListener<Releasable> callback = (ActionListener<Releasable>) invocation.getArguments()[1];
            final long primaryTerm = indexShard.getPrimaryTerm();
            if (term < primaryTerm) {
                throw new IllegalArgumentException(String.format(Locale.ROOT, "%s operation term [%d] is too old (current [%d])",
                    shardId, term, primaryTerm));
            }
            count.incrementAndGet();
            callback.onResponse(count::decrementAndGet);
            return null;
        }).when(indexShard).acquireReplicaOperationPermit(anyLong(), anyLong(), any(ActionListener.class), anyString());
        when(indexShard.routingEntry()).thenAnswer(invocationOnMock -> {
            final ClusterState state = clusterService.state();
            final RoutingNode node = state.getRoutingNodes().node(state.nodes().getLocalNodeId());
            final ShardRouting routing = node.getByShardId(shardId);
            if (routing == null) {
                throw new ShardNotFoundException(shardId, "shard is no longer assigned to current node");
            }
            return routing;
        });
        when(indexShard.state()).thenAnswer(invocationOnMock -> isRelocated.get() ? IndexShardState.RELOCATED : IndexShardState.STARTED);
        doThrow(new AssertionError("failed shard is not supported")).when(indexShard).failShard(anyString(), any(Exception.class));
        when(indexShard.getPrimaryTerm()).thenAnswer(i ->
            clusterService.state().metaData().getIndexSafe(shardId.getIndex()).primaryTerm(shardId.id()));
        return indexShard;
    }

    private static class TestRequest extends ReplicatedWriteRequest<TestRequest> {
        TestRequest() {
            setShardId(new ShardId("test", "test", 1));
        }

        @Override
        public String toString() {
            return "TestRequest{}";
        }
    }

    private static class TestResponse extends ReplicationResponse implements WriteResponse {
        boolean forcedRefresh;

        @Override
        public void setForcedRefresh(boolean forcedRefresh) {
            this.forcedRefresh = forcedRefresh;
        }
    }

    private static class CapturingActionListener<R> implements ActionListener<R> {
        private R response;
        private Exception failure;

        @Override
        public void onResponse(R response) {
            this.response = response;
        }

        @Override
        public void onFailure(Exception failure) {
            this.failure = failure;
        }
    }
}
