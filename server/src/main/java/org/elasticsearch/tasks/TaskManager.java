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

package org.elasticsearch.tasks;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.carrotsearch.hppc.ObjectIntMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchTimeoutException;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterStateApplier;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;
import org.elasticsearch.common.util.concurrent.ConcurrentMapLong;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.threadpool.ThreadPool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.common.unit.TimeValue.timeValueMillis;
import static org.elasticsearch.http.HttpTransportSettings.SETTING_HTTP_MAX_HEADER_SIZE;

/**
 * Task Manager service for keeping track of currently running tasks on the nodes
 */
public class TaskManager implements ClusterStateApplier {

    private static final Logger logger = LogManager.getLogger(TaskManager.class);

    private static final TimeValue WAIT_FOR_COMPLETION_POLL = timeValueMillis(100);

    /** Rest headers that are copied to the task */
    private final List<String> taskHeaders;
    private final ThreadPool threadPool;

    private final ConcurrentMapLong<Task> tasks = ConcurrentCollections.newConcurrentMapLongWithAggressiveConcurrency();

    private final ConcurrentMapLong<CancellableTaskHolder> cancellableTasks = ConcurrentCollections
        .newConcurrentMapLongWithAggressiveConcurrency();

    private final AtomicLong taskIdGenerator = new AtomicLong();

    private final Map<TaskId, String> banedParents = new ConcurrentHashMap<>();

    private TaskResultsService taskResultsService;

    private volatile DiscoveryNodes lastDiscoveryNodes = DiscoveryNodes.EMPTY_NODES;

    private final ByteSizeValue maxHeaderSize;

    public TaskManager(Settings settings, ThreadPool threadPool, Set<String> taskHeaders) {
        this.threadPool = threadPool;
        this.taskHeaders = new ArrayList<>(taskHeaders);
        this.maxHeaderSize = SETTING_HTTP_MAX_HEADER_SIZE.get(settings);
    }

    public void setTaskResultsService(TaskResultsService taskResultsService) {
        assert this.taskResultsService == null;
        this.taskResultsService = taskResultsService;
    }

    /**
     * Registers a task without parent task
     */
    public Task register(String type, String action, TaskAwareRequest request) {
        Map<String, String> headers = new HashMap<>();
        long headerSize = 0;
        long maxSize = maxHeaderSize.getBytes();
        ThreadContext threadContext = threadPool.getThreadContext();
        for (String key : taskHeaders) {
            String httpHeader = threadContext.getHeader(key);
            if (httpHeader != null) {
                headerSize += key.length() * 2 + httpHeader.length() * 2;
                if (headerSize > maxSize) {
                    throw new IllegalArgumentException("Request exceeded the maximum size of task headers " + maxHeaderSize);
                }
                headers.put(key, httpHeader);
            }
        }
        Task task = request.createTask(taskIdGenerator.incrementAndGet(), type, action, request.getParentTask(), headers);
        Objects.requireNonNull(task);
        assert task.getParentTaskId().equals(request.getParentTask()) : "Request [ " + request + "] didn't preserve it parentTaskId";
        if (logger.isTraceEnabled()) {
            logger.trace("register {} [{}] [{}] [{}]", task.getId(), type, action, task.getDescription());
        }

        if (task instanceof CancellableTask) {
            registerCancellableTask(task);
        } else {
            Task previousTask = tasks.put(task.getId(), task);
            assert previousTask == null;
        }
        return task;
    }

    private void registerCancellableTask(Task task) {
        CancellableTask cancellableTask = (CancellableTask) task;
        CancellableTaskHolder holder = new CancellableTaskHolder(cancellableTask);
        CancellableTaskHolder oldHolder = cancellableTasks.put(task.getId(), holder);
        assert oldHolder == null;
        // Check if this task was banned before we start it. The empty check is used to avoid
        // computing the hash code of the parent taskId as most of the time banedParents is empty.
        if (task.getParentTaskId().isSet() && banedParents.isEmpty() == false) {
            String reason = banedParents.get(task.getParentTaskId());
            if (reason != null) {
                try {
                    holder.cancel(reason);
                    throw new TaskCancelledException("Task cancelled before it started: " + reason);
                } finally {
                    // let's clean up the registration
                    unregister(task);
                }
            }
        }
    }

    /**
     * Cancels a task
     * <p>
     * After starting cancellation on the parent task, the task manager tries to cancel all children tasks
     * of the current task. Once cancellation of the children tasks is done, the listener is triggered.
     * If the task is completed or unregistered from TaskManager, then the listener is called immediately.
     */
    public void cancel(CancellableTask task, String reason, Runnable listener) {
        CancellableTaskHolder holder = cancellableTasks.get(task.getId());
        if (holder != null) {
            logger.trace("cancelling task with id {}", task.getId());
            holder.cancel(reason, listener);
        } else {
            listener.run();
        }
    }

    /**
     * Unregister the task
     */
    public Task unregister(Task task) {
        logger.trace("unregister task for id: {}", task.getId());
        if (task instanceof CancellableTask) {
            CancellableTaskHolder holder = cancellableTasks.remove(task.getId());
            if (holder != null) {
                holder.finish();
                return holder.getTask();
            } else {
                return null;
            }
        } else {
            return tasks.remove(task.getId());
        }
    }

    /**
     * Register a node on which a child task will execute. The returned {@link Releasable} must be called
     * to unregister the child node once the child task is completed or failed.
     */
    public Releasable registerChildNode(long taskId, DiscoveryNode node) {
        final CancellableTaskHolder holder = cancellableTasks.get(taskId);
        if (holder != null) {
            holder.registerChildNode(node);
            return Releasables.releaseOnce(() -> holder.unregisterChildNode(node));
        }
        return () -> {};
    }

    public DiscoveryNode localNode() {
        return lastDiscoveryNodes.getLocalNode();
    }

    /**
     * Stores the task failure
     */
    public <Response extends ActionResponse> void storeResult(Task task, Exception error, ActionListener<Response> listener) {
        DiscoveryNode localNode = lastDiscoveryNodes.getLocalNode();
        if (localNode == null) {
            // too early to store anything, shouldn't really be here - just pass the error along
            listener.onFailure(error);
            return;
        }
        final TaskResult taskResult;
        try {
            taskResult = task.result(localNode, error);
        } catch (IOException ex) {
            logger.warn(() -> new ParameterizedMessage("couldn't store error {}", ExceptionsHelper.detailedMessage(error)), ex);
            listener.onFailure(ex);
            return;
        }
        taskResultsService.storeResult(taskResult, new ActionListener<Void>() {
            @Override
            public void onResponse(Void aVoid) {
                listener.onFailure(error);
            }

            @Override
            public void onFailure(Exception e) {
                logger.warn(() -> new ParameterizedMessage("couldn't store error {}", ExceptionsHelper.detailedMessage(error)), e);
                listener.onFailure(e);
            }
        });
    }

    /**
     * Stores the task result
     */
    public <Response extends ActionResponse> void storeResult(Task task, Response response, ActionListener<Response> listener) {
        DiscoveryNode localNode = lastDiscoveryNodes.getLocalNode();
        if (localNode == null) {
            // too early to store anything, shouldn't really be here - just pass the response along
            logger.warn("couldn't store response {}, the node didn't join the cluster yet", response);
            listener.onResponse(response);
            return;
        }
        final TaskResult taskResult;
        try {
            taskResult = task.result(localNode, response);
        } catch (IOException ex) {
            logger.warn(() -> new ParameterizedMessage("couldn't store response {}", response), ex);
            listener.onFailure(ex);
            return;
        }

        taskResultsService.storeResult(taskResult, new ActionListener<Void>() {
            @Override
            public void onResponse(Void aVoid) {
                listener.onResponse(response);
            }

            @Override
            public void onFailure(Exception e) {
                logger.warn(() -> new ParameterizedMessage("couldn't store response {}", response), e);
                listener.onFailure(e);
            }
        });
    }

    /**
     * Returns the list of currently running tasks on the node
     */
    public Map<Long, Task> getTasks() {
        HashMap<Long, Task> taskHashMap = new HashMap<>(this.tasks);
        for (CancellableTaskHolder holder : cancellableTasks.values()) {
            taskHashMap.put(holder.getTask().getId(), holder.getTask());
        }
        return Collections.unmodifiableMap(taskHashMap);
    }


    /**
     * Returns the list of currently running tasks on the node that can be cancelled
     */
    public Map<Long, CancellableTask> getCancellableTasks() {
        HashMap<Long, CancellableTask> taskHashMap = new HashMap<>();
        for (CancellableTaskHolder holder : cancellableTasks.values()) {
            taskHashMap.put(holder.getTask().getId(), holder.getTask());
        }
        return Collections.unmodifiableMap(taskHashMap);
    }

    /**
     * Returns a task with given id, or null if the task is not found.
     */
    public Task getTask(long id) {
        Task task = tasks.get(id);
        if (task != null) {
            return task;
        } else {
            return getCancellableTask(id);
        }
    }

    /**
     * Returns a cancellable task with given id, or null if the task is not found.
     */
    public CancellableTask getCancellableTask(long id) {
        CancellableTaskHolder holder = cancellableTasks.get(id);
        if (holder != null) {
            return holder.getTask();
        } else {
            return null;
        }
    }

    /**
     * Returns the number of currently banned tasks.
     * <p>
     * Will be used in task manager stats and for debugging.
     */
    public int getBanCount() {
        return banedParents.size();
    }

    /**
     * Bans all tasks with the specified parent task from execution, cancels all tasks that are currently executing.
     * <p>
     * This method is called when a parent task that has children is cancelled.
     */
    public void setBan(TaskId parentTaskId, String reason) {
        logger.trace("setting ban for the parent task {} {}", parentTaskId, reason);

        // Set the ban first, so the newly created tasks cannot be registered
        synchronized (banedParents) {
            if (lastDiscoveryNodes.nodeExists(parentTaskId.getNodeId())) {
                // Only set the ban if the node is the part of the cluster
                banedParents.put(parentTaskId, reason);
            }
        }

        // Now go through already running tasks and cancel them
        for (Map.Entry<Long, CancellableTaskHolder> taskEntry : cancellableTasks.entrySet()) {
            CancellableTaskHolder holder = taskEntry.getValue();
            if (holder.hasParent(parentTaskId)) {
                holder.cancel(reason);
            }
        }
    }

    /**
     * Removes the ban for the specified parent task.
     * <p>
     * This method is called when a previously banned task finally cancelled
     */
    public void removeBan(TaskId parentTaskId) {
        logger.trace("removing ban for the parent task {}", parentTaskId);
        banedParents.remove(parentTaskId);
    }

    // for testing
    public boolean childTasksCancelledOrBanned(TaskId parentTaskId) {
        if (banedParents.containsKey(parentTaskId)) {
            return true;
        }
        return cancellableTasks.values().stream().noneMatch(task -> task.hasParent(parentTaskId));
    }

    /**
     * Start rejecting new child requests as the parent task was cancelled.
     *
     * @param taskId                the parent task id
     * @param onChildTasksCompleted called when all child tasks are completed or failed
     * @return the set of current nodes that have outstanding child tasks
     */
    public Collection<DiscoveryNode> startBanOnChildrenNodes(long taskId, Runnable onChildTasksCompleted) {
        final CancellableTaskHolder holder = cancellableTasks.get(taskId);
        if (holder != null) {
            return holder.startBan(onChildTasksCompleted);
        } else {
            onChildTasksCompleted.run();
            return Collections.emptySet();
        }
    }

    @Override
    public void applyClusterState(ClusterChangedEvent event) {
        lastDiscoveryNodes = event.state().getNodes();
        if (event.nodesRemoved()) {
            synchronized (banedParents) {
                lastDiscoveryNodes = event.state().getNodes();
                // Remove all bans that were registered by nodes that are no longer in the cluster state
                Iterator<TaskId> banIterator = banedParents.keySet().iterator();
                while (banIterator.hasNext()) {
                    TaskId taskId = banIterator.next();
                    if (lastDiscoveryNodes.nodeExists(taskId.getNodeId()) == false) {
                        logger.debug("Removing ban for the parent [{}] on the node [{}], reason: the parent node is gone", taskId,
                            event.state().getNodes().getLocalNode());
                        banIterator.remove();
                    }
                }
            }
            // Cancel cancellable tasks for the nodes that are gone
            for (Map.Entry<Long, CancellableTaskHolder> taskEntry : cancellableTasks.entrySet()) {
                CancellableTaskHolder holder = taskEntry.getValue();
                CancellableTask task = holder.getTask();
                TaskId parentTaskId = task.getParentTaskId();
                if (parentTaskId.isSet() && lastDiscoveryNodes.nodeExists(parentTaskId.getNodeId()) == false) {
                    if (task.cancelOnParentLeaving()) {
                        holder.cancel("Coordinating node [" + parentTaskId.getNodeId() + "] left the cluster");
                    }
                }
            }
        }
    }

    /**
     * Blocks the calling thread, waiting for the task to vanish from the TaskManager.
     */
    public void waitForTaskCompletion(Task task, long untilInNanos) {
        while (System.nanoTime() - untilInNanos < 0) {
            if (getTask(task.getId()) == null) {
                return;
            }
            try {
                Thread.sleep(WAIT_FOR_COMPLETION_POLL.millis());
            } catch (InterruptedException e) {
                throw new ElasticsearchException("Interrupted waiting for completion of [{}]", e, task);
            }
        }
        throw new ElasticsearchTimeoutException("Timed out waiting for completion of [{}]", task);
    }

    private static class CancellableTaskHolder {
        private final CancellableTask task;
        private boolean finished = false;
        private List<Runnable> cancellationListeners = null;
        private ObjectIntMap<DiscoveryNode> childTasksPerNode = null;
        private boolean banChildren = false;
        private List<Runnable> childTaskCompletedListeners = null;

        CancellableTaskHolder(CancellableTask task) {
            this.task = task;
        }

        void cancel(String reason, Runnable listener) {
            final Runnable toRun;
            synchronized (this) {
                if (finished) {
                    assert cancellationListeners == null;
                    toRun = listener;
                } else {
                    toRun = () -> {};
                    if (listener != null) {
                        if (cancellationListeners == null) {
                            cancellationListeners = new ArrayList<>();
                        }
                        cancellationListeners.add(listener);
                    }
                }
            }
            try {
                task.cancel(reason);
            } finally {
                if (toRun != null) {
                    toRun.run();
                }
            }
        }

        void cancel(String reason) {
            task.cancel(reason);
        }

        /**
         * Marks task as finished.
         */
        public void finish() {
            final List<Runnable> listeners;
            synchronized (this) {
                this.finished = true;
                if (cancellationListeners != null) {
                    listeners = cancellationListeners;
                    cancellationListeners = null;
                } else {
                    listeners = Collections.emptyList();
                }
            }
            // We need to call the listener outside of the synchronised section to avoid potential bottle necks
            // in the listener synchronization
            notifyListeners(listeners);
        }

        private void notifyListeners(List<Runnable> listeners) {
            assert Thread.holdsLock(this) == false;
            Exception rootException = null;
            for (Runnable listener : listeners) {
                try {
                    listener.run();
                } catch (RuntimeException inner) {
                    rootException = ExceptionsHelper.useOrSuppress(rootException, inner);
                }
            }
            ExceptionsHelper.reThrowIfNotNull(rootException);
        }

        public boolean hasParent(TaskId parentTaskId) {
            return task.getParentTaskId().equals(parentTaskId);
        }

        public CancellableTask getTask() {
            return task;
        }

        synchronized void registerChildNode(DiscoveryNode node) {
            if (banChildren) {
                throw new TaskCancelledException("The parent task was cancelled, shouldn't start any child tasks");
            }
            if (childTasksPerNode == null) {
                childTasksPerNode = new ObjectIntHashMap<>();
            }
            childTasksPerNode.addTo(node, 1);
        }

        void unregisterChildNode(DiscoveryNode node) {
            final List<Runnable> listeners;
            synchronized (this) {
                if (childTasksPerNode.addTo(node, -1) == 0) {
                    childTasksPerNode.remove(node);
                }
                if (childTasksPerNode.isEmpty() && this.childTaskCompletedListeners != null) {
                    listeners = childTaskCompletedListeners;
                    childTaskCompletedListeners = null;
                } else {
                    listeners = Collections.emptyList();
                }
            }
            notifyListeners(listeners);
        }

        Set<DiscoveryNode> startBan(Runnable onChildTasksCompleted) {
            final Set<DiscoveryNode> pendingChildNodes;
            final Runnable toRun;
            synchronized (this) {
                banChildren = true;
                if (childTasksPerNode == null) {
                    pendingChildNodes = Collections.emptySet();
                } else {
                    pendingChildNodes = StreamSupport.stream(childTasksPerNode.spliterator(), false)
                        .map(e -> e.key).collect(Collectors.toSet());
                }
                if (pendingChildNodes.isEmpty()) {
                    assert childTaskCompletedListeners == null;
                    toRun = onChildTasksCompleted;
                } else {
                    toRun = () -> {};
                    if (childTaskCompletedListeners == null) {
                        childTaskCompletedListeners = new ArrayList<>();
                    }
                    childTaskCompletedListeners.add(onChildTasksCompleted);
                }
            }
            toRun.run();
            return pendingChildNodes;
        }
    }

}
