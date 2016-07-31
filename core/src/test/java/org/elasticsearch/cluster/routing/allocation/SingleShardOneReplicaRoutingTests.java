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
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.RoutingNodes;
import org.elasticsearch.cluster.routing.RoutingTable;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ESAllocationTestCase;

import static org.elasticsearch.cluster.routing.ShardRoutingState.INITIALIZING;
import static org.elasticsearch.cluster.routing.ShardRoutingState.STARTED;
import static org.elasticsearch.cluster.routing.ShardRoutingState.UNASSIGNED;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 *
 */
public class SingleShardOneReplicaRoutingTests extends ESAllocationTestCase {
    private final ESLogger logger = Loggers.getLogger(SingleShardOneReplicaRoutingTests.class);

    public void testSingleIndexFirstStartPrimaryThenBackups() {
        AllocationService strategy = createAllocationService(Settings.builder().put("cluster.routing.allocation.node_concurrent_recoveries", 10).build());

        logger.info("Building initial routing table");

        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test").settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(1))
                .build();

        RoutingTable routingTable = RoutingTable.builder()
                .addAsNew(metaData.index("test"))
                .build();

        ClusterState clusterState = ClusterState.builder(org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING.getDefault(Settings.EMPTY)).metaData(metaData).routingTable(routingTable).build();

        assertThat(routingTable.index("test").shards().size(), equalTo(1));
        assertThat(routingTable.index("test").shard(0).size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).shards().size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).shards().get(0).state(), equalTo(UNASSIGNED));
        assertThat(routingTable.index("test").shard(0).shards().get(1).state(), equalTo(UNASSIGNED));
        assertThat(routingTable.index("test").shard(0).shards().get(0).currentNodeId(), nullValue());
        assertThat(routingTable.index("test").shard(0).shards().get(1).currentNodeId(), nullValue());

        logger.info("Adding one node and performing rerouting");
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder().add(newNode("node1"))).build();

        RoutingTable prevRoutingTable = routingTable;
        routingTable = strategy.reroute(clusterState, "reroute").routingTable();
        clusterState = ClusterState.builder(clusterState).routingTable(routingTable).build();

        assertThat(prevRoutingTable != routingTable, equalTo(true));
        assertThat(routingTable.index("test").shards().size(), equalTo(1));
        assertThat(routingTable.index("test").shard(0).size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).shards().size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).primaryShard().state(), equalTo(INITIALIZING));
        assertThat(routingTable.index("test").shard(0).primaryShard().currentNodeId(), equalTo("node1"));
        assertThat(routingTable.index("test").shard(0).replicaShards().size(), equalTo(1));
        assertThat(routingTable.index("test").shard(0).replicaShards().get(0).state(), equalTo(UNASSIGNED));
        assertThat(routingTable.index("test").shard(0).replicaShards().get(0).currentNodeId(), nullValue());

        logger.info("Add another node and perform rerouting, nothing will happen since primary shards not started");
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).add(newNode("node2"))).build();
        prevRoutingTable = routingTable;
        routingTable = strategy.reroute(clusterState, "reroute").routingTable();
        clusterState = ClusterState.builder(clusterState).routingTable(routingTable).build();

        assertThat(prevRoutingTable == routingTable, equalTo(true));

        logger.info("Start the primary shard (on node1)");
        RoutingNodes routingNodes = clusterState.getRoutingNodes();
        prevRoutingTable = routingTable;
        routingTable = strategy.applyStartedShards(clusterState, routingNodes.node("node1").shardsWithState(INITIALIZING)).routingTable();
        clusterState = ClusterState.builder(clusterState).routingTable(routingTable).build();

        assertThat(prevRoutingTable != routingTable, equalTo(true));
        assertThat(routingTable.index("test").shards().size(), equalTo(1));
        assertThat(routingTable.index("test").shard(0).size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).shards().size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).primaryShard().state(), equalTo(STARTED));
        assertThat(routingTable.index("test").shard(0).primaryShard().currentNodeId(), equalTo("node1"));
        assertThat(routingTable.index("test").shard(0).replicaShards().size(), equalTo(1));
        // backup shards are initializing as well, we make sure that they recover from primary *started* shards in the IndicesClusterStateService
        assertThat(routingTable.index("test").shard(0).replicaShards().get(0).state(), equalTo(INITIALIZING));
        assertThat(routingTable.index("test").shard(0).replicaShards().get(0).currentNodeId(), equalTo("node2"));


        logger.info("Reroute, nothing should change");
        prevRoutingTable = routingTable;
        routingTable = strategy.reroute(clusterState, "reroute").routingTable();
        assertThat(prevRoutingTable == routingTable, equalTo(true));

        logger.info("Start the backup shard");
        routingNodes = clusterState.getRoutingNodes();
        prevRoutingTable = routingTable;
        routingTable = strategy.applyStartedShards(clusterState, routingNodes.node("node2").shardsWithState(INITIALIZING)).routingTable();
        clusterState = ClusterState.builder(clusterState).routingTable(routingTable).build();

        assertThat(prevRoutingTable != routingTable, equalTo(true));
        assertThat(routingTable.index("test").shards().size(), equalTo(1));
        assertThat(routingTable.index("test").shard(0).size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).shards().size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).primaryShard().state(), equalTo(STARTED));
        assertThat(routingTable.index("test").shard(0).primaryShard().currentNodeId(), equalTo("node1"));
        assertThat(routingTable.index("test").shard(0).replicaShards().size(), equalTo(1));
        assertThat(routingTable.index("test").shard(0).replicaShards().get(0).state(), equalTo(STARTED));
        assertThat(routingTable.index("test").shard(0).replicaShards().get(0).currentNodeId(), equalTo("node2"));

        logger.info("Kill node1, backup shard should become primary");

        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).remove("node1")).build();
        prevRoutingTable = routingTable;
        routingTable = strategy.reroute(clusterState, "reroute").routingTable();
        clusterState = ClusterState.builder(clusterState).routingTable(routingTable).build();

        assertThat(prevRoutingTable != routingTable, equalTo(true));
        assertThat(routingTable.index("test").shards().size(), equalTo(1));
        assertThat(routingTable.index("test").shard(0).size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).shards().size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).primaryShard().state(), equalTo(STARTED));
        assertThat(routingTable.index("test").shard(0).primaryShard().currentNodeId(), equalTo("node2"));
        assertThat(routingTable.index("test").shard(0).replicaShards().size(), equalTo(1));
        // backup shards are initializing as well, we make sure that they recover from primary *started* shards in the IndicesClusterStateService
        assertThat(routingTable.index("test").shard(0).replicaShards().get(0).state(), equalTo(UNASSIGNED));
        assertThat(routingTable.index("test").shard(0).replicaShards().get(0).currentNodeId(), nullValue());

        logger.info("Start another node, backup shard should start initializing");

        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).add(newNode("node3"))).build();
        prevRoutingTable = routingTable;
        routingTable = strategy.reroute(clusterState, "reroute").routingTable();
        clusterState = ClusterState.builder(clusterState).routingTable(routingTable).build();

        assertThat(prevRoutingTable != routingTable, equalTo(true));
        assertThat(routingTable.index("test").shards().size(), equalTo(1));
        assertThat(routingTable.index("test").shard(0).size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).shards().size(), equalTo(2));
        assertThat(routingTable.index("test").shard(0).primaryShard().state(), equalTo(STARTED));
        assertThat(routingTable.index("test").shard(0).primaryShard().currentNodeId(), equalTo("node2"));
        assertThat(routingTable.index("test").shard(0).replicaShards().size(), equalTo(1));
        // backup shards are initializing as well, we make sure that they recover from primary *started* shards in the IndicesClusterStateService
        assertThat(routingTable.index("test").shard(0).replicaShards().get(0).state(), equalTo(INITIALIZING));
        assertThat(routingTable.index("test").shard(0).replicaShards().get(0).currentNodeId(), equalTo("node3"));
    }
}
