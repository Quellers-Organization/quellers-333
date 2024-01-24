/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.TextEmbedding;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.inference.Model;
import org.elasticsearch.inference.ModelConfigurations;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xpack.core.ml.action.CreateTrainedModelAssignmentAction;
import org.elasticsearch.xpack.core.ml.action.StartTrainedModelDeploymentAction;

public abstract class TextEmbeddingModel extends Model {

    public TextEmbeddingModel(String modelId, TaskType taskType, String service, TextEmbeddingServiceSettings serviceSettings) {
        super(new ModelConfigurations(modelId, taskType, service, serviceSettings));
    }

    @Override
    public TextEmbeddingServiceSettings getServiceSettings() {
        return (TextEmbeddingServiceSettings) super.getServiceSettings();
    }

    abstract StartTrainedModelDeploymentAction.Request getStartTrainedModelDeploymentActionRequest();

    abstract ActionListener<CreateTrainedModelAssignmentAction.Response> getCreateTrainedModelAssignmentActionListener(
        Model model,
        ActionListener<Boolean> listener
    );
}
