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

package org.elasticsearch.cluster.routing.allocation;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ESAllocationTestCase;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.RoutingTable;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.ShardRoutingState;
import org.elasticsearch.cluster.routing.allocation.command.AllocateReplicaAllocationCommand;
import org.elasticsearch.cluster.routing.allocation.command.AllocationCommands;
import org.elasticsearch.cluster.routing.allocation.decider.MaxRetryAllocationDecider;
import org.elasticsearch.common.settings.Settings;

import java.util.Collections;
import java.util.List;

public class RetryFailedAllocationTests extends ESAllocationTestCase {

    private MockAllocationService strategy;
    private ClusterState clusterState;
    private final String INDEX_NAME = "index";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MetaData metaData = MetaData.builder().put(IndexMetaData.builder(INDEX_NAME)
            .settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(1)).build();
        RoutingTable routingTable = RoutingTable.builder().addAsNew(metaData.index(INDEX_NAME)).build();
        clusterState = ClusterState.builder(ClusterName.DEFAULT).metaData(metaData).routingTable(routingTable)
            .nodes(DiscoveryNodes.builder().add(newNode("node1")).add(newNode("node2"))).build();
        strategy = createAllocationService(Settings.EMPTY);
    }

    private ShardRouting getPrimary() {
        for (ShardRouting shard: clusterState.getRoutingTable().allShards()) {
            if (shard.getIndexName().equals(INDEX_NAME) && shard.primary()) {
                return shard;
            }
        }
        throw new IllegalArgumentException("No primary found for index: " + INDEX_NAME);
    }

    private ShardRouting getReplica() {
        for (ShardRouting shard: clusterState.getRoutingTable().allShards()) {
            if (shard.getIndexName().equals(INDEX_NAME) && !shard.primary()) {
                return shard;
            }
        }
        throw new IllegalArgumentException("No replica found for index: " + INDEX_NAME);
    }

    public void testRetryFailedResetForAllocationCommands() {
        final int retries = MaxRetryAllocationDecider.SETTING_ALLOCATION_MAX_RETRY.get(Settings.EMPTY);
        clusterState = strategy.reroute(clusterState, "initial allocation");
        clusterState = strategy.applyStartedShards(clusterState, Collections.singletonList(getPrimary()));

        // Exhaust all replica allocation attempts with shard failures
        for (int i = 0; i < retries; i++) {
            List<FailedShard> failedShards = Collections.singletonList(
                new FailedShard(getReplica(), "failing-shard::attempt-" + i,
                    new UnsupportedOperationException(), randomBoolean()));
            clusterState = strategy.applyFailedShards(clusterState, failedShards);
            clusterState = strategy.reroute(clusterState, "allocation retry attempt-" + i);
        }

        // Now allocate replica with retry_failed flag set
        AllocationService.CommandsResult result = strategy.reroute(clusterState,
            new AllocationCommands(new AllocateReplicaAllocationCommand(INDEX_NAME, 0,
                getPrimary().currentNodeId().equals("node1") ? "node2" : "node1")),
            false, true);
        clusterState = result.getClusterState();

        assertEquals(ShardRoutingState.INITIALIZING, getReplica().state());
        clusterState = strategy.applyStartedShards(clusterState, Collections.singletonList(getReplica()));
        assertEquals(ShardRoutingState.STARTED, getReplica().state());
        assertFalse(clusterState.getRoutingNodes().hasUnassignedShards());
    }
}
