/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.enrich.action;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.support.master.AcknowledgedTransportMasterNodeAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.OriginSettingClient;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.ingest.IngestService;
import org.elasticsearch.ingest.PipelineConfiguration;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.enrich.EnrichPolicy;
import org.elasticsearch.xpack.core.enrich.action.DeleteEnrichPolicyAction;
import org.elasticsearch.xpack.enrich.AbstractEnrichProcessor;
import org.elasticsearch.xpack.enrich.EnrichPolicyLocks;
import org.elasticsearch.xpack.enrich.EnrichStore;

import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.xpack.core.ClientHelper.ENRICH_ORIGIN;

public class TransportDeleteEnrichPolicyAction extends AcknowledgedTransportMasterNodeAction<DeleteEnrichPolicyAction.Request> {

    private final EnrichPolicyLocks enrichPolicyLocks;
    private final IngestService ingestService;
    private final Client client;
    // the most lenient we can get in order to not bomb out if no indices are found, which is a valid case
    // where a user creates and deletes a policy before running execute
    private static final IndicesOptions LENIENT_OPTIONS = IndicesOptions.fromOptions(true, true, true, true);

    @Inject
    public TransportDeleteEnrichPolicyAction(
        TransportService transportService,
        ClusterService clusterService,
        ThreadPool threadPool,
        ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver,
        Client client,
        EnrichPolicyLocks enrichPolicyLocks,
        IngestService ingestService
    ) {
        super(
            DeleteEnrichPolicyAction.NAME,
            transportService,
            clusterService,
            threadPool,
            actionFilters,
            DeleteEnrichPolicyAction.Request::new,
            indexNameExpressionResolver,
            ThreadPool.Names.SAME
        );
        this.client = client;
        this.enrichPolicyLocks = enrichPolicyLocks;
        this.ingestService = ingestService;
    }

    @Override
    protected void masterOperation(
        Task task,
        DeleteEnrichPolicyAction.Request request,
        ClusterState state,
        ActionListener<AcknowledgedResponse> listener
    ) throws Exception {
        EnrichPolicy policy = EnrichStore.getPolicy(request.getName(), state); // ensure the policy exists first
        if (policy == null) {
            throw new ResourceNotFoundException("policy [{}] not found", request.getName());
        }

        enrichPolicyLocks.lockPolicy(request.getName());
        try {
            List<PipelineConfiguration> pipelines = IngestService.getPipelines(state);
            List<String> pipelinesWithProcessors = new ArrayList<>();

            for (PipelineConfiguration pipelineConfiguration : pipelines) {
                List<AbstractEnrichProcessor> enrichProcessors = ingestService.getProcessorsInPipeline(
                    pipelineConfiguration.getId(),
                    AbstractEnrichProcessor.class
                );
                for (AbstractEnrichProcessor processor : enrichProcessors) {
                    if (processor.getPolicyName().equals(request.getName())) {
                        pipelinesWithProcessors.add(pipelineConfiguration.getId());
                    }
                }
            }

            if (pipelinesWithProcessors.isEmpty() == false) {
                throw new ElasticsearchStatusException(
                    "Could not delete policy [{}] because a pipeline is referencing it {}",
                    RestStatus.CONFLICT,
                    request.getName(),
                    pipelinesWithProcessors
                );
            }
        } catch (Exception e) {
            enrichPolicyLocks.releasePolicy(request.getName());
            listener.onFailure(e);
            return;
        }

        GetIndexRequest indices = new GetIndexRequest().indices(EnrichPolicy.getBaseName(request.getName()) + "-*")
            .indicesOptions(IndicesOptions.lenientExpand());

        String[] concreteIndices = indexNameExpressionResolver.concreteIndexNamesWithSystemIndexAccess(state, indices);

        deleteIndicesAndPolicy(concreteIndices, request.getName(), ActionListener.wrap((response) -> {
            enrichPolicyLocks.releasePolicy(request.getName());
            listener.onResponse(response);
        }, (exc) -> {
            enrichPolicyLocks.releasePolicy(request.getName());
            listener.onFailure(exc);
        }));
    }

    private void deleteIndicesAndPolicy(String[] indices, String name, ActionListener<AcknowledgedResponse> listener) {
        if (indices.length == 0) {
            deletePolicy(name, listener);
            return;
        }

        // delete all enrich indices for this policy, we delete concrete indices here but not a wildcard index expression
        // as the setting 'action.destructive_requires_name' may be set to true
        DeleteIndexRequest deleteRequest = new DeleteIndexRequest().indices(indices).indicesOptions(LENIENT_OPTIONS);

        new OriginSettingClient(client, ENRICH_ORIGIN).admin().indices().delete(deleteRequest, ActionListener.wrap((response) -> {
            if (response.isAcknowledged() == false) {
                listener.onFailure(
                    new ElasticsearchStatusException(
                        "Could not fetch indices to delete during policy delete of [{}]",
                        RestStatus.INTERNAL_SERVER_ERROR,
                        name
                    )
                );
            } else {
                deletePolicy(name, listener);
            }
        }, (error) -> listener.onFailure(error)));
    }

    private void deletePolicy(String name, ActionListener<AcknowledgedResponse> listener) {
        EnrichStore.deletePolicy(name, clusterService, e -> {
            if (e == null) {
                listener.onResponse(AcknowledgedResponse.TRUE);
            } else {
                listener.onFailure(e);
            }
        });
    }

    @Override
    protected ClusterBlockException checkBlock(DeleteEnrichPolicyAction.Request request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }
}
