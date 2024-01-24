/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 *
 * this file was contributed to by a generative AI
 */

package org.elasticsearch.xpack.inference.services.TextEmbedding;

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.inference.Model;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xpack.core.ml.action.CreateTrainedModelAssignmentAction;
import org.elasticsearch.xpack.core.ml.action.StartTrainedModelDeploymentAction;
import org.elasticsearch.xpack.core.ml.utils.ExceptionsHelper;

import static org.elasticsearch.xpack.core.ml.inference.assignment.AllocationStatus.State.STARTED;

public class MultilingualE5SmallModel extends TextEmbeddingModel {

    public MultilingualE5SmallModel(String modelId, TaskType taskType, String service, MultilingualE5SmallServiceSettings serviceSettings) {
        super(modelId, taskType, service, serviceSettings);
    }

    @Override
    public MultilingualE5SmallServiceSettings getServiceSettings() {
        return (MultilingualE5SmallServiceSettings) super.getServiceSettings();
    }

    @Override
    StartTrainedModelDeploymentAction.Request getStartTrainedModelDeploymentActionRequest() {
        var startRequest = new StartTrainedModelDeploymentAction.Request(this.getModelId(), this.getModelId());
        startRequest.setNumberOfAllocations(this.getServiceSettings().getNumAllocations());
        startRequest.setThreadsPerAllocation(this.getServiceSettings().getNumThreads());
        startRequest.setWaitForState(STARTED);

        return startRequest;
    }

    @Override
    ActionListener<CreateTrainedModelAssignmentAction.Response> getCreateTrainedModelAssignmentActionListener(
        Model model,
        ActionListener<Boolean> listener
    ) {

        return new ActionListener<>() {
            @Override
            public void onResponse(CreateTrainedModelAssignmentAction.Response response) {
                listener.onResponse(Boolean.TRUE);
            }

            @Override
            public void onFailure(Exception e) {
                if (ExceptionsHelper.unwrapCause(e) instanceof ResourceNotFoundException) {
                    listener.onFailure(
                        new ResourceNotFoundException(
                            "Could not start the TextEmbeddingService service as the "
                                + "Multilingual-E5-Small model for this platform cannot be found."
                                + " Multilingual-E5-Small needs to be downloaded before it can be started"
                        )
                    );
                    return;
                }
                listener.onFailure(e);
            }
        };
    }

}
