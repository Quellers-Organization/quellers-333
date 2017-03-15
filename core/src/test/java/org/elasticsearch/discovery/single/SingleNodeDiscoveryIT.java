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

package org.elasticsearch.discovery.single;

import org.apache.lucene.util.IOUtils;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.discovery.zen.PingContextProvider;
import org.elasticsearch.discovery.zen.UnicastHostsProvider;
import org.elasticsearch.discovery.zen.UnicastZenPing;
import org.elasticsearch.discovery.zen.ZenPing;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.transport.MockTransportService;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.Matchers.equalTo;

@ESIntegTestCase.ClusterScope(
        numDataNodes = 1,
        numClientNodes = 0,
        supportsDedicatedMasters = false,
        autoMinMasterNodes = false)
public class SingleNodeDiscoveryIT extends ESIntegTestCase {

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings
                .builder()
                .put(super.nodeSettings(nodeOrdinal))
                .put("discovery.type", "single-node")
                .build();
    }

    public void testDoesNotPing() throws IOException, InterruptedException, ExecutionException {
        final Settings empty = Settings.EMPTY;
        final Version version = Version.CURRENT;
        final Stack<Closeable> closeables = new Stack<>();
        final TestThreadPool threadPool = new TestThreadPool(getClass().getName());
        try {
            final MockTransportService pingTransport =
                    MockTransportService.createNewService(empty, version, threadPool, null);
            pingTransport.start();
            closeables.push(pingTransport);
            final TransportService nodeTransport =
                    internalCluster().getInstance(TransportService.class);
            final UnicastHostsProvider provider =
                    () -> Collections.singletonList(nodeTransport.getLocalNode());
            final UnicastZenPing unicastZenPing =
                    new UnicastZenPing(empty, threadPool, pingTransport, provider);
            final DiscoveryNodes nodes =
                    DiscoveryNodes.builder().add(pingTransport.getLocalNode()).build();
            final ClusterName clusterName = new ClusterName(internalCluster().getClusterName());
            final ClusterState state = ClusterState.builder(clusterName).nodes(nodes).build();
            unicastZenPing.start(new PingContextProvider() {
                @Override
                public ClusterState clusterState() {
                    return state;
                }

                @Override
                public DiscoveryNodes nodes() {
                    return DiscoveryNodes.builder().add(nodeTransport.getLocalNode()).build();
                }
            });
            closeables.push(unicastZenPing);
            final CompletableFuture<ZenPing.PingCollection> responses = new CompletableFuture<>();
            unicastZenPing.ping(responses::complete, TimeValue.timeValueSeconds(3));
            responses.get();
            assertThat(responses.get().size(), equalTo(0));
        } finally {
            while (!closeables.isEmpty()) {
                IOUtils.closeWhileHandlingException(closeables.pop());
            }
            terminate(threadPool);
        }
    }

}
