/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.dataframe.action;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.license.LicenseUtils;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.persistent.PersistentTasksCustomMetaData;
import org.elasticsearch.persistent.PersistentTasksService;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.ClientHelper;
import org.elasticsearch.xpack.core.XPackField;
import org.elasticsearch.xpack.core.dataframe.DataFrameMessages;
import org.elasticsearch.xpack.core.dataframe.action.StartDataFrameTransformAction;
import org.elasticsearch.xpack.core.dataframe.action.StartDataFrameTransformTaskAction;
import org.elasticsearch.xpack.core.dataframe.transforms.DataFrameTransform;
import org.elasticsearch.xpack.core.dataframe.transforms.DataFrameTransformConfig;
import org.elasticsearch.xpack.core.dataframe.transforms.DataFrameTransformState;
import org.elasticsearch.xpack.core.dataframe.transforms.DataFrameTransformTaskState;
import org.elasticsearch.xpack.dataframe.persistence.DataFrameTransformsConfigManager;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TransportStartDataFrameTransformAction extends
    TransportMasterNodeAction<StartDataFrameTransformAction.Request, StartDataFrameTransformAction.Response> {

    private static final Logger logger = LogManager.getLogger(TransportStartDataFrameTransformAction.class);
    private final XPackLicenseState licenseState;
    private final DataFrameTransformsConfigManager dataFrameTransformsConfigManager;
    private final PersistentTasksService persistentTasksService;
    private final Client client;

    @Inject
    public TransportStartDataFrameTransformAction(TransportService transportService, ActionFilters actionFilters,
                                                  ClusterService clusterService, XPackLicenseState licenseState,
                                                  ThreadPool threadPool, IndexNameExpressionResolver indexNameExpressionResolver,
                                                  DataFrameTransformsConfigManager dataFrameTransformsConfigManager,
                                                  PersistentTasksService persistentTasksService, Client client) {
        super(StartDataFrameTransformAction.NAME, transportService, clusterService, threadPool, actionFilters,
                StartDataFrameTransformAction.Request::new, indexNameExpressionResolver);
        this.licenseState = licenseState;
        this.dataFrameTransformsConfigManager = dataFrameTransformsConfigManager;
        this.persistentTasksService = persistentTasksService;
        this.client = client;
    }

    @Override
    protected String executor() {
        return ThreadPool.Names.SAME;
    }

    @Override
    protected StartDataFrameTransformAction.Response newResponse() {
        return new StartDataFrameTransformAction.Response();
    }

    @Override
    protected void masterOperation(StartDataFrameTransformAction.Request request,
                                   ClusterState state,
                                   ActionListener<StartDataFrameTransformAction.Response> listener) throws Exception {
        if (!licenseState.isDataFrameAllowed()) {
            listener.onFailure(LicenseUtils.newComplianceException(XPackField.DATA_FRAME));
            return;
        }
        final DataFrameTransform transformTask = createDataFrameTransform(request.getId(), threadPool);

        // <3> Wait for the allocated task's state to STARTED
        ActionListener<PersistentTasksCustomMetaData.PersistentTask<DataFrameTransform>> newPersistentTaskActionListener =
            ActionListener.wrap(
                task -> {
                    waitForDataFrameTaskStarted(task.getId(),
                        transformTask,
                        request.timeout(),
                        ActionListener.wrap(
                            taskStarted -> listener.onResponse(new StartDataFrameTransformAction.Response(true)),
                            listener::onFailure));
            },
            listener::onFailure
        );

        // <2> Find or Create the task in cluster state so that it will start executing on the node
        ActionListener<DataFrameTransformConfig> getTransformListener = ActionListener.wrap(
            config -> {
                if (config.isValid() == false) {
                    listener.onFailure(new ElasticsearchStatusException(
                        DataFrameMessages.getMessage(DataFrameMessages.DATA_FRAME_CONFIG_INVALID, request.getId()),
                        RestStatus.BAD_REQUEST
                    ));
                    return;
                }
                PersistentTasksCustomMetaData.PersistentTask<DataFrameTransform> existingTask =
                    getExistingTask(transformTask.getId(), state);
                if (existingTask == null) {
                    // Create the allocated task and wait for it to be started
                    persistentTasksService.sendStartRequest(transformTask.getId(),
                        DataFrameTransform.NAME,
                        transformTask,
                        newPersistentTaskActionListener);
                } else {
                    DataFrameTransformState transformState = (DataFrameTransformState)existingTask.getState();
                    if(transformState.getTaskState() == DataFrameTransformTaskState.FAILED && request.isForce() == false) {
                        listener.onFailure(new ElasticsearchStatusException(
                            "Unable to start data frame transform [" + config.getId() +
                                "] as it is in a failed state with failure: [" + transformState.getReason() +
                            "]. Use force start to restart data frame transform once error is resolved.",
                            RestStatus.CONFLICT));
                    } else if (transformState.getTaskState() != DataFrameTransformTaskState.STOPPED &&
                               transformState.getTaskState() != DataFrameTransformTaskState.FAILED) {
                        listener.onFailure(new ElasticsearchStatusException(
                            "Unable to start data frame transform [" + config.getId() +
                                "] as it is in state [" + transformState.getTaskState()  + "]", RestStatus.CONFLICT));
                    } else {
                        // If the task already exists but is not assigned to a node, something is weird
                        // return a failure that includes the current assignment explanation (if one exists)
                        if (existingTask.isAssigned() == false) {
                            String assignmentExplanation = "unknown reason";
                            if (existingTask.getAssignment() != null) {
                                assignmentExplanation = existingTask.getAssignment().getExplanation();
                            }
                            listener.onFailure(new ElasticsearchStatusException("Unable to start data frame transform [" +
                                config.getId() + "] as it is not assigned to a node, explanation: " + assignmentExplanation,
                                RestStatus.CONFLICT));
                            return;
                        }
                        // If the task already exists and is assigned to a node, simply attempt to set it to start
                        ClientHelper.executeAsyncWithOrigin(client,
                            ClientHelper.DATA_FRAME_ORIGIN,
                            StartDataFrameTransformTaskAction.INSTANCE,
                            new StartDataFrameTransformTaskAction.Request(request.getId()),
                            ActionListener.wrap(
                                r -> listener.onResponse(new StartDataFrameTransformAction.Response(true)),
                                listener::onFailure));
                    }
                }
            },
            listener::onFailure
        );

        // <1> Get the config to verify it exists and is valid
        dataFrameTransformsConfigManager.getTransformConfiguration(request.getId(), getTransformListener);
    }

    @Override
    protected ClusterBlockException checkBlock(StartDataFrameTransformAction.Request request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    private static DataFrameTransform createDataFrameTransform(String transformId, ThreadPool threadPool) {
        return new DataFrameTransform(transformId);
    }

    @SuppressWarnings("unchecked")
    private static PersistentTasksCustomMetaData.PersistentTask<DataFrameTransform> getExistingTask(String id, ClusterState state) {
        PersistentTasksCustomMetaData pTasksMeta = state.getMetaData().custom(PersistentTasksCustomMetaData.TYPE);
        if (pTasksMeta == null) {
            return null;
        }
        Collection<PersistentTasksCustomMetaData.PersistentTask<?>> existingTask = pTasksMeta.findTasks(DataFrameTransform.NAME,
            t -> t.getId().equals(id));
        if (existingTask.isEmpty()) {
            return null;
        } else {
            assert(existingTask.size() == 1);
            PersistentTasksCustomMetaData.PersistentTask<?> pTask = existingTask.iterator().next();
            if (pTask.getParams() instanceof DataFrameTransform) {
                return (PersistentTasksCustomMetaData.PersistentTask<DataFrameTransform>)pTask;
            }
            throw new ElasticsearchStatusException("Found data frame transform persistent task [" + id + "] with incorrect params",
                RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void cancelDataFrameTask(String taskId, String dataFrameId, Exception exception, Consumer<Exception> onFailure) {
        persistentTasksService.sendRemoveRequest(taskId,
            new ActionListener<PersistentTasksCustomMetaData.PersistentTask<?>>() {
                @Override
                public void onResponse(PersistentTasksCustomMetaData.PersistentTask<?> task) {
                    // We succeeded in cancelling the persistent task, but the
                    // problem that caused us to cancel it is the overall result
                    onFailure.accept(exception);
                }

                @Override
                public void onFailure(Exception e) {
                    logger.error("[" + dataFrameId + "] Failed to cancel persistent task that could " +
                        "not be assigned due to [" + exception.getMessage() + "]", e);
                    onFailure.accept(exception);
                }
            }
        );
    }

    private void waitForDataFrameTaskStarted(String taskId,
                                             DataFrameTransform params,
                                             TimeValue timeout,
                                             ActionListener<Boolean> listener) {
        DataFramePredicate predicate = new DataFramePredicate();
        persistentTasksService.waitForPersistentTaskCondition(taskId, predicate, timeout,
            new PersistentTasksService.WaitForPersistentTaskListener<DataFrameTransform>() {
                @Override
                public void onResponse(PersistentTasksCustomMetaData.PersistentTask<DataFrameTransform>
                                           persistentTask) {
                    if (predicate.exception != null) {
                        // We want to return to the caller without leaving an unassigned persistent task
                        cancelDataFrameTask(taskId, params.getId(), predicate.exception, listener::onFailure);
                    } else {
                        listener.onResponse(true);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(e);
                }

                @Override
                public void onTimeout(TimeValue timeout) {
                    listener.onFailure(new ElasticsearchException("Starting dataframe ["
                        + params.getId() + "] timed out after [" + timeout + "]"));
                }
            });
    }

    /**
     * Important: the methods of this class must NOT throw exceptions.  If they did then the callers
     * of endpoints waiting for a condition tested by this predicate would never get a response.
     */
    private class DataFramePredicate implements Predicate<PersistentTasksCustomMetaData.PersistentTask<?>> {

        private volatile Exception exception;

        @Override
        public boolean test(PersistentTasksCustomMetaData.PersistentTask<?> persistentTask) {
            if (persistentTask == null) {
                return false;
            }
            PersistentTasksCustomMetaData.Assignment assignment = persistentTask.getAssignment();
            if (assignment != null &&
                assignment.equals(PersistentTasksCustomMetaData.INITIAL_ASSIGNMENT) == false &&
                assignment.isAssigned() == false) {
                // For some reason, the task is not assigned to a node, but is no longer in the `INITIAL_ASSIGNMENT` state
                // Consider this a failure.
                exception = new ElasticsearchStatusException("Could not start dataframe, allocation explanation [" +
                    assignment.getExplanation() + "]", RestStatus.TOO_MANY_REQUESTS);
                return true;
            }
            // We just want it assigned so we can tell it to start working
            return assignment != null && assignment.isAssigned() && isNotStopped(persistentTask);
        }

        // checking for `isNotStopped` as the state COULD be marked as failed for any number of reasons
        // But if it is in a failed state, _stats will show as much and give good reason to the user.
        // If it is not able to be assigned to a node all together, we should just close the task completely
        private boolean isNotStopped(PersistentTasksCustomMetaData.PersistentTask<?> task) {
            DataFrameTransformState state = (DataFrameTransformState)task.getState();
            return state != null && state.getTaskState().equals(DataFrameTransformTaskState.STOPPED) == false;
        }
    }
}
