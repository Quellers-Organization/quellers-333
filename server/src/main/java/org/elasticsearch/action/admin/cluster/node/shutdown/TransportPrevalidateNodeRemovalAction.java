/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.cluster.node.shutdown;

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.TransportMasterNodeReadAction;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.health.ClusterStateHealth;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.core.Strings;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.logging.LogManager;
import org.elasticsearch.logging.Logger;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.elasticsearch.action.admin.cluster.node.shutdown.NodesRemovalPrevalidation.NodeResult;
import static org.elasticsearch.action.admin.cluster.node.shutdown.NodesRemovalPrevalidation.Result;

public class TransportPrevalidateNodeRemovalAction extends TransportMasterNodeReadAction<
    PrevalidateNodeRemovalRequest,
    PrevalidateNodeRemovalResponse> {

    private static final Logger logger = LogManager.getLogger(TransportPrevalidateNodeRemovalAction.class);

    private final NodeClient client;

    @Inject
    public TransportPrevalidateNodeRemovalAction(
        TransportService transportService,
        ClusterService clusterService,
        ThreadPool threadPool,
        ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver,
        NodeClient client
    ) {
        super(
            PrevalidateNodeRemovalAction.NAME,
            false,
            transportService,
            clusterService,
            threadPool,
            actionFilters,
            PrevalidateNodeRemovalRequest::new,
            indexNameExpressionResolver,
            PrevalidateNodeRemovalResponse::new,
            ThreadPool.Names.SAME
        );
        this.client = client;
    }

    @Override
    protected void masterOperation(
        Task task,
        PrevalidateNodeRemovalRequest request,
        ClusterState state,
        ActionListener<PrevalidateNodeRemovalResponse> listener
    ) {
        try {
            Set<DiscoveryNode> requestNodes = resolveNodes(request, state.nodes());
            doPrevalidation(request, requestNodes, state, listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    public static Set<DiscoveryNode> resolveNodes(PrevalidateNodeRemovalRequest request, DiscoveryNodes discoveryNodes) {
        // Only one of the three arrays must be non-empty.
        assert Stream.of(request.getNames(), request.getIds(), request.getExternalIds())
            .filter(TransportPrevalidateNodeRemovalAction::notEmpty)
            .toList()
            .size() == 1;
        // Resolve by name
        if (notEmpty(request.getNames())) {
            logger.debug("resolving nodes for prevalidation using name");
            var names = new HashSet<>(Arrays.asList(request.getNames()));
            var resolvedNodes = discoveryNodes.stream().filter(n -> names.contains(n.getName())).collect(Collectors.toSet());
            if (resolvedNodes.size() < names.size()) {
                // find out which one wasn't found
                var existingNodeNames = discoveryNodes.stream().map(DiscoveryNode::getName).collect(Collectors.toSet());
                names.removeAll(existingNodeNames);
                throw new ResourceNotFoundException("could not resolve node names {}", names);
            }
            assert resolvedNodes.size() == request.getNames().length;
            return resolvedNodes;
        }
        // Resolve by ID
        if (notEmpty(request.getIds())) {
            logger.debug("resolving nodes for prevalidation using ID");
            var ids = request.getIds();
            var resolvedNode = Arrays.stream(ids).map(discoveryNodes::get).filter(Objects::nonNull).collect(Collectors.toSet());
            if (resolvedNode.size() < ids.length) {
                // find out which one wasn't found
                var existingNodeIds = discoveryNodes.stream().map(DiscoveryNode::getId).collect(Collectors.toSet());
                var idsNotFound = Arrays.stream(ids).filter(id -> existingNodeIds.contains(id) == false).collect(Collectors.toSet());
                throw new ResourceNotFoundException("could not resolve node IDs {}", idsNotFound);
            }
            return resolvedNode;
        }
        // Resolve by external ID
        logger.debug("resolving nodes for prevalidation using external ID");
        var externalIds = new HashSet<>(Arrays.asList(request.getExternalIds()));
        var resolvedNodes = discoveryNodes.stream().filter(n -> externalIds.contains(n.getExternalId())).collect(Collectors.toSet());
        if (resolvedNodes.size() < externalIds.size()) {
            // find out which one wasn't found
            var existingExternalIds = discoveryNodes.stream().map(DiscoveryNode::getExternalId).collect(Collectors.toSet());
            externalIds.removeAll(existingExternalIds);
            throw new ResourceNotFoundException("could not resolve node external IDs {}", externalIds);
        }
        assert resolvedNodes.size() == request.getExternalIds().length;
        return resolvedNodes;
    }

    private static boolean notEmpty(String[] a) {
        return a != null && a.length > 0;
    }

    @Override
    protected ClusterBlockException checkBlock(PrevalidateNodeRemovalRequest request, ClusterState state) {
        // Allow running this action even when there are blocks on the cluster
        return null;
    }

    private void doPrevalidation(
        PrevalidateNodeRemovalRequest request,
        Set<DiscoveryNode> requestNodes,
        ClusterState clusterState,
        ActionListener<PrevalidateNodeRemovalResponse> listener
    ) {
        assert requestNodes != null && requestNodes.isEmpty() == false;

        logger.debug(() -> "prevalidate node removal for nodes " + requestNodes);
        ClusterStateHealth clusterStateHealth = new ClusterStateHealth(clusterState);
        Metadata metadata = clusterState.metadata();
        DiscoveryNodes clusterNodes = clusterState.getNodes();
        switch (clusterStateHealth.getStatus()) {
            case GREEN, YELLOW -> {
                List<NodeResult> nodesResults = requestNodes.stream()
                    .map(dn -> new NodeResult(dn.getName(), dn.getId(), dn.getExternalId(), new Result(true, "")))
                    .toList();
                listener.onResponse(
                    new PrevalidateNodeRemovalResponse(new NodesRemovalPrevalidation(true, "cluster status is not RED", nodesResults))
                );
            }
            case RED -> {
                Set<String> redIndices = clusterStateHealth.getIndices()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().getStatus() == ClusterHealthStatus.RED)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
                // If all red indices are searchable snapshot indices, it is safe to remove any node.
                Set<String> redNonSSIndices = redIndices.stream()
                    .map(metadata::index)
                    .filter(i -> i.isSearchableSnapshot() == false)
                    .map(im -> im.getIndex().getName())
                    .collect(Collectors.toSet());
                if (redNonSSIndices.isEmpty()) {
                    List<NodeResult> nodeResults = requestNodes.stream()
                        .map(dn -> new NodeResult(dn.getName(), dn.getId(), dn.getExternalId(), new Result(true, "")))
                        .toList();
                    listener.onResponse(
                        new PrevalidateNodeRemovalResponse(
                            new NodesRemovalPrevalidation(true, "all red indices are searchable snapshot indices", nodeResults)
                        )
                    );
                } else {
                    // Reach out to the nodes to find out whether they contain copies of the red non-searchable-snapshot indices
                    Set<ShardId> redShards = clusterStateHealth.getIndices()
                        .entrySet()
                        .stream()
                        .filter(indexHealthEntry -> redNonSSIndices.contains(indexHealthEntry.getKey()))
                        .map(Map.Entry::getValue) // ClusterHealthIndex of red non-searchable-snapshot indices
                        .flatMap(
                            redIndexHealth -> redIndexHealth.getShards()
                                .values()
                                .stream()
                                .filter(shardHealth -> shardHealth.getStatus() == ClusterHealthStatus.RED)
                                .map(redShardHealth -> Tuple.tuple(redIndexHealth.getIndex(), redShardHealth))
                        ) // (Index, ClusterShardHealth) of all red shards
                        .map(
                            redIndexShardHealthTuple -> new ShardId(
                                metadata.index(redIndexShardHealthTuple.v1()).getIndex(),
                                redIndexShardHealthTuple.v2().getShardId()
                            )
                        ) // Convert to ShardId
                        .collect(Collectors.toSet());
                    var nodeIds = requestNodes.stream().map(DiscoveryNode::getId).toList().toArray(new String[0]);
                    var checkShardsRequest = new CheckShardsOnDataPathRequest(redShards, nodeIds).timeout(request.timeout());
                    client.execute(TransportCheckShardsOnDataPathAction.TYPE, checkShardsRequest, new ActionListener<>() {
                        @Override
                        public void onResponse(CheckShardsOnDataPathResponse response) {
                            listener.onResponse(new PrevalidateNodeRemovalResponse(createPrevalidationResult(clusterNodes, response)));
                        }

                        @Override
                        public void onFailure(Exception e) {
                            listener.onFailure(e);
                        }
                    });
                }
            }
        }
    }

    private NodesRemovalPrevalidation createPrevalidationResult(DiscoveryNodes nodes, CheckShardsOnDataPathResponse response) {
        List<NodeResult> nodeResults = new ArrayList<>(response.getNodes().size() + response.failures().size());
        for (NodeCheckShardsOnDataPathResponse nodeResponse : response.getNodes()) {
            Result result;
            if (nodeResponse.getShardIds().isEmpty()) {
                result = new Result(true, "");
            } else {
                result = new Result(
                    false,
                    Strings.format("node contains copies of the following red shards: %s", nodeResponse.getShardIds())
                );
            }
            nodeResults.add(
                new NodeResult(
                    nodeResponse.getNode().getName(),
                    nodeResponse.getNode().getId(),
                    nodeResponse.getNode().getExternalId(),
                    result
                )
            );
        }
        for (FailedNodeException failedResponse : response.failures()) {
            DiscoveryNode node = nodes.get(failedResponse.nodeId());
            nodeResults.add(
                new NodeResult(
                    node.getName(),
                    node.getId(),
                    node.getExternalId(),
                    new Result(false, Strings.format("failed contacting the node: %s", failedResponse.getDetailedMessage()))
                )
            );
        }
        // determine overall result from the node results.
        Result prevalidationResult;
        Set<String> unsafeNodeRemovals = response.getNodes()
            .stream()
            .filter(r -> r.getShardIds().isEmpty() == false)
            .map(r -> r.getNode().getId())
            .collect(Collectors.toSet());
        if (unsafeNodeRemovals.isEmpty() == false) {
            prevalidationResult = new Result(
                false,
                Strings.format("nodes with the following IDs contain copies of red shards: %s", unsafeNodeRemovals)
            );
        } else if (response.failures().isEmpty() == false) {
            Set<String> unknownNodeRemovals = response.failures().stream().map(FailedNodeException::nodeId).collect(Collectors.toSet());
            prevalidationResult = new Result(
                false,
                Strings.format("cannot prevalidate removal of nodes with the following IDs: %s", unknownNodeRemovals)
            );
        } else {
            prevalidationResult = new Result(true, "");
        }
        return new NodesRemovalPrevalidation(prevalidationResult.isSafe(), prevalidationResult.message(), nodeResults);
    }
}
