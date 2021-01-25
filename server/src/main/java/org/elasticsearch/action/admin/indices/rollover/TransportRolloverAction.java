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

package org.elasticsearch.action.admin.indices.rollover;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.SetOnce;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsAction;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsRequest;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.ActiveShardsObserver;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateUpdateTask;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.index.shard.DocsStats;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Main class to swap the index pointed to by an alias, given some conditions
 */
public class TransportRolloverAction extends TransportMasterNodeAction<RolloverRequest, RolloverResponse> {

    private static final Logger logger = LogManager.getLogger(TransportRolloverAction.class);
    private final MetadataRolloverService rolloverService;
    private final ActiveShardsObserver activeShardsObserver;
    private final Client client;

    @Inject
    public TransportRolloverAction(TransportService transportService, ClusterService clusterService, ThreadPool threadPool,
                                   ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
                                   MetadataRolloverService rolloverService, Client client) {
        super(RolloverAction.NAME, transportService, clusterService, threadPool, actionFilters, RolloverRequest::new,
            indexNameExpressionResolver, RolloverResponse::new, ThreadPool.Names.SAME);
        this.rolloverService = rolloverService;
        this.client = client;
        this.activeShardsObserver = new ActiveShardsObserver(clusterService, threadPool);
    }

    @Override
    protected ClusterBlockException checkBlock(RolloverRequest request, ClusterState state) {
        IndicesOptions indicesOptions = IndicesOptions.fromOptions(true, true,
            request.indicesOptions().expandWildcardsOpen(), request.indicesOptions().expandWildcardsClosed());

        return state.blocks().indicesBlockedException(ClusterBlockLevel.METADATA_WRITE,
            indexNameExpressionResolver.concreteIndexNames(state, indicesOptions, request));
    }

    @Override
    protected void masterOperation(Task task, final RolloverRequest rolloverRequest, final ClusterState preState,
                                   final ActionListener<RolloverResponse> listener) throws Exception {

        Metadata metadata = preState.metadata();

        IndicesStatsRequest statsRequest = new IndicesStatsRequest().indices(rolloverRequest.getRolloverTarget())
            .clear()
            .indicesOptions(IndicesOptions.fromOptions(true, false, true, true))
            .docs(true);
        statsRequest.setParentTask(clusterService.localNode().getId(), task.getId());
        // Rollover can sometimes happen concurrently, to handle these cases, we treat rollover in the same way we would treat a
        // "synchronized" block, in that we have a "before" world, where we calculate naming and condition matching, we then enter our
        // synchronization (in this case, the submitStateUpdateTask which is serialized on the master node), where we then regenerate the
        // names and re-check conditions. More explanation follows inline below.
        client.execute(IndicesStatsAction.INSTANCE, statsRequest,
            ActionListener.wrap(statsResponse -> {
                // Now that we have the stats for the cluster, we need to know the
                // names of the index for which we should evaluate
                // conditions, as well as what our newly created index *would* be.
                final MetadataRolloverService.NameResolution preRolloverNames =
                    rolloverService.resolveRolloverNames(preState, rolloverRequest.getRolloverTarget(),
                        rolloverRequest.getNewIndexName(), rolloverRequest.getCreateIndexRequest());
                final String preSourceIndexName = preRolloverNames.sourceName;
                final String preRolloverIndexName = preRolloverNames.rolloverName;

                rolloverService.validateIndexName(preState, preRolloverIndexName);

                // Evaluate the conditions, so that we can tell without a cluster state update whether a rollover would occur.
                final Map<String, Boolean> preConditionResults = evaluateConditions(rolloverRequest.getConditions().values(),
                    metadata.index(preSourceIndexName), statsResponse);

                // If this is a dry run, return with the results without invoking a cluster state update
                if (rolloverRequest.isDryRun()) {
                    listener.onResponse(new RolloverResponse(preSourceIndexName, preRolloverIndexName,
                        preConditionResults, true, false, false, false));
                    return;
                }

                // Holders for what our final source and rolled over index names are as well as the
                // conditions met to cause the rollover, these are needed so we wait on and report
                // the correct indices and conditions in the clusterStateProcessed method
                final SetOnce<String> sourceIndex = new SetOnce<>();
                final SetOnce<String> rolloverIndex = new SetOnce<>();
                final SetOnce<Map<String, Boolean>> conditionResults = new SetOnce<>();

                final List<Condition<?>> preMetConditions = rolloverRequest.getConditions().values().stream()
                    .filter(condition -> preConditionResults.get(condition.toString())).collect(Collectors.toList());

                // Pre-check the conditions to see whether we should submit a new cluster state task
                if (preConditionResults.size() == 0 || preMetConditions.size() > 0) {

                    // Submit the cluster state, this can be thought of as a "synchronized"
                    // block in that it is single-threaded on the master node
                    clusterService.submitStateUpdateTask("rollover_index source [" + preRolloverIndexName + "] to target ["
                        + preRolloverIndexName + "]", new ClusterStateUpdateTask() {
                        @Override
                        public ClusterState execute(ClusterState currentState) throws Exception {
                            // Regenerate the rollover names, as a rollover could have happened
                            // in between the pre-check and the cluster state update
                            final MetadataRolloverService.NameResolution rolloverNames =
                                rolloverService.resolveRolloverNames(currentState, rolloverRequest.getRolloverTarget(),
                                    rolloverRequest.getNewIndexName(), rolloverRequest.getCreateIndexRequest());
                            final String sourceIndexName = rolloverNames.sourceName;

                            // Re-evaluate the conditions, now with our final source index name
                            final Map<String, Boolean> postConditionResults = evaluateConditions(rolloverRequest.getConditions().values(),
                                metadata.index(sourceIndexName), statsResponse);
                            final List<Condition<?>> metConditions = rolloverRequest.getConditions().values().stream()
                                .filter(condition -> postConditionResults.get(condition.toString())).collect(Collectors.toList());
                            // Update the final condition results so they can be used when returning the response
                            conditionResults.set(postConditionResults);

                            if (postConditionResults.size() == 0 || metConditions.size() > 0) {
                                // Perform the actual rollover
                                MetadataRolloverService.RolloverResult rolloverResult =
                                    rolloverService.rolloverClusterState(currentState, rolloverRequest.getRolloverTarget(),
                                        rolloverRequest.getNewIndexName(),
                                        rolloverRequest.getCreateIndexRequest(), preMetConditions, false, false);
                                logger.trace("rollover result [{}]", rolloverResult);

                                // Update the "final" source and resulting rollover index names.
                                // Note that we use the actual rollover result for these, because
                                // even though we're single threaded, it's possible for the
                                // rollover names generated before the actual rollover to be
                                // different due to things like date resolution
                                sourceIndex.set(rolloverResult.sourceIndexName);
                                rolloverIndex.set(rolloverResult.rolloverIndexName);

                                // Return the new rollover cluster state, which includes the changes that create the new index
                                return rolloverResult.clusterState;
                            } else {
                                // Upon re-evaluation of the conditions, none were met, so
                                // therefore do not perform a rollover, returning the current
                                // cluster state.
                                return currentState;
                            }
                        }

                        @Override
                        public void onFailure(String source, Exception e) {
                            listener.onFailure(e);
                        }

                        @Override
                        public void clusterStateProcessed(String source, ClusterState oldState, ClusterState newState) {
                            // Now assuming we have a new state and the name of the rolled over index, we need to wait for the
                            // configured number of active shards, as well as return the names of the indices that were rolled/created
                            if (newState.equals(oldState) == false) {
                                assert sourceIndex.get() != null : "source index missing on successful rollover";
                                assert rolloverIndex.get() != null : "rollover index missing on successful rollover";
                                assert conditionResults.get() != null : "matching rollover conditions missing on successful rollover";

                                activeShardsObserver.waitForActiveShards(new String[]{rolloverIndex.get()},
                                    rolloverRequest.getCreateIndexRequest().waitForActiveShards(),
                                    rolloverRequest.masterNodeTimeout(),
                                    isShardsAcknowledged -> listener.onResponse(new RolloverResponse(
                                        sourceIndex.get(), rolloverIndex.get(), conditionResults.get(), false, true, true,
                                        isShardsAcknowledged)),
                                    listener::onFailure);
                            } else {
                                // We did not roll over due to conditions not being met inside the cluster state update
                                listener.onResponse(new RolloverResponse(
                                    preSourceIndexName, preRolloverIndexName, preConditionResults, false, false, false, false));
                            }
                        }
                    });
                } else {
                    // conditions not met
                    listener.onResponse(new RolloverResponse(preSourceIndexName, preRolloverIndexName,
                        preConditionResults, false, false, false, false));
                }
            }, listener::onFailure));
    }

    static Map<String, Boolean> evaluateConditions(final Collection<Condition<?>> conditions,
                                                   @Nullable final DocsStats docsStats,
                                                   @Nullable final IndexMetadata metadata) {
        if (metadata == null) {
            return conditions.stream().collect(Collectors.toMap(Condition::toString, cond -> false));
        }
        final long numDocs = docsStats == null ? 0 : docsStats.getCount();
        final long indexSize = docsStats == null ? 0 : docsStats.getTotalSizeInBytes();
        final Condition.Stats stats = new Condition.Stats(numDocs, metadata.getCreationDate(), new ByteSizeValue(indexSize));
        return conditions.stream()
            .map(condition -> condition.evaluate(stats))
            .collect(Collectors.toMap(result -> result.condition.toString(), result -> result.matched));
    }

    static Map<String, Boolean> evaluateConditions(final Collection<Condition<?>> conditions,
                                                   @Nullable final IndexMetadata metadata,
                                                   @Nullable final IndicesStatsResponse statsResponse) {
        if (metadata == null) {
            return conditions.stream().collect(Collectors.toMap(Condition::toString, cond -> false));
        } else {
            final DocsStats docsStats = Optional.ofNullable(statsResponse)
                .map(stats -> stats.getIndex(metadata.getIndex().getName()))
                .map(indexStats -> indexStats.getPrimaries().getDocs())
                .orElse(null);
            return evaluateConditions(conditions, docsStats, metadata);
        }
    }
}
