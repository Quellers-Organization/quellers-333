/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.searchablesnapshots.cache.blob;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkAction;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.search.ClosePointInTimeAction;
import org.elasticsearch.action.search.ClosePointInTimeRequest;
import org.elasticsearch.action.search.ClosePointInTimeResponse;
import org.elasticsearch.action.search.OpenPointInTimeAction;
import org.elasticsearch.action.search.OpenPointInTimeRequest;
import org.elasticsearch.action.search.OpenPointInTimeResponse;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.TransportActions;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.OriginSettingClient;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.RepositoriesMetadata;
import org.elasticsearch.cluster.routing.IndexRoutingTable;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.Releasable;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.PointInTimeBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.snapshots.SearchableSnapshotsSettings;
import org.elasticsearch.threadpool.Scheduler;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.elasticsearch.gateway.GatewayService.STATE_NOT_RECOVERED_BLOCK;
import static org.elasticsearch.xpack.core.ClientHelper.SEARCHABLE_SNAPSHOTS_ORIGIN;
import static org.elasticsearch.xpack.searchablesnapshots.SearchableSnapshots.SNAPSHOT_BLOB_CACHE_INDEX;
import static org.elasticsearch.xpack.searchablesnapshots.SearchableSnapshots.SNAPSHOT_INDEX_ID_SETTING;
import static org.elasticsearch.xpack.searchablesnapshots.SearchableSnapshots.SNAPSHOT_SNAPSHOT_ID_SETTING;

/**
 * A service that delete documents in the snapshot blob cache index when they are not required anymore.
 *
 * This service runs on the data node that contains the snapshot blob cache primary shard. It listens to cluster state updates to find
 * searchable snapshot indices that are deleted and checks if the index snapshot is still used by other searchable snapshot indices. If the
 * index snapshot is not used anymore then i triggers the deletion of corresponding cached blobs in the snapshot blob cache index using a
 * delete-by-query.
 */
public class BlobStoreCacheMaintenanceService implements ClusterStateListener {

    private static final Logger logger = LogManager.getLogger(BlobStoreCacheMaintenanceService.class);

    /**
     * The interval at which the periodic cleanup of the blob store cache index is scheduled.
     */
    public static final Setting<TimeValue> SNAPSHOT_SNAPSHOT_CLEANUP_INTERVAL_SETTING = Setting.timeSetting(
        "searchable_snapshots.blob_cache.periodic_cleanup.interval",
        TimeValue.timeValueHours(1),
        TimeValue.ZERO,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );
    /**
     * The keep alive value for the internal point-in-time requests executed during the periodic cleanup.
     */
    public static final Setting<TimeValue> SNAPSHOT_SNAPSHOT_CLEANUP_KEEP_ALIVE_SETTING = Setting.timeSetting(
        "searchable_snapshots.blob_cache.periodic_cleanup.pit_keep_alive",
        TimeValue.timeValueMinutes(5L),
        TimeValue.timeValueSeconds(10),
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );
    /**
     * The number of documents that are searched for and bulk-deleted during the periodic cleanup.
     */
    public static final Setting<Integer> SNAPSHOT_SNAPSHOT_CLEANUP_BATCH_SIZE_SETTING = Setting.intSetting(
        "searchable_snapshots.blob_cache.periodic_cleanup.batch_size",
        100,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

    private final ClusterService clusterService;
    private final Client clientWithOrigin;
    private final String systemIndexName;
    private final ThreadPool threadPool;

    private volatile Scheduler.Cancellable periodicTask;
    private volatile TimeValue periodicTaskInterval;
    private volatile TimeValue periodicTaskKeepAlive;
    private volatile int periodicTaskBatchSize;
    private volatile boolean schedulePeriodic;

    public BlobStoreCacheMaintenanceService(
        Settings settings,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        String systemIndexName
    ) {
        this.clientWithOrigin = new OriginSettingClient(Objects.requireNonNull(client), SEARCHABLE_SNAPSHOTS_ORIGIN);
        this.systemIndexName = Objects.requireNonNull(systemIndexName);
        this.clusterService = Objects.requireNonNull(clusterService);
        this.threadPool = Objects.requireNonNull(threadPool);
        this.periodicTaskInterval = SNAPSHOT_SNAPSHOT_CLEANUP_INTERVAL_SETTING.get(settings);
        this.periodicTaskKeepAlive = SNAPSHOT_SNAPSHOT_CLEANUP_KEEP_ALIVE_SETTING.get(settings);
        this.periodicTaskBatchSize = SNAPSHOT_SNAPSHOT_CLEANUP_BATCH_SIZE_SETTING.get(settings);
        ClusterSettings clusterSettings = clusterService.getClusterSettings();
        clusterSettings.addSettingsUpdateConsumer(SNAPSHOT_SNAPSHOT_CLEANUP_INTERVAL_SETTING, this::setPeriodicTaskInterval);
        clusterSettings.addSettingsUpdateConsumer(SNAPSHOT_SNAPSHOT_CLEANUP_KEEP_ALIVE_SETTING, this::setPeriodicTaskKeepAlive);
        clusterSettings.addSettingsUpdateConsumer(SNAPSHOT_SNAPSHOT_CLEANUP_BATCH_SIZE_SETTING, this::setPeriodicTaskBatchSize);
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        final ClusterState state = event.state();
        if (state.getBlocks().hasGlobalBlock(STATE_NOT_RECOVERED_BLOCK)) {
            return; // state not fully recovered
        }
        final ShardRouting primary = systemIndexPrimaryShard(state);
        if (primary == null || Objects.equals(state.nodes().getLocalNodeId(), primary.currentNodeId()) == false) {
            // system index primary shard does not exist or is not assigned to this data node
            stopPeriodicTask();
            return;
        }
        if (event.indicesDeleted().isEmpty() == false) {
            threadPool.generic().execute(new DeletedIndicesMaintenanceTask(event));
        }
        if (periodicTask == null || periodicTask.isCancelled()) {
            schedulePeriodic = true;
            schedulePeriodicTask();
        }
    }

    private synchronized void setPeriodicTaskInterval(TimeValue interval) {
        this.periodicTaskInterval = interval;
    }

    private void setPeriodicTaskKeepAlive(TimeValue keepAlive) {
        this.periodicTaskKeepAlive = keepAlive;
    }

    public void setPeriodicTaskBatchSize(int batchSize) {
        this.periodicTaskBatchSize = batchSize;
    }

    private synchronized void schedulePeriodicTask() {
        if (schedulePeriodic) {
            try {
                final TimeValue delay = periodicTaskInterval;
                if (delay.getMillis() > 0L) {
                    final PeriodicMaintenanceTask task = new PeriodicMaintenanceTask(periodicTaskKeepAlive, periodicTaskBatchSize);
                    periodicTask = threadPool.schedule(task, delay, ThreadPool.Names.GENERIC);
                } else {
                    periodicTask = null;
                }
            } catch (EsRejectedExecutionException e) {
                if (e.isExecutorShutdown()) {
                    logger.debug("failed to schedule next periodic maintenance task for blob store cache, node is shutting down", e);
                } else {
                    throw e;
                }
            }
        }
    }

    private synchronized void stopPeriodicTask() {
        schedulePeriodic = false;
        if (periodicTask != null && periodicTask.isCancelled() == false) {
            periodicTask.cancel();
            periodicTask = null;
        }
    }

    @Nullable
    private ShardRouting systemIndexPrimaryShard(final ClusterState state) {
        final IndexMetadata indexMetadata = state.metadata().index(systemIndexName);
        if (indexMetadata != null) {
            final IndexRoutingTable indexRoutingTable = state.routingTable().index(indexMetadata.getIndex());
            if (indexRoutingTable != null) {
                return indexRoutingTable.shard(0).primaryShard();
            }
        }
        return null;
    }

    private static boolean hasSearchableSnapshotWith(final ClusterState state, final String snapshotId, final String indexId) {
        for (IndexMetadata indexMetadata : state.metadata()) {
            final Settings indexSettings = indexMetadata.getSettings();
            if (SearchableSnapshotsSettings.isSearchableSnapshotStore(indexSettings)) {
                if (Objects.equals(snapshotId, SNAPSHOT_SNAPSHOT_ID_SETTING.get(indexSettings))
                    && Objects.equals(indexId, SNAPSHOT_INDEX_ID_SETTING.get(indexSettings))) {
                    return true;
                }
            }
        }
        return false;
    }

    static QueryBuilder buildDeleteByQuery(int numberOfShards, String snapshotUuid, String indexUuid) {
        final Set<String> paths = IntStream.range(0, numberOfShards)
            .mapToObj(shard -> String.join("/", snapshotUuid, indexUuid, String.valueOf(shard)))
            .collect(Collectors.toSet());
        assert paths.isEmpty() == false;
        return QueryBuilders.termsQuery("blob.path", paths);
    }

    /**
     * A maintenance task that cleans up the blob store cache index after searchable snapshot indices are deleted
     */
    private class DeletedIndicesMaintenanceTask extends AbstractRunnable {

        private final ClusterChangedEvent event;

        DeletedIndicesMaintenanceTask(ClusterChangedEvent event) {
            assert event.indicesDeleted().isEmpty() == false;
            this.event = Objects.requireNonNull(event);
        }

        @Override
        protected void doRun() {
            final Queue<Tuple<DeleteByQueryRequest, ActionListener<BulkByScrollResponse>>> queue = new LinkedList<>();
            final ClusterState state = event.state();

            for (Index deletedIndex : event.indicesDeleted()) {
                final IndexMetadata indexMetadata = event.previousState().metadata().index(deletedIndex);
                assert indexMetadata != null : "no previous metadata found for " + deletedIndex;
                if (indexMetadata != null) {
                    final Settings indexSetting = indexMetadata.getSettings();
                    if (SearchableSnapshotsSettings.isSearchableSnapshotStore(indexSetting)) {
                        assert state.metadata().hasIndex(deletedIndex) == false;

                        final String snapshotId = SNAPSHOT_SNAPSHOT_ID_SETTING.get(indexSetting);
                        final String indexId = SNAPSHOT_INDEX_ID_SETTING.get(indexSetting);

                        // we should do nothing if the current cluster state contains another
                        // searchable snapshot index that uses the same index snapshot
                        if (hasSearchableSnapshotWith(state, snapshotId, indexId)) {
                            logger.debug(
                                "snapshot [{}] of index {} is in use, skipping maintenance of snapshot blob cache entries",
                                snapshotId,
                                indexId
                            );
                            continue;
                        }

                        final DeleteByQueryRequest request = new DeleteByQueryRequest(systemIndexName);
                        request.setQuery(buildDeleteByQuery(indexMetadata.getNumberOfShards(), snapshotId, indexId));
                        request.setRefresh(queue.isEmpty());

                        queue.add(Tuple.tuple(request, new ActionListener<>() {
                            @Override
                            public void onResponse(BulkByScrollResponse response) {
                                logger.debug(
                                    "blob cache maintenance task deleted [{}] entries after deletion of {} (snapshot:{}, index:{})",
                                    response.getDeleted(),
                                    deletedIndex,
                                    snapshotId,
                                    indexId
                                );
                            }

                            @Override
                            public void onFailure(Exception e) {
                                logger.debug(
                                    () -> new ParameterizedMessage(
                                        "exception when executing blob cache maintenance task after deletion of {} (snapshot:{}, index:{})",
                                        deletedIndex,
                                        snapshotId,
                                        indexId
                                    ),
                                    e
                                );
                            }
                        }));
                    }
                }
            }

            if (queue.isEmpty() == false) {
                executeNextCleanUp(queue);
            }
        }

        void executeNextCleanUp(final Queue<Tuple<DeleteByQueryRequest, ActionListener<BulkByScrollResponse>>> queue) {
            assert Thread.currentThread().getName().contains(ThreadPool.Names.GENERIC);
            final Tuple<DeleteByQueryRequest, ActionListener<BulkByScrollResponse>> next = queue.poll();
            if (next != null) {
                cleanUp(next.v1(), next.v2(), queue);
            }
        }

        void cleanUp(
            final DeleteByQueryRequest request,
            final ActionListener<BulkByScrollResponse> listener,
            final Queue<Tuple<DeleteByQueryRequest, ActionListener<BulkByScrollResponse>>> queue
        ) {
            assert Thread.currentThread().getName().contains(ThreadPool.Names.GENERIC);
            clientWithOrigin.execute(DeleteByQueryAction.INSTANCE, request, ActionListener.runAfter(listener, () -> {
                if (queue.isEmpty() == false) {
                    threadPool.generic().execute(() -> executeNextCleanUp(queue));
                }
            }));
        }

        @Override
        public void onFailure(Exception e) {
            logger.warn(
                () -> new ParameterizedMessage("snapshot blob cache maintenance task failed for cluster state update [{}]", event.source()),
                e
            );
        }
    }

    private class PeriodicMaintenanceTask implements Runnable, Releasable {

        private final TimeValue keepAlive;
        private final int batchSize;

        private final AtomicReference<Exception> error = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicLong processed = new AtomicLong();
        private final AtomicLong deletes = new AtomicLong();
        private final AtomicLong total = new AtomicLong();

        private volatile SearchResponse searchResponse;
        private volatile String pointIntTimeId;
        private volatile Object[] searchAfter;

        PeriodicMaintenanceTask(TimeValue keepAlive, int batchSize) {
            this.keepAlive = keepAlive;
            this.batchSize = batchSize;
        }

        @Override
        public void run() {
            final PeriodicMaintenanceTask maintenanceTask = this;
            assert assertGenericThread();

            try {
                ensureOpen();
                if (pointIntTimeId == null) {
                    final OpenPointInTimeRequest openRequest = new OpenPointInTimeRequest(SNAPSHOT_BLOB_CACHE_INDEX);
                    openRequest.keepAlive(keepAlive);
                    clientWithOrigin.execute(OpenPointInTimeAction.INSTANCE, openRequest, new ActionListener<>() {
                        @Override
                        public void onResponse(OpenPointInTimeResponse response) {
                            logger.trace("periodic maintenance task initialized with point-in-time id [{}]", response.getPointInTimeId());
                            maintenanceTask.pointIntTimeId = response.getPointInTimeId();
                            executeNext(maintenanceTask);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            if (TransportActions.isShardNotAvailableException(e)) {
                                complete(maintenanceTask, null);
                            } else {
                                complete(maintenanceTask, e);
                            }
                        }
                    });
                    return;
                }

                final String pitId = pointIntTimeId;
                assert Strings.hasLength(pitId);

                if (searchResponse == null) {
                    final SearchSourceBuilder searchSource = new SearchSourceBuilder();
                    searchSource.trackScores(false);
                    searchSource.sort("_shard_doc");
                    searchSource.size(batchSize);
                    final PointInTimeBuilder pointInTime = new PointInTimeBuilder(pitId);
                    searchSource.pointInTimeBuilder(pointInTime);
                    pointInTime.setKeepAlive(keepAlive);
                    final SearchRequest searchRequest = new SearchRequest();
                    searchRequest.source(searchSource);
                    if (searchAfter != null) {
                        searchSource.searchAfter(searchAfter);
                    }
                    clientWithOrigin.execute(SearchAction.INSTANCE, searchRequest, new ActionListener<>() {
                        @Override
                        public void onResponse(SearchResponse response) {
                            maintenanceTask.total.compareAndSet(0L, response.getHits().getTotalHits().value);
                            maintenanceTask.searchResponse = response;
                            maintenanceTask.searchAfter = null;
                            executeNext(maintenanceTask);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            complete(maintenanceTask, e);
                        }
                    });
                    return;
                }

                final SearchHit[] searchHits = searchResponse.getHits().getHits();
                if (searchHits != null && searchHits.length > 0) {
                    final ClusterState state = clusterService.state();
                    final BulkRequest bulkRequest = new BulkRequest();

                    final RepositoriesMetadata repositories = state.metadata()
                        .custom(RepositoriesMetadata.TYPE, RepositoriesMetadata.EMPTY);

                    final Set<Tuple<String, String>> missingSnapshots = new HashSet<>();
                    final Set<String> missingRepositories = new HashSet<>();

                    Object[] lastSortValues = null;
                    for (SearchHit searchHit : searchHits) {
                        assert searchHit.getId() != null;
                        assert searchHit.hasSource();
                        boolean delete = false;
                        try {
                            // See {@link BlobStoreCacheService#generateId}
                            // doc id = {repository name}/{snapshot id}/{snapshot index id}/{shard id}/{file name}/@{file offset}
                            final String[] parts = Objects.requireNonNull(searchHit.getId()).split("/");
                            assert parts.length == 6 : Arrays.toString(parts) + " vs " + searchHit.getId();

                            final String repositoryName = parts[0];
                            if (missingRepositories.contains(repositoryName) || repositories.repository(repositoryName) == null) {
                                logger.trace(
                                    "deleting blob store cache entry [id:{}, repository:{}, reason: repository does not exist]",
                                    searchHit.getId(),
                                    repositoryName
                                );
                                missingRepositories.add(repositoryName);
                                delete = true;
                                continue;
                            }

                            final Tuple<String, String> snapshot = Tuple.tuple(parts[1], parts[2]);
                            if (missingSnapshots.contains(snapshot)
                                || hasSearchableSnapshotWith(state, snapshot.v1(), snapshot.v2()) == false) {
                                logger.trace(
                                    "deleting blob store cache entry [id:{}, snapshotId:{}, indexId:{}, reason: unused]",
                                    searchHit.getId(),
                                    snapshot.v1(),
                                    snapshot.v2()
                                );
                                missingSnapshots.add(snapshot);
                                delete = true;
                                continue;
                            }

                            final CachedBlob cachedBlob = CachedBlob.fromSource(Objects.requireNonNull(searchHit.getSourceAsMap()));
                            if (Version.CURRENT.isCompatible(cachedBlob.version()) == false) {
                                logger.trace(
                                    "deleting blob store cache entry [id:{}, version:{}, reason: incompatible version]",
                                    searchHit.getId(),
                                    cachedBlob.version()
                                );
                                delete = true;
                                continue;
                            }
                        } catch (Exception e) {
                            logger.warn(
                                () -> new ParameterizedMessage("failed to parse blob store cache entry [id:{}]", searchHit.getId()),
                                e
                            );
                            delete = true;
                        } finally {
                            if (delete) {
                                final DeleteRequest deleteRequest = new DeleteRequest().index(searchHit.getIndex());
                                deleteRequest.id(searchHit.getId());
                                deleteRequest.setIfSeqNo(searchHit.getSeqNo());
                                deleteRequest.setIfPrimaryTerm(searchHit.getPrimaryTerm());
                                bulkRequest.add(deleteRequest);
                            }
                            maintenanceTask.processed.incrementAndGet();
                            lastSortValues = searchHit.getSortValues();
                        }
                    }

                    assert lastSortValues != null;
                    if (bulkRequest.numberOfActions() == 0) {
                        this.searchResponse = null;
                        this.searchAfter = lastSortValues;
                        executeNext(this);
                        return;
                    }

                    final Object[] finalSearchAfter = lastSortValues;
                    clientWithOrigin.execute(BulkAction.INSTANCE, bulkRequest, new ActionListener<>() {
                        @Override
                        public void onResponse(BulkResponse response) {
                            for (BulkItemResponse itemResponse : response.getItems()) {
                                if (itemResponse.isFailed() == false) {
                                    assert itemResponse.getResponse() instanceof DeleteResponse;
                                    maintenanceTask.deletes.incrementAndGet();
                                }
                            }
                            maintenanceTask.searchResponse = null;
                            maintenanceTask.searchAfter = finalSearchAfter;
                            executeNext(maintenanceTask);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            complete(maintenanceTask, e);
                        }
                    });
                    return;
                }
                // we're done, complete the task
                complete(this, null);
            } catch (Exception e) {
                complete(this, e);
            }
        }

        public boolean isClosed() {
            return closed.get();
        }

        private void ensureOpen() {
            if (isClosed()) {
                assert false : "should not use periodic task after close";
                throw new IllegalStateException("Periodic maintenance task is closed");
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                final Exception e = error.get();
                if (e != null) {
                    logger.warn(
                        () -> new ParameterizedMessage(
                            "periodic maintenance task completed with failure ({} deleted documents out of a total of {})",
                            deletes.get(),
                            total.get()
                        ),
                        e
                    );
                } else {
                    logger.info(
                        () -> new ParameterizedMessage(
                            "periodic maintenance task completed ({} deleted documents out of a total of {}, {})",
                            deletes.get(),
                            total.get(),
                            processed.get()
                        )
                    );
                }
            }
        }

        private boolean assertGenericThread() {
            final String threadName = Thread.currentThread().getName();
            assert threadName.contains(ThreadPool.Names.GENERIC) : threadName;
            return true;
        }
    }

    private void executeNext(PeriodicMaintenanceTask maintenanceTask) {
        threadPool.generic().execute(maintenanceTask);
    }

    private void complete(PeriodicMaintenanceTask maintenanceTask, @Nullable Exception failure) {
        assert maintenanceTask.isClosed() == false;
        final Releasable releasable = () -> {
            try {
                final Exception previous = maintenanceTask.error.getAndSet(failure);
                assert previous == null : "periodic maintenance task already failed: " + previous;
                maintenanceTask.close();
            } finally {
                schedulePeriodicTask();
            }
        };
        boolean waitForRelease = false;
        try {
            final String pitId = maintenanceTask.pointIntTimeId;
            if (Strings.hasLength(pitId)) {
                final ClosePointInTimeRequest closeRequest = new ClosePointInTimeRequest(pitId);
                clientWithOrigin.execute(ClosePointInTimeAction.INSTANCE, closeRequest, ActionListener.runAfter(new ActionListener<>() {
                    @Override
                    public void onResponse(ClosePointInTimeResponse response) {
                        if (response.isSucceeded()) {
                            logger.debug("periodic maintenance task successfully closed point-in-time id [{}]", pitId);
                        } else {
                            logger.debug("point-in-time id [{}] not found", pitId);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        logger.warn(() -> new ParameterizedMessage("failed to close point-in-time id [{}]", pitId), e);
                    }
                }, () -> Releasables.close(releasable)));
                waitForRelease = true;
            }
        } finally {
            if (waitForRelease == false) {
                Releasables.close(releasable);
            }
        }
    }
}
