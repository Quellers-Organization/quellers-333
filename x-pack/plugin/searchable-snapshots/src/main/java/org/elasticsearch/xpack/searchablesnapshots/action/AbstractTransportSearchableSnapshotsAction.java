/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.searchablesnapshots.action;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.broadcast.BroadcastRequest;
import org.elasticsearch.action.support.broadcast.BroadcastResponse;
import org.elasticsearch.action.support.broadcast.node.TransportBroadcastByNodeAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.ShardsIterator;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.store.InMemoryNoOpCommitDirectory;
import org.elasticsearch.index.store.SearchableSnapshotDirectory;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.index.IndexModule.INDEX_STORE_TYPE_SETTING;
import static org.elasticsearch.xpack.searchablesnapshots.SearchableSnapshots.SNAPSHOT_DIRECTORY_FACTORY_KEY;

public abstract class AbstractTransportSearchableSnapshotsAction
    <Request extends BroadcastRequest<Request>, Response extends BroadcastResponse, ShardOperationResult extends Writeable>
    extends TransportBroadcastByNodeAction<Request, Response, ShardOperationResult> {

    private final IndicesService indicesService;

    AbstractTransportSearchableSnapshotsAction(String actionName, ClusterService clusterService, TransportService transportService,
                                               ActionFilters actionFilters, IndexNameExpressionResolver resolver,
                                               Writeable.Reader<Request> request, String executor, IndicesService indicesService) {
        super(actionName, clusterService, transportService, actionFilters, resolver, request, executor);
        this.indicesService = indicesService;
    }

    AbstractTransportSearchableSnapshotsAction(String actionName, ClusterService clusterService, TransportService transportService,
                                               ActionFilters actionFilters, IndexNameExpressionResolver resolver,
                                               Writeable.Reader<Request> request, String executor, IndicesService indicesService,
                                               boolean canTripCircuitBreaker) {
        super(actionName, clusterService, transportService, actionFilters, resolver, request, executor, canTripCircuitBreaker);
        this.indicesService = indicesService;
    }

    @Override
    protected ClusterBlockException checkGlobalBlock(ClusterState state, Request request) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_READ);
    }

    @Override
    protected ClusterBlockException checkRequestBlock(ClusterState state, Request request, String[] indices) {
        return state.blocks().indicesBlockedException(ClusterBlockLevel.METADATA_READ, indices);
    }

    @Override
    protected ShardsIterator shards(ClusterState state, Request request, String[] concreteIndices) {
        final List<String> searchableSnapshotIndices = new ArrayList<>();
        for (String concreteIndex : concreteIndices) {
            IndexMetadata indexMetaData = state.metadata().index(concreteIndex);
            if (indexMetaData != null) {
                Settings indexSettings = indexMetaData.getSettings();
                if (INDEX_STORE_TYPE_SETTING.get(indexSettings).equals(SNAPSHOT_DIRECTORY_FACTORY_KEY)) {
                    searchableSnapshotIndices.add(concreteIndex);
                }
            }
        }
        if (searchableSnapshotIndices.isEmpty()) {
            throw new ResourceNotFoundException("No searchable snapshots indices found");
        }
        return state.routingTable().allShards(searchableSnapshotIndices.toArray(new String[0]));
    }

    @Override
    protected ShardOperationResult shardOperation(Request request, ShardRouting shardRouting) throws IOException {
        final IndexShard indexShard = indicesService.indexServiceSafe(shardRouting.index()).getShard(shardRouting.id());
        final SearchableSnapshotDirectory directory = unwrapDirectory(indexShard.store().directory());
        assert directory != null;
        assert directory.getShardId().equals(shardRouting.shardId());
        return executeShardOperation(request, shardRouting, directory);
    }

    protected abstract ShardOperationResult executeShardOperation(Request request,
                                                                  ShardRouting shardRouting,
                                                                  SearchableSnapshotDirectory directory) throws IOException;

    @Nullable
    private static SearchableSnapshotDirectory unwrapDirectory(Directory dir) {
        while (dir != null) {
            if (dir instanceof SearchableSnapshotDirectory) {
                return (SearchableSnapshotDirectory) dir;
            } else if (dir instanceof InMemoryNoOpCommitDirectory) {
                dir = ((InMemoryNoOpCommitDirectory) dir).getRealDirectory();
            } else if (dir instanceof FilterDirectory) {
                dir = ((FilterDirectory) dir).getDelegate();
            } else {
                dir = null;
            }
        }
        return null;
    }
}
