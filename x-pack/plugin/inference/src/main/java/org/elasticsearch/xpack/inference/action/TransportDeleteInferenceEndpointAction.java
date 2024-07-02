/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.SubscribableListener;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.inference.InferenceServiceRegistry;
import org.elasticsearch.ingest.IngestMetadata;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.inference.action.DeleteInferenceEndpointAction;
import org.elasticsearch.xpack.core.ml.utils.InferenceProcessorInfoExtractor;
import org.elasticsearch.xpack.inference.common.InferenceExceptions;
import org.elasticsearch.xpack.inference.registry.ModelRegistry;

import java.util.Set;

import static org.elasticsearch.xpack.core.ml.utils.SemanticTextInfoExtractor.extractSemanticTextFields;

public class TransportDeleteInferenceEndpointAction extends TransportMasterNodeAction<
    DeleteInferenceEndpointAction.Request,
    DeleteInferenceEndpointAction.Response> {

    private final ModelRegistry modelRegistry;
    private final InferenceServiceRegistry serviceRegistry;
    private static final Logger logger = LogManager.getLogger(TransportDeleteInferenceEndpointAction.class);

    @Inject
    public TransportDeleteInferenceEndpointAction(
        TransportService transportService,
        ClusterService clusterService,
        ThreadPool threadPool,
        ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver,
        ModelRegistry modelRegistry,
        InferenceServiceRegistry serviceRegistry
    ) {
        super(
            DeleteInferenceEndpointAction.NAME,
            transportService,
            clusterService,
            threadPool,
            actionFilters,
            DeleteInferenceEndpointAction.Request::new,
            indexNameExpressionResolver,
            DeleteInferenceEndpointAction.Response::new,
            EsExecutors.DIRECT_EXECUTOR_SERVICE
        );
        this.modelRegistry = modelRegistry;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    protected void masterOperation(
        Task task,
        DeleteInferenceEndpointAction.Request request,
        ClusterState state,
        ActionListener<DeleteInferenceEndpointAction.Response> masterListener
    ) {
        SubscribableListener.<ModelRegistry.UnparsedModel>newForked(modelConfigListener -> {
            // Get the model from the registry

            modelRegistry.getModel(request.getInferenceEndpointId(), modelConfigListener);
        }).<Boolean>andThen((listener, unparsedModel) -> {
            // Validate the request & issue the stop request to the service

            if (request.getTaskType().isAnyOrSame(unparsedModel.taskType()) == false) {
                // specific task type in request does not match the models
                listener.onFailure(InferenceExceptions.mismatchedTaskTypeException(request.getTaskType(), unparsedModel.taskType()));
                return;
            }
            logger.error("Stopping inference endpoint {}", request.getInferenceEndpointId());
            if (request.isDryRun()) {
                logger.error("Stopping inference endpoint {} with dry run 1", request.getInferenceEndpointId());

                Set<String> pipelines = InferenceProcessorInfoExtractor.pipelineIdsForResource(
                    state,
                    Set.of(request.getInferenceEndpointId())
                );
                logger.error("Stopping inference endpoint {} with dry run 2", request.getInferenceEndpointId());

                Set<String> indexesReferencedBySemanticText = extractSemanticTextFields(
                    state.getMetadata(),
                    Set.of(request.getInferenceEndpointId())
                );
                logger.error("Stopping inference endpoint {} with dry run 3", request.getInferenceEndpointId());

                masterListener.onResponse(new DeleteInferenceEndpointAction.Response(false, pipelines, indexesReferencedBySemanticText));
                return;
            } else if (request.isForceDelete() == false
                && endpointIsReferenceInPipelinesOrSemanticText(state, request.getInferenceEndpointId(), listener)) {
                    return;
                }

            logger.error("Stopping inference endpoint {} after check", request.getInferenceEndpointId());

            var service = serviceRegistry.getService(unparsedModel.service());
            if (service.isPresent()) {
                service.get().stop(request.getInferenceEndpointId(), listener);
            } else {
                listener.onFailure(
                    new ElasticsearchStatusException(
                        "No service found for this inference endpoint " + request.getInferenceEndpointId(),
                        RestStatus.NOT_FOUND
                    )
                );
            }
        }).<Boolean>andThen((listener, didStop) -> {
            if (didStop) {
                modelRegistry.deleteModel(request.getInferenceEndpointId(), listener);
            } else {
                listener.onFailure(
                    new ElasticsearchStatusException(
                        "Failed to stop inference endpoint " + request.getInferenceEndpointId(),
                        RestStatus.INTERNAL_SERVER_ERROR
                    )
                );
            }
        })
            .addListener(
                masterListener.delegateFailure(
                    (l3, didDeleteModel) -> masterListener.onResponse(
                        new DeleteInferenceEndpointAction.Response(didDeleteModel, Set.of(), Set.of())
                    )
                )
            );
    }

    private static boolean endpointIsReferenceInPipelinesOrSemanticText(
        final ClusterState state,
        final String inferenceEndpointId,
        ActionListener<Boolean> listener
    ) {
        logger.error("Stopping inference endpoint -- check");
        return endpointIsReferencedInPipelines(state, inferenceEndpointId, listener)
            || endpointIsReferenceInSemanticText(state, inferenceEndpointId, listener);
    }

    private static boolean endpointIsReferenceInSemanticText(
        final ClusterState state,
        final String inferenceEndpointId,
        ActionListener<Boolean> listener
    ) {
        if (extractSemanticTextFields(state.getMetadata(), Set.of(inferenceEndpointId)).isEmpty() == false) {
            listener.onFailure(
                new ElasticsearchStatusException(
                    "Inference endpoint "
                        + inferenceEndpointId
                        + " is referenced by `SemanticText` field(s) and cannot be deleted. "
                        + "Use `force` to delete it anyway, or use `dry_run` to list the `SemanticText` field(s) that reference it.",
                    RestStatus.CONFLICT
                )
            );
            return true;
        }
        return false;
    }

    private static boolean endpointIsReferencedInPipelines(
        final ClusterState state,
        final String inferenceEndpointId,
        ActionListener<Boolean> listener
    ) {
        Metadata metadata = state.getMetadata();
        if (metadata == null) {
            listener.onFailure(
                new ElasticsearchStatusException(
                    " Could not determine if the endpoint is referenced in a pipeline as cluster state metadata was unexpectedly null. "
                        + "Use `force` to delete it anyway",
                    RestStatus.INTERNAL_SERVER_ERROR
                )
            );
            // Unsure why the ClusterState metadata would ever be null, but in this case it seems safer to assume the endpoint is referenced
            return true;
        }
        IngestMetadata ingestMetadata = metadata.custom(IngestMetadata.TYPE);
        if (ingestMetadata == null) {
            logger.debug("No ingest metadata found in cluster state while attempting to delete inference endpoint");
        } else {
            Set<String> modelIdsReferencedByPipelines = InferenceProcessorInfoExtractor.getModelIdsFromInferenceProcessors(ingestMetadata);
            if (modelIdsReferencedByPipelines.contains(inferenceEndpointId)) {
                listener.onFailure(
                    new ElasticsearchStatusException(
                        "Inference endpoint "
                            + inferenceEndpointId
                            + " is referenced by pipelines and cannot be deleted. "
                            + "Use `force` to delete it anyway, or use `dry_run` to list the pipelines that reference it.",
                        RestStatus.CONFLICT
                    )
                );
                return true;
            }
        }
        return false;
    }

    @Override
    protected ClusterBlockException checkBlock(DeleteInferenceEndpointAction.Request request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.WRITE);
    }

}
