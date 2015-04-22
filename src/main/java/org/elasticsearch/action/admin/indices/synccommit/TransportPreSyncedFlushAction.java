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



package org.elasticsearch.action.admin.indices.synccommit;
//TODO: renam epackage to synced flush and put all the other stuff there, don't know where is should finally go

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ShardOperationFailedException;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.DefaultShardOperationFailedException;
import org.elasticsearch.action.support.broadcast.BroadcastShardOperationFailedException;
import org.elasticsearch.action.support.broadcast.TransportBroadcastOperationAction;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.routing.GroupShardsIterator;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.google.common.collect.Lists.newArrayList;


/**
 * Sync Commit Action.
 */
public class TransportPreSyncedFlushAction extends TransportBroadcastOperationAction<PreSyncedFlushRequest, PreSyncedFlushResponse, PreSyncedShardFlushRequest, PreSyncedShardFlushResponse> {

    private final IndicesService indicesService;

    public static final String NAME = "indices:admin/presyncedflush";

    @Inject
    public TransportPreSyncedFlushAction(Settings settings, ThreadPool threadPool, ClusterService clusterService, TransportService transportService, IndicesService indicesService, ActionFilters actionFilters) {
        super(settings, NAME, threadPool, clusterService, transportService, actionFilters);
        this.indicesService = indicesService;
    }

    @Override
    protected String executor() {
        return ThreadPool.Names.FLUSH;
    }

    @Override
    protected PreSyncedFlushRequest newRequestInstance() {
        return new PreSyncedFlushRequest();
    }

    @Override
    protected PreSyncedFlushResponse newResponse(PreSyncedFlushRequest request, AtomicReferenceArray shardsResponses, ClusterState clusterState) {
        int successfulShards = 0;
        int failedShards = 0;
        List<ShardOperationFailedException> shardFailures = null;
        for (int i = 0; i < shardsResponses.length(); i++) {
            Object shardResponse = shardsResponses.get(i);
            if (shardResponse == null) {
                // a non active shard, ignore
            } else if (shardResponse instanceof BroadcastShardOperationFailedException) {
                failedShards++;
                if (shardFailures == null) {
                    shardFailures = newArrayList();
                }
                shardFailures.add(new DefaultShardOperationFailedException((BroadcastShardOperationFailedException) shardResponse));
            } else {
                successfulShards++;
            }
        }
        return new PreSyncedFlushResponse(shardsResponses.length(), successfulShards, failedShards, shardFailures, shardsResponses);
    }

    @Override
    protected PreSyncedShardFlushRequest newShardRequest() {
        return new PreSyncedShardFlushRequest();
    }

    @Override
    protected PreSyncedShardFlushRequest newShardRequest(int numShards, ShardRouting shard, PreSyncedFlushRequest request) {
        return new PreSyncedShardFlushRequest(shard, request);
    }

    @Override
    protected PreSyncedShardFlushResponse newShardResponse() {
        return new PreSyncedShardFlushResponse();
    }

    @Override
    protected PreSyncedShardFlushResponse shardOperation(PreSyncedShardFlushRequest request) throws ElasticsearchException {
        IndexShard indexShard = indicesService.indexServiceSafe(request.shardId().getIndex()).shardSafe(request.shardId().id());
        FlushRequest flushRequest = new FlushRequest().force(false).waitIfOngoing(true);
        byte[] id = indexShard.flush(flushRequest);
        return new PreSyncedShardFlushResponse(id, request.shardRouting());
    }

    /**
     * The sync commit request works against one primary and all of its copies.
     */
    @Override
    protected GroupShardsIterator shards(ClusterState clusterState, PreSyncedFlushRequest request, String[] concreteIndices) {
        return clusterState.routingTable().allShardCopiesGrouped(request.shardId());
    }

    @Override
    protected ClusterBlockException checkGlobalBlock(ClusterState state, PreSyncedFlushRequest request) {
        return null;
    }

    @Override
    protected ClusterBlockException checkRequestBlock(ClusterState state, PreSyncedFlushRequest countRequest, String[] concreteIndices) {
        return null;
    }
}
