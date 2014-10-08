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

package org.elasticsearch.cluster.routing.operation.plain;

import com.google.common.collect.Lists;
import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.ElasticsearchIllegalStateException;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.GroupShardsIterator;
import org.elasticsearch.cluster.routing.IndexRoutingTable;
import org.elasticsearch.cluster.routing.IndexShardRoutingTable;
import org.elasticsearch.cluster.routing.ShardIterator;
import org.elasticsearch.cluster.routing.allocation.decider.AwarenessAllocationDecider;
import org.elasticsearch.cluster.routing.operation.OperationRouting;
import org.elasticsearch.cluster.routing.operation.hash.HashFunction;
import org.elasticsearch.cluster.routing.operation.hash.djb.DjbHashFunction;
import org.elasticsearch.cluster.routing.operation.hash.murmur3.Murmur3HashFunction;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.math.MathUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexShardMissingException;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.indices.IndexMissingException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class PlainOperationRouting extends AbstractComponent implements OperationRouting {

    public static final String SETTING_LEGACY_HASH_FUNCTION = "index.legacy.routing.hash.type";
    public static final String SETTING_LEGACY_USE_TYPE = "index.legacy.routing.use_type";

    // hard-coded hash function as of 2.0
    // older indices will read which hash function to use in their index settings
    private static final HashFunction HASH_FUNCTION = new Murmur3HashFunction();

    private final AwarenessAllocationDecider awarenessAllocationDecider;

    @Inject
    public PlainOperationRouting(Settings settings, AwarenessAllocationDecider awarenessAllocationDecider) {
        super(settings);
        this.awarenessAllocationDecider = awarenessAllocationDecider;
    }

    @Override
    public ShardIterator indexShards(ClusterState clusterState, String index, String type, String id, @Nullable String routing) throws IndexMissingException, IndexShardMissingException {
        return shards(clusterState, index, type, id, routing).shardsIt();
    }

    @Override
    public ShardIterator deleteShards(ClusterState clusterState, String index, String type, String id, @Nullable String routing) throws IndexMissingException, IndexShardMissingException {
        return shards(clusterState, index, type, id, routing).shardsIt();
    }

    @Override
    public ShardIterator getShards(ClusterState clusterState, String index, String type, String id, @Nullable String routing, @Nullable String preference) throws IndexMissingException, IndexShardMissingException {
        return preferenceActiveShardIterator(shards(clusterState, index, type, id, routing), clusterState.nodes().localNodeId(), clusterState.nodes(), preference);
    }

    @Override
    public ShardIterator getShards(ClusterState clusterState, String index, int shardId, @Nullable String preference) throws IndexMissingException, IndexShardMissingException {
        return preferenceActiveShardIterator(shards(clusterState, index, shardId), clusterState.nodes().localNodeId(), clusterState.nodes(), preference);
    }

    @Override
    public GroupShardsIterator broadcastDeleteShards(ClusterState clusterState, String index) throws IndexMissingException {
        return indexRoutingTable(clusterState, index).groupByShardsIt();
    }

    @Override
    public GroupShardsIterator deleteByQueryShards(ClusterState clusterState, String index, @Nullable Set<String> routing) throws IndexMissingException {
        if (routing == null || routing.isEmpty()) {
            return indexRoutingTable(clusterState, index).groupByShardsIt();
        }

        // we use set here and not identity set since we might get duplicates
        HashSet<ShardIterator> set = new HashSet<>();
        IndexRoutingTable indexRouting = indexRoutingTable(clusterState, index);
        for (String r : routing) {
            int shardId = shardId(clusterState, index, null, null, r);
            IndexShardRoutingTable indexShard = indexRouting.shard(shardId);
            if (indexShard == null) {
                throw new IndexShardMissingException(new ShardId(index, shardId));
            }
            set.add(indexShard.shardsRandomIt());
        }
        return new GroupShardsIterator(Lists.newArrayList(set));
    }

    @Override
    public int searchShardsCount(ClusterState clusterState, String[] indices, String[] concreteIndices, @Nullable Map<String, Set<String>> routing, @Nullable String preference) throws IndexMissingException {
        final Set<IndexShardRoutingTable> shards = computeTargetedShards(clusterState, concreteIndices, routing);
        return shards.size();
    }

    @Override
    public GroupShardsIterator searchShards(ClusterState clusterState, String[] indices, String[] concreteIndices, @Nullable Map<String, Set<String>> routing, @Nullable String preference) throws IndexMissingException {
        final Set<IndexShardRoutingTable> shards = computeTargetedShards(clusterState, concreteIndices, routing);
        final Set<ShardIterator> set = new HashSet<>(shards.size());
        for (IndexShardRoutingTable shard : shards) {
            ShardIterator iterator = preferenceActiveShardIterator(shard, clusterState.nodes().localNodeId(), clusterState.nodes(), preference);
            if (iterator != null) {
                set.add(iterator);
            }
        }
        return new GroupShardsIterator(Lists.newArrayList(set));
    }

    private static final Map<String, Set<String>> EMPTY_ROUTING = Collections.emptyMap();

    private Set<IndexShardRoutingTable> computeTargetedShards(ClusterState clusterState, String[] concreteIndices, @Nullable Map<String, Set<String>> routing) throws IndexMissingException {
        routing = routing == null ? EMPTY_ROUTING : routing; // just use an empty map
        final Set<IndexShardRoutingTable> set = new HashSet<>();
        // we use set here and not list since we might get duplicates
        for (String index : concreteIndices) {
            final IndexRoutingTable indexRouting = indexRoutingTable(clusterState, index);
            final Set<String> effectiveRouting = routing.get(index);
            if (effectiveRouting != null) {
                for (String r : effectiveRouting) {
                    int shardId = shardId(clusterState, index, null, null, r);
                    IndexShardRoutingTable indexShard = indexRouting.shard(shardId);
                    if (indexShard == null) {
                        throw new IndexShardMissingException(new ShardId(index, shardId));
                    }
                    // we might get duplicates, but that's ok, they will override one another
                    set.add(indexShard);
                }
            } else {
                for (IndexShardRoutingTable indexShard : indexRouting) {
                    set.add(indexShard);
                }
            }
        }
        return set;
    }

    private ShardIterator preferenceActiveShardIterator(IndexShardRoutingTable indexShard, String localNodeId, DiscoveryNodes nodes, @Nullable String preference) {
        if (preference == null || preference.isEmpty()) {
            String[] awarenessAttributes = awarenessAllocationDecider.awarenessAttributes();
            if (awarenessAttributes.length == 0) {
                return indexShard.activeInitializingShardsRandomIt();
            } else {
                return indexShard.preferAttributesActiveInitializingShardsIt(awarenessAttributes, nodes);
            }
        }
        if (preference.charAt(0) == '_') {
            Preference preferenceType = Preference.parse(preference);
            if (preferenceType == Preference.SHARDS) {
                // starts with _shards, so execute on specific ones
                int index = preference.indexOf(';');

                String shards;
                if (index == -1) {
                    shards = preference.substring(Preference.SHARDS.type().length() + 1);
                } else {
                    shards = preference.substring(Preference.SHARDS.type().length() + 1, index);
                }
                String[] ids = Strings.splitStringByCommaToArray(shards);
                boolean found = false;
                for (String id : ids) {
                    if (Integer.parseInt(id) == indexShard.shardId().id()) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return null;
                }
                // no more preference
                if (index == -1 || index == preference.length() - 1) {
                    String[] awarenessAttributes = awarenessAllocationDecider.awarenessAttributes();
                    if (awarenessAttributes.length == 0) {
                        return indexShard.activeInitializingShardsRandomIt();
                    } else {
                        return indexShard.preferAttributesActiveInitializingShardsIt(awarenessAttributes, nodes);
                    }
                } else {
                    // update the preference and continue
                    preference = preference.substring(index + 1);
                }
            }
            preferenceType = Preference.parse(preference);
            switch (preferenceType) {
                case PREFER_NODE:
                    return indexShard.preferNodeActiveInitializingShardsIt(preference.substring(Preference.PREFER_NODE.type().length() + 1));
                case LOCAL:
                    return indexShard.preferNodeActiveInitializingShardsIt(localNodeId);
                case PRIMARY:
                    return indexShard.primaryActiveInitializingShardIt();
                case PRIMARY_FIRST:
                    return indexShard.primaryFirstActiveInitializingShardsIt();
                case ONLY_LOCAL:
                    return indexShard.onlyNodeActiveInitializingShardsIt(localNodeId);
                case ONLY_NODE:
                    String nodeId = preference.substring(Preference.ONLY_NODE.type().length() + 1);
                    ensureNodeIdExists(nodes, nodeId);
                    return indexShard.onlyNodeActiveInitializingShardsIt(nodeId);
                default:
                    throw new ElasticsearchIllegalArgumentException("unknown preference [" + preferenceType + "]");
            }
        }
        // if not, then use it as the index
        String[] awarenessAttributes = awarenessAllocationDecider.awarenessAttributes();
        if (awarenessAttributes.length == 0) {
            return indexShard.activeInitializingShardsIt(DjbHashFunction.DJB_HASH(preference));
        } else {
            return indexShard.preferAttributesActiveInitializingShardsIt(awarenessAttributes, nodes, DjbHashFunction.DJB_HASH(preference));
        }
    }

    public IndexMetaData indexMetaData(ClusterState clusterState, String index) {
        IndexMetaData indexMetaData = clusterState.metaData().index(index);
        if (indexMetaData == null) {
            throw new IndexMissingException(new Index(index));
        }
        return indexMetaData;
    }

    protected IndexRoutingTable indexRoutingTable(ClusterState clusterState, String index) {
        IndexRoutingTable indexRouting = clusterState.routingTable().index(index);
        if (indexRouting == null) {
            throw new IndexMissingException(new Index(index));
        }
        return indexRouting;
    }


    // either routing is set, or type/id are set

    protected IndexShardRoutingTable shards(ClusterState clusterState, String index, String type, String id, String routing) {
        int shardId = shardId(clusterState, index, type, id, routing);
        return shards(clusterState, index, shardId);
    }

    protected IndexShardRoutingTable shards(ClusterState clusterState, String index, int shardId) {
        IndexShardRoutingTable indexShard = indexRoutingTable(clusterState, index).shard(shardId);
        if (indexShard == null) {
            throw new IndexShardMissingException(new ShardId(index, shardId));
        }
        return indexShard;
    }

    private int shardId(ClusterState clusterState, String index, String type, String id, @Nullable String routing) {
        final IndexMetaData indexMetaData = indexMetaData(clusterState, index);
        final Version createdVersion = Version.indexCreated(indexMetaData.getSettings());
        final HashFunction hashFunction;
        final boolean useType;

        final Class<? extends HashFunction> hashFunctionClass = indexMetaData.getSettings().getAsClass(SETTING_LEGACY_HASH_FUNCTION, null);
        if (createdVersion.onOrAfter(Version.V_2_0_0)) {
            if (hashFunctionClass != null) {
                throw new ElasticsearchIllegalStateException("Index [" + index + "] has the `" + SETTING_LEGACY_HASH_FUNCTION + "` setting while it is not supposed to have it since it was created on " + createdVersion);
            }
            hashFunction = HASH_FUNCTION;
            useType = false;
        } else {
            if (hashFunctionClass == null) {
                throw new ElasticsearchIllegalStateException("Index [" + index + "] misses the `" + SETTING_LEGACY_HASH_FUNCTION + "` setting while recovery from gateway should have added it since it was created on " + createdVersion);
            }
            // TODO: is there a way to make Guice instantiate it instead?
            try {
                hashFunction = hashFunctionClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new ElasticsearchIllegalStateException("Cannot instantiate hash function", e);
            }
            useType = indexMetaData.getSettings().getAsBoolean(SETTING_LEGACY_USE_TYPE, false);
        }

        final int hash;
        if (routing == null) {
            if (!useType) {
                hash = hash(hashFunction, id);
            } else {
                hash = hash(hashFunction, type, id);
            }
        } else {
            hash = hash(hashFunction, routing);
        }
        if (createdVersion.onOrAfter(Version.V_2_0_0)) {
            return MathUtils.mod(hash, indexMetaData.numberOfShards());
        } else {
            return Math.abs(hash % indexMetaData.numberOfShards());
        }
    }

    protected int hash(HashFunction hashFunction, String routing) {
        return hashFunction.hash(routing);
    }

    @Deprecated
    protected int hash(HashFunction hashFunction, String type, String id) {
        if (type == null || "_all".equals(type)) {
            throw new ElasticsearchIllegalArgumentException("Can't route an operation with no type and having type part of the routing (for backward comp)");
        }
        return hashFunction.hash(type, id);
    }

    private void ensureNodeIdExists(DiscoveryNodes nodes, String nodeId) {
        if (!nodes.dataNodes().keys().contains(nodeId)) {
            throw new ElasticsearchIllegalArgumentException("No data node with id[" + nodeId + "] found");
        }
    }

}
