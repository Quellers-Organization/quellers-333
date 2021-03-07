/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ml.rest.inference;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.RestApiVersion;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.xpack.core.ml.action.DeleteTrainedModelAction;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.rest.RestRequest.Method.DELETE;
import static org.elasticsearch.xpack.core.ml.inference.TrainedModelConfig.MODEL_ID;
import static org.elasticsearch.xpack.ml.MachineLearning.BASE_PATH;

public class RestDeleteTrainedModelAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return List.of(
            Route.builder(DELETE, BASE_PATH + "trained_models/{" + MODEL_ID.getPreferredName() + "}")
                .replaces(DELETE, BASE_PATH + "inference/{" + MODEL_ID.getPreferredName() + "}", RestApiVersion.V_8).build()
        );
    }

    @Override
    public String getName() {
        return "ml_delete_trained_models_action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        String modelId = restRequest.param(MODEL_ID.getPreferredName());
        DeleteTrainedModelAction.Request request = new DeleteTrainedModelAction.Request(modelId);
        return channel -> client.execute(DeleteTrainedModelAction.INSTANCE, request, new RestToXContentListener<>(channel));
    }
}
