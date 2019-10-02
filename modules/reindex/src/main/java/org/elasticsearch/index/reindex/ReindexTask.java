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

package org.elasticsearch.index.reindex;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ContextPreservingActionListener;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.persistent.AllocatedPersistentTask;
import org.elasticsearch.persistent.PersistentTaskState;
import org.elasticsearch.persistent.PersistentTasksCustomMetaData;
import org.elasticsearch.persistent.PersistentTasksExecutor;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.tasks.TaskManager;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ReindexTask extends AllocatedPersistentTask {

    private static final Logger logger = LogManager.getLogger(ReindexTask.class);

    // TODO: Name
    public static final String NAME = "reindex/job";

    private final NodeClient client;
    private final ReindexIndexClient reindexIndexClient;
    private final Reindexer reindexer;
    private final TaskId taskId;
    private final BulkByScrollTask childTask;
    private volatile BulkByScrollTask.Status transientStatus;

    public static class ReindexPersistentTasksExecutor extends PersistentTasksExecutor<ReindexJob> {

        private final ClusterService clusterService;
        private final Client client;
        private final ThreadPool threadPool;
        private final ScriptService scriptService;
        private final ReindexSslConfig reindexSslConfig;
        private final NamedXContentRegistry xContentRegistry;

        ReindexPersistentTasksExecutor(ClusterService clusterService, Client client, NamedXContentRegistry xContentRegistry,
                                       ThreadPool threadPool, ScriptService scriptService, ReindexSslConfig reindexSslConfig) {
            super(NAME, ThreadPool.Names.GENERIC);
            this.clusterService = clusterService;
            this.client = client;
            this.xContentRegistry = xContentRegistry;
            this.threadPool = threadPool;
            this.scriptService = scriptService;
            this.reindexSslConfig = reindexSslConfig;
        }

        @Override
        protected void nodeOperation(AllocatedPersistentTask task, ReindexJob reindexJob, PersistentTaskState state) {
            ReindexTask reindexTask = (ReindexTask) task;
            reindexTask.execute(reindexJob);
        }

        @Override
        protected AllocatedPersistentTask createTask(long id, String type, String action, TaskId parentTaskId,
                                                     PersistentTasksCustomMetaData.PersistentTask<ReindexJob> taskInProgress,
                                                     Map<String, String> headers) {
            headers.putAll(taskInProgress.getParams().getHeaders());
            Reindexer reindexer = new Reindexer(clusterService, client, threadPool, scriptService, reindexSslConfig);
            return new ReindexTask(id, type, action, parentTaskId, headers, clusterService, xContentRegistry, client, reindexer);
        }
    }

    private ReindexTask(long id, String type, String action, TaskId parentTask, Map<String, String> headers,
                        ClusterService clusterService, NamedXContentRegistry xContentRegistry, Client client, Reindexer reindexer) {
        // TODO: description
        super(id, type, action, "persistent reindex", parentTask, headers);
        this.client = (NodeClient) client;
        this.reindexIndexClient = new ReindexIndexClient(client, clusterService, xContentRegistry);
        this.reindexer = reindexer;
        this.taskId = new TaskId(clusterService.localNode().getId(), id);
        this.childTask = new BulkByScrollTask(id, type, action, getDescription(), parentTask, headers);
        this.transientStatus = childTask.getStatus();

    }

    @Override
    public Status getStatus() {
        return transientStatus;
    }

    BulkByScrollTask getChildTask() {
        return childTask;
    }

    private void execute(ReindexJob reindexJob) {
        long allocationId = getAllocationId();
        boolean shouldStoreResult = reindexJob.shouldStoreResult();
        TaskManager taskManager = getTaskManager();

        ReindexTaskStateUpdater taskUpdater = new ReindexTaskStateUpdater(reindexIndexClient, client.threadPool(), getPersistentTaskId(),
            allocationId, new ActionListener<>() {
            @Override
            public void onResponse(ReindexTaskStateDoc stateDoc) {
                updatePersistentTaskState(new ReindexJobState(taskId, ReindexJobState.Status.DONE), new ActionListener<>() {
                    @Override
                    public void onResponse(PersistentTasksCustomMetaData.PersistentTask<?> persistentTask) {
                        if (shouldStoreResult) {
                            taskManager.storeResult(ReindexTask.this, stateDoc.getReindexResponse(), new ActionListener<>() {
                                @Override
                                public void onResponse(BulkByScrollResponse response) {
                                    markAsCompleted();
                                }

                                @Override
                                public void onFailure(Exception ex) {
                                    logger.info("Failed to store task result", ex);
                                    markAsFailed(ex);
                                }
                            });
                        } else {
                            markAsCompleted();
                        }
                    }

                    @Override
                    public void onFailure(Exception ex) {
                        logger.info("Failed to update task in cluster state to success", ex);
                        markEphemeralTaskFailed(shouldStoreResult, ex);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                logger.info("Reindex task failed", e);
                updateClusterStateToFailed(reindexJob.shouldStoreResult(), ReindexJobState.Status.DONE, e);
            }
        }, () -> childTask.cancel("ASSIGNMENT_CONFLICT"));

        taskUpdater.assign(new ActionListener<>() {
            @Override
            public void onResponse(ReindexTaskStateDoc stateDoc) {
                ReindexRequest reindexRequest = stateDoc.getReindexRequest();
                reindexer.initTask(childTask, reindexRequest, new ActionListener<>() {
                    @Override
                    public void onResponse(Void aVoid) {
                        // TODO: need to store status in state so we can continue from it.
                        transientStatus = childTask.getStatus();
                        performReindex(reindexJob, stateDoc, taskUpdater);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        taskUpdater.finish(null, wrapException(e));
                    }
                });
            }

            @Override
            public void onFailure(Exception ex) {
                updateClusterStateToFailed(reindexJob.shouldStoreResult(), ReindexJobState.Status.ASSIGNMENT_FAILED, ex);
            }
        });
    }

    private void sendStartedNotification(boolean shouldStoreResult) {
        updatePersistentTaskState(new ReindexJobState(taskId, ReindexJobState.Status.STARTED), new ActionListener<>() {
            @Override
            public void onResponse(PersistentTasksCustomMetaData.PersistentTask<?> persistentTask) {
            }

            @Override
            public void onFailure(Exception e) {
                logger.info("Failed to update task in cluster state to started", e);
                markEphemeralTaskFailed(shouldStoreResult, e);
            }
        });
    }

    private void performReindex(ReindexJob reindexJob, ReindexTaskStateDoc stateDoc, ReindexTaskStateUpdater taskUpdater) {
        ReindexRequest reindexRequest = stateDoc.getReindexRequest();
        ScrollableHitSource.Checkpoint checkpoint = stateDoc.getCheckpoint();
        ThreadContext threadContext = client.threadPool().getThreadContext();

        Supplier<ThreadContext.StoredContext> context = threadContext.newRestorableContext(false);
        // TODO: Eventually we only want to retain security context
        try (ThreadContext.StoredContext ignore = stashWithHeaders(threadContext, reindexJob.getHeaders())) {
            reindexer.execute(childTask, reindexRequest, new ContextPreservingActionListener<>(context, new ActionListener<>() {
                @Override
                public void onResponse(BulkByScrollResponse response) {
                    transientStatus = response.getStatus();
                    taskUpdater.finish(response, null);
                }

                @Override
                public void onFailure(Exception e) {
                    taskUpdater.finish(null, wrapException(e));
                }
            }), checkpoint, (checkpoint1, status) -> {
                transientStatus = status;
                taskUpdater.onCheckpoint(checkpoint1, status);
            });
        }
        // send this after we started reindex to ensure sub-tasks are created.
        sendStartedNotification(reindexJob.shouldStoreResult());
    }

    private void updateClusterStateToFailed(boolean shouldStoreResult, ReindexJobState.Status status, Exception ex) {
        updatePersistentTaskState(new ReindexJobState(taskId, status), new ActionListener<>() {
            @Override
            public void onResponse(PersistentTasksCustomMetaData.PersistentTask<?> persistentTask) {
                markEphemeralTaskFailed(shouldStoreResult, ex);
            }

            @Override
            public void onFailure(Exception e) {
                logger.info("Failed to update task in cluster state to failed", e);
                ex.addSuppressed(e);
                markEphemeralTaskFailed(shouldStoreResult, ex);
            }
        });
    }

    private void markEphemeralTaskFailed(boolean shouldStoreResult, Exception ex) {
        TaskManager taskManager = getTaskManager();
        assert taskManager != null : "TaskManager should have been set before reindex started";
        if (shouldStoreResult) {
            taskManager.storeResult(ReindexTask.this, ex, ActionListener.wrap(() -> markAsFailed(ex)));
        } else {
            markAsFailed(ex);
        }
    }

    private static ElasticsearchException wrapException(Exception ex) {
        if (ex instanceof ElasticsearchException) {
            return (ElasticsearchException) ex;
        } else {
            return new ElasticsearchException(ex);
        }
    }

    // TODO: Copied from ClientHelper in x-pack
    private static ThreadContext.StoredContext stashWithHeaders(ThreadContext threadContext, Map<String, String> headers) {
        final ThreadContext.StoredContext storedContext = threadContext.stashContext();
        if (headers.containsKey(Task.X_OPAQUE_ID)) {
            headers = new HashMap<>(headers);
            // If the X_OPAQUE_ID is present, we should not set it again.
            headers.remove(Task.X_OPAQUE_ID);
        }
        threadContext.copyHeaders(headers.entrySet());
        return storedContext;
    }
}
