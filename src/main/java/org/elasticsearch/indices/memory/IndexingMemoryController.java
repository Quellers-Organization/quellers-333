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

package org.elasticsearch.indices.memory;

import java.util.*;
import java.util.concurrent.ScheduledFuture;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.engine.EngineClosedException;
import org.elasticsearch.index.engine.FlushNotAllowedEngineException;
import org.elasticsearch.index.service.IndexService;
import org.elasticsearch.index.shard.IndexShardState;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.shard.service.IndexShard;
import org.elasticsearch.index.shard.service.InternalIndexShard;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.monitor.jvm.JvmInfo;
import org.elasticsearch.node.settings.NodeSettingsService;
import org.elasticsearch.threadpool.ThreadPool;
import com.google.common.collect.Lists;

/**
 *
 */
public class IndexingMemoryController extends AbstractLifecycleComponent<IndexingMemoryController> {

    public static final String INDEX_BUFFER_SIZE = "indices.memory.index_buffer_size";
    public static final String MIN_INDEX_BUFFER_SIZE = "indices.memory.min_index_buffer_size";
    public static final String MAX_INDEX_BUFFER_SIZE = "indices.memory.max_index_buffer_size";
    public static final String MIN_SHARD_INDEX_BUFFER_SIZE = "indices.memory.min_shard_index_buffer_size";
    public static final String MAX_SHARD_INDEX_BUFFER_SIZE = "indices.memory.max_shard_index_buffer_size";

    /** Default value for index_buffer_size. */
    private static final String DEFAULT_INDEX_BUFFER = "10%";

    /** Default value for min_index_buffer_size, which is applied when index_buffer_size is a %tg. */
    private static final ByteSizeValue DEFAULT_MIN_INDEX_BUFFER_SIZE = new ByteSizeValue(48, ByteSizeUnit.MB);

    /** Default min_shard_index_buffer_size, which is applied after dividing up index_buffer_size across all active shards. */
    private static final ByteSizeValue DEFAULT_MIN_SHARD_INDEX_BUFFER_SIZE = new ByteSizeValue(4, ByteSizeUnit.MB);
    
    /** Default max_shard_index_buffer_size, which is applied after dividing up index_buffer_size across all active shards. */
    // LUCENE MONITOR: Based on this thread, currently (based on Mike), having a large buffer does not make a lot of sense: https://issues.apache.org/jira/browse/LUCENE-2324?focusedCommentId=13005155&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-13005155
    private static final ByteSizeValue DEFAULT_MAX_SHARD_INDEX_BUFFER_SIZE = new ByteSizeValue(512, ByteSizeUnit.MB);

    private final ThreadPool threadPool;
    private final IndicesService indicesService;

    private volatile String indexingBufferString;
    private volatile ByteSizeValue indexingBuffer;
    private volatile ByteSizeValue minShardIndexingBufferSize;
    private volatile ByteSizeValue maxShardIndexingBufferSize;
    private volatile ByteSizeValue minIndexingBufferSize;
    private volatile ByteSizeValue maxIndexingBufferSize;

    private final ByteSizeValue translogBuffer;
    private final ByteSizeValue minShardTranslogBufferSize;
    private final ByteSizeValue maxShardTranslogBufferSize;
    private final NodeSettingsService nodeSettingsService;

    private final TimeValue inactiveTime;
    private final TimeValue interval;

    private volatile ScheduledFuture scheduler;

    private final ShardsIndicesStatusChecker statusChecker = new ShardsIndicesStatusChecker();
    private final ApplySettings applySettings = new ApplySettings();

    private static final EnumSet<IndexShardState> CAN_UPDATE_INDEX_BUFFER_STATES = EnumSet.of(IndexShardState.POST_RECOVERY, IndexShardState.STARTED, IndexShardState.RELOCATED);

    @Inject
    public IndexingMemoryController(Settings settings, ThreadPool threadPool, IndicesService indicesService, NodeSettingsService nodeSettingsService) {
        super(settings);
        this.threadPool = threadPool;
        this.indicesService = indicesService;
        this.nodeSettingsService = nodeSettingsService;
        this.indexingBufferString = settings.get(INDEX_BUFFER_SIZE, DEFAULT_INDEX_BUFFER);

        // TODO: should we validate min/max here?  somewhat dangerous because on upgrade this means existing configs may fail, since we are
        // now lenient... but this would be a "favor" to such users since they don't realize their config is messed up now:
        this.minIndexingBufferSize = settings.getAsBytesSize(MIN_INDEX_BUFFER_SIZE, DEFAULT_MIN_INDEX_BUFFER_SIZE);
        this.maxIndexingBufferSize = settings.getAsBytesSize(MAX_INDEX_BUFFER_SIZE, null);
        this.minShardIndexingBufferSize = settings.getAsBytesSize(MIN_SHARD_INDEX_BUFFER_SIZE, DEFAULT_MIN_SHARD_INDEX_BUFFER_SIZE);
        this.maxShardIndexingBufferSize = settings.getAsBytesSize(MAX_SHARD_INDEX_BUFFER_SIZE, DEFAULT_MAX_SHARD_INDEX_BUFFER_SIZE);

        this.indexingBuffer = computeIndexingBuffer(this.indexingBufferString);

        ByteSizeValue translogBuffer;
        String translogBufferSetting = componentSettings.get("translog_buffer_size", "1%");
        if (translogBufferSetting.endsWith("%")) {
            double percent = Double.parseDouble(translogBufferSetting.substring(0, translogBufferSetting.length() - 1));
            translogBuffer = new ByteSizeValue((long) (((double) JvmInfo.jvmInfo().mem().heapMax().bytes()) * (percent / 100)));
            ByteSizeValue minTranslogBuffer = componentSettings.getAsBytesSize("min_translog_buffer_size", new ByteSizeValue(256, ByteSizeUnit.KB));
            ByteSizeValue maxTranslogBuffer = componentSettings.getAsBytesSize("max_translog_buffer_size", null);

            if (translogBuffer.bytes() < minTranslogBuffer.bytes()) {
                translogBuffer = minTranslogBuffer;
            }
            if (maxTranslogBuffer != null && translogBuffer.bytes() > maxTranslogBuffer.bytes()) {
                translogBuffer = maxTranslogBuffer;
            }
        } else {
            translogBuffer = ByteSizeValue.parseBytesSizeValue(translogBufferSetting, null);
        }
        this.translogBuffer = translogBuffer;
        this.minShardTranslogBufferSize = componentSettings.getAsBytesSize("min_shard_translog_buffer_size", new ByteSizeValue(2, ByteSizeUnit.KB));
        this.maxShardTranslogBufferSize = componentSettings.getAsBytesSize("max_shard_translog_buffer_size", new ByteSizeValue(64, ByteSizeUnit.KB));

        this.inactiveTime = componentSettings.getAsTime("shard_inactive_time", TimeValue.timeValueMinutes(30));
        // we need to have this relatively small to move a shard from inactive to active fast (enough)
        this.interval = componentSettings.getAsTime("interval", TimeValue.timeValueSeconds(30));

        logger.debug("using {} [{}, actual value: {}], with {} [{}], {} [{}], shard_inactive_time [{}]",
                     INDEX_BUFFER_SIZE, this.indexingBufferString, this.indexingBuffer,
                     MIN_SHARD_INDEX_BUFFER_SIZE,
                     this.minShardIndexingBufferSize,
                     MAX_SHARD_INDEX_BUFFER_SIZE,
                     this.maxShardIndexingBufferSize, this.inactiveTime);
        nodeSettingsService.addListener(applySettings);
    }

    private ByteSizeValue computeIndexingBuffer(String setting) {
        ByteSizeValue indexingBuffer;
        if (setting.endsWith("%")) {
            double percent = Double.parseDouble(setting.substring(0, setting.length() - 1));
            indexingBuffer = new ByteSizeValue((long) (((double) JvmInfo.jvmInfo().mem().heapMax().bytes()) * (percent / 100)));

            if (indexingBuffer.bytes() < minIndexingBufferSize.bytes()) {
                indexingBuffer = minIndexingBufferSize;
            }

            if (maxIndexingBufferSize != null && indexingBuffer.bytes() > maxIndexingBufferSize.bytes()) {
                indexingBuffer = maxIndexingBufferSize;
            }
        } else {
            indexingBuffer = ByteSizeValue.parseBytesSizeValue(setting, null);
        }
        return indexingBuffer;
    }

    @Override
    protected void doStart() throws ElasticsearchException {
        // its fine to run it on the scheduler thread, no busy work
        this.scheduler = threadPool.scheduleWithFixedDelay(statusChecker, interval);
    }

    @Override
    protected void doStop() throws ElasticsearchException {
        if (scheduler != null) {
            scheduler.cancel(false);
            scheduler = null;
        }
    }

    @Override
    protected void doClose() throws ElasticsearchException {
        nodeSettingsService.removeListener(applySettings);
    }

    class ApplySettings implements NodeSettingsService.Listener {
        @Override
        public void onRefreshSettings(Settings settings) {
            String indexingBufferString = settings.get(INDEX_BUFFER_SIZE,
                                                       IndexingMemoryController.this.indexingBufferString);

            ByteSizeValue minIndexingBufferSize = settings.getAsBytesSize(MIN_INDEX_BUFFER_SIZE,
                                                                          IndexingMemoryController.this.minIndexingBufferSize);
            ByteSizeValue maxIndexingBufferSize = settings.getAsBytesSize(MAX_INDEX_BUFFER_SIZE,
                                                                          IndexingMemoryController.this.maxIndexingBufferSize);
            if (maxIndexingBufferSize != null && minIndexingBufferSize.bytes() > maxIndexingBufferSize.bytes()) {
                throw new IllegalArgumentException("minIndexingBufferSize=" + minIndexingBufferSize + " maxIndexingBufferSize=" + maxIndexingBufferSize);
            }

            ByteSizeValue minShardIndexingBufferSize = settings.getAsBytesSize(MIN_SHARD_INDEX_BUFFER_SIZE,
                                                                               IndexingMemoryController.this.minShardIndexingBufferSize);
            ByteSizeValue maxShardIndexingBufferSize = settings.getAsBytesSize(MAX_SHARD_INDEX_BUFFER_SIZE,
                                                                               IndexingMemoryController.this.maxShardIndexingBufferSize);
            if (minShardIndexingBufferSize.bytes() > maxShardIndexingBufferSize.bytes()) {
                throw new IllegalArgumentException("minShardIndexingBufferSize=" + minShardIndexingBufferSize + " maxShardIndexingBufferSize=" + maxShardIndexingBufferSize);
            }

            boolean changed = false;

            if (minIndexingBufferSize.equals(IndexingMemoryController.this.minIndexingBufferSize) == false) {
                logger.info("updating [{}] from [{}] to [{}]", MIN_INDEX_BUFFER_SIZE,
                            IndexingMemoryController.this.minIndexingBufferSize, minIndexingBufferSize);
                IndexingMemoryController.this.minIndexingBufferSize = minIndexingBufferSize;
                changed = true;
            }

            if (maxIndexingBufferSize != null &&
                (IndexingMemoryController.this.maxIndexingBufferSize == null ||
                 (maxIndexingBufferSize.equals(IndexingMemoryController.this.maxIndexingBufferSize) == false))) {
                logger.info("updating [{}] from [{}] to [{}]", MAX_INDEX_BUFFER_SIZE,
                            IndexingMemoryController.this.maxIndexingBufferSize, maxIndexingBufferSize);
                IndexingMemoryController.this.maxIndexingBufferSize = maxIndexingBufferSize;
                changed = true;
            }

            if (minShardIndexingBufferSize.equals(IndexingMemoryController.this.minShardIndexingBufferSize) == false) {
                logger.info("updating [{}] from [{}] to [{}]", MIN_SHARD_INDEX_BUFFER_SIZE,
                            IndexingMemoryController.this.minShardIndexingBufferSize, minShardIndexingBufferSize);
                IndexingMemoryController.this.minShardIndexingBufferSize = minShardIndexingBufferSize;
                changed = true;
            }

            if (maxShardIndexingBufferSize.equals(IndexingMemoryController.this.maxShardIndexingBufferSize) == false) {
                logger.info("updating [{}] from [{}] to [{}]", MAX_SHARD_INDEX_BUFFER_SIZE,
                            IndexingMemoryController.this.maxShardIndexingBufferSize, maxShardIndexingBufferSize);
                IndexingMemoryController.this.maxShardIndexingBufferSize = maxShardIndexingBufferSize;
                changed = true;
            }

            // Must parse this after incorporating the min/max changes above:
            ByteSizeValue indexingBuffer = computeIndexingBuffer(indexingBufferString);

            // Only set this.indexingBuffer/String after computeIndexingBuffer succeeds in parsing it:
            if (indexingBufferString.equals(IndexingMemoryController.this.indexingBufferString) == false ||
                indexingBuffer.equals(IndexingMemoryController.this.indexingBuffer) == false) {
                logger.info("updating [{}] from [{}, actual value: {}] to [{}, actual value: {}]",
                            INDEX_BUFFER_SIZE,
                            IndexingMemoryController.this.indexingBufferString,
                            IndexingMemoryController.this.indexingBuffer,
                            indexingBufferString,
                            indexingBuffer);
                IndexingMemoryController.this.indexingBufferString = indexingBufferString;
                IndexingMemoryController.this.indexingBuffer = indexingBuffer;
                changed = true;
            }

            if (changed) {
                // Recalculate RAM buffer for all active shards:
                logger.debug("using {} [{}], with {} [{}], {} [{}]",
                             INDEX_BUFFER_SIZE, indexingBuffer,
                             MIN_SHARD_INDEX_BUFFER_SIZE,
                             minShardIndexingBufferSize,
                             MAX_SHARD_INDEX_BUFFER_SIZE,
                             maxShardIndexingBufferSize);
                statusChecker.run(true);
            }
        }
    }

    /**
     * returns the current budget for the total amount of indexing buffers of
     * active shards on this node
     */
    public ByteSizeValue getIndexingBufferSize() {
        return indexingBuffer;
    }

    /** 
     * returns the current minimum per-shard index buffer size
     */
    public ByteSizeValue getMinShardIndexingBufferSize() {
        return minShardIndexingBufferSize;
    }

    /** 
     * returns the current maximum per-shard index buffer size
     */
    public ByteSizeValue getMaxShardIndexingBufferSize() {
        return maxShardIndexingBufferSize;
    }

    /** 
     * returns the current minimum index buffer size
     */
    public ByteSizeValue getMinIndexingBufferSize() {
        return minIndexingBufferSize;
    }

    /** 
     * returns the current maximum index buffer size (can be null)
     */
    public ByteSizeValue getMaxIndexingBufferSize() {
        return maxIndexingBufferSize;
    }

    class ShardsIndicesStatusChecker implements Runnable {

        private final Map<ShardId, ShardIndexingStatus> shardsIndicesStatus = new HashMap<>();

        @Override
        public void run() {
            run(false);
        }

        /** If forced is true, which happens when dynamic memory settings are updating, we recalculate even if active/inactive shards didn't change. */
        public synchronized void run(boolean forced) {

            EnumSet<ShardStatusChangeType> changes = EnumSet.noneOf(ShardStatusChangeType.class);

            changes.addAll(purgeDeletedAndClosedShards());

            final List<IndexShard> activeToInactiveIndexingShards = Lists.newArrayList();
            final int activeShards = updateShardStatuses(changes, activeToInactiveIndexingShards);
            for (IndexShard indexShard : activeToInactiveIndexingShards) {
                // update inactive indexing buffer size
                try {
                    ((InternalIndexShard) indexShard).engine().updateIndexingBufferSize(Engine.INACTIVE_SHARD_INDEXING_BUFFER);
                    ((InternalIndexShard) indexShard).translog().updateBuffer(Translog.INACTIVE_SHARD_TRANSLOG_BUFFER);
                } catch (EngineClosedException e) {
                    // ignore
                } catch (FlushNotAllowedEngineException e) {
                    // ignore
                }
            }
            if (forced || !changes.isEmpty()) {
                calcAndSetShardBuffers(activeShards, "[" + changes + " forced=" + forced + "]");
            }
        }

        /**
         * goes through all existing shards and check whether the changes their active status
         *
         * @return the current count of active shards
         */
        private int updateShardStatuses(EnumSet<ShardStatusChangeType> changes, List<IndexShard> activeToInactiveIndexingShards) {
            int activeShards = 0;
            for (IndexService indexService : indicesService) {
                for (IndexShard indexShard : indexService) {

                    if (!CAN_UPDATE_INDEX_BUFFER_STATES.contains(indexShard.state())) {
                        // not ready to be updated yet.
                        continue;
                    }

                    final long time = threadPool.estimatedTimeInMillis();

                    Translog translog = ((InternalIndexShard) indexShard).translog();
                    ShardIndexingStatus status = shardsIndicesStatus.get(indexShard.shardId());
                    if (status == null) {
                        status = new ShardIndexingStatus();
                        shardsIndicesStatus.put(indexShard.shardId(), status);
                        changes.add(ShardStatusChangeType.ADDED);
                    }
                    // check if it is deemed to be inactive (sam translogId and numberOfOperations over a long period of time)
                    if (status.translogId == translog.currentId() && translog.estimatedNumberOfOperations() == 0) {
                        if (status.time == -1) { // first time
                            status.time = time;
                        }
                        // inactive?
                        if (status.activeIndexing) {
                            // mark it as inactive only if enough time has passed and there are no ongoing merges going on...
                            if ((time - status.time) > inactiveTime.millis() && indexShard.mergeStats().getCurrent() == 0) {
                                // inactive for this amount of time, mark it
                                activeToInactiveIndexingShards.add(indexShard);
                                status.activeIndexing = false;
                                changes.add(ShardStatusChangeType.BECAME_INACTIVE);
                                logger.debug("marking shard [{}][{}] as inactive (inactive_time[{}]) indexing wise, setting size to [{}]", indexShard.shardId().index().name(), indexShard.shardId().id(), inactiveTime, Engine.INACTIVE_SHARD_INDEXING_BUFFER);
                            }
                        }
                    } else {
                        if (!status.activeIndexing) {
                            status.activeIndexing = true;
                            changes.add(ShardStatusChangeType.BECAME_ACTIVE);
                            logger.debug("marking shard [{}][{}] as active indexing wise", indexShard.shardId().index().name(), indexShard.shardId().id());
                        }
                        status.time = -1;
                    }
                    status.translogId = translog.currentId();
                    status.translogNumberOfOperations = translog.estimatedNumberOfOperations();

                    if (status.activeIndexing) {
                        activeShards++;
                    }
                }
            }
            return activeShards;
        }

        /**
         * purge any existing statuses that are no longer updated
         *
         * @return true if any change
         */
        private EnumSet<ShardStatusChangeType> purgeDeletedAndClosedShards() {
            EnumSet<ShardStatusChangeType> changes = EnumSet.noneOf(ShardStatusChangeType.class);

            Iterator<ShardId> statusShardIdIterator = shardsIndicesStatus.keySet().iterator();
            while (statusShardIdIterator.hasNext()) {
                ShardId statusShardId = statusShardIdIterator.next();
                IndexService indexService = indicesService.indexService(statusShardId.getIndex());
                boolean remove = false;
                try {
                    if (indexService == null) {
                        remove = true;
                        continue;
                    }
                    IndexShard indexShard = indexService.shard(statusShardId.id());
                    if (indexShard == null) {
                        remove = true;
                        continue;
                    }
                    remove = !CAN_UPDATE_INDEX_BUFFER_STATES.contains(indexShard.state());

                } finally {
                    if (remove) {
                        changes.add(ShardStatusChangeType.DELETED);
                        statusShardIdIterator.remove();
                    }
                }
            }
            return changes;
        }

        private void calcAndSetShardBuffers(int activeShards, String reason) {
            if (activeShards == 0) {
                return;
            }
            ByteSizeValue shardIndexingBufferSize = new ByteSizeValue(indexingBuffer.bytes() / activeShards);
            if (shardIndexingBufferSize.bytes() < minShardIndexingBufferSize.bytes()) {
                shardIndexingBufferSize = minShardIndexingBufferSize;
            }
            if (shardIndexingBufferSize.bytes() > maxShardIndexingBufferSize.bytes()) {
                shardIndexingBufferSize = maxShardIndexingBufferSize;
            }

            ByteSizeValue shardTranslogBufferSize = new ByteSizeValue(translogBuffer.bytes() / activeShards);
            if (shardTranslogBufferSize.bytes() < minShardTranslogBufferSize.bytes()) {
                shardTranslogBufferSize = minShardTranslogBufferSize;
            }
            if (shardTranslogBufferSize.bytes() > maxShardTranslogBufferSize.bytes()) {
                shardTranslogBufferSize = maxShardTranslogBufferSize;
            }

            logger.debug("recalculating shard indexing buffer (reason={}), total is [{}] with [{}] active shards, each shard set to indexing=[{}], translog=[{}]", reason, indexingBuffer, activeShards, shardIndexingBufferSize, shardTranslogBufferSize);
            for (IndexService indexService : indicesService) {
                for (IndexShard indexShard : indexService) {
                    IndexShardState state = indexShard.state();
                    if (!CAN_UPDATE_INDEX_BUFFER_STATES.contains(state)) {
                        logger.trace("shard [{}] is not yet ready for index buffer update. index shard state: [{}]", indexShard.shardId(), state);
                        continue;
                    }
                    ShardIndexingStatus status = shardsIndicesStatus.get(indexShard.shardId());
                    if (status == null || status.activeIndexing) {
                        try {
                            ((InternalIndexShard) indexShard).engine().updateIndexingBufferSize(shardIndexingBufferSize);
                            ((InternalIndexShard) indexShard).translog().updateBuffer(shardTranslogBufferSize);
                        } catch (EngineClosedException e) {
                            // ignore
                            continue;
                        } catch (FlushNotAllowedEngineException e) {
                            // ignore
                            continue;
                        } catch (Exception e) {
                            logger.warn("failed to set shard {} index buffer to [{}]", indexShard.shardId(), shardIndexingBufferSize);
                        }
                    }
                }
            }
        }
    }

    private static enum ShardStatusChangeType {
        ADDED, DELETED, BECAME_ACTIVE, BECAME_INACTIVE
    }


    static class ShardIndexingStatus {
        long translogId = -1;
        int translogNumberOfOperations = -1;
        boolean activeIndexing = true;
        long time = -1; // contains the first time we saw this shard with no operations done on it
    }
}
