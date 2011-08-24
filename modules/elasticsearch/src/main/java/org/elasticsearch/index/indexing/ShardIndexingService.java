/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.index.indexing;

import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.metrics.MeanMetric;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.settings.IndexSettings;
import org.elasticsearch.index.shard.AbstractIndexShardComponent;
import org.elasticsearch.index.shard.ShardId;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 */
public class ShardIndexingService extends AbstractIndexShardComponent {

    private final StatsHolder totalStats = new StatsHolder();

    private volatile Map<String, StatsHolder> typesStats = ImmutableMap.of();

    private CopyOnWriteArrayList<IndexingOperationListener> listeners = null;

    @Inject public ShardIndexingService(ShardId shardId, @IndexSettings Settings indexSettings) {
        super(shardId, indexSettings);
    }

    /**
     * Returns the stats, including type specific stats. If the types are null/0 length, then nothing
     * is returned for them. If they are set, then only types provided will be returned, or
     * <tt>_all</tt> for all types.
     */
    public IndexingStats stats(String... types) {
        IndexingStats.Stats total = totalStats.stats();
        Map<String, IndexingStats.Stats> typesSt = null;
        if (types != null && types.length > 0) {
            if (types.length == 1 && types[0].equals("_all")) {
                typesSt = new HashMap<String, IndexingStats.Stats>(typesStats.size());
                for (Map.Entry<String, StatsHolder> entry : typesStats.entrySet()) {
                    typesSt.put(entry.getKey(), entry.getValue().stats());
                }
            } else {
                typesSt = new HashMap<String, IndexingStats.Stats>(types.length);
                for (String type : types) {
                    StatsHolder statsHolder = typesStats.get(type);
                    if (statsHolder != null) {
                        typesSt.put(type, statsHolder.stats());
                    }
                }
            }
        }
        return new IndexingStats(total, typesSt);
    }

    public synchronized void addListener(IndexingOperationListener listener) {
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<IndexingOperationListener>();
        }
        listeners.add(listener);
    }

    public synchronized void removeListener(IndexingOperationListener listener) {
        if (listeners == null) {
            return;
        }
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            listeners = null;
        }
    }

    public Engine.Create preCreate(Engine.Create create) {
        if (listeners != null) {
            for (IndexingOperationListener listener : listeners) {
                create = listener.preCreate(create);
            }
        }
        return create;
    }

    public void postCreate(Engine.Create create) {
        long took = create.endTime() - create.startTime();
        totalStats.indexMetric.inc(took);
        typeStats(create.type()).indexMetric.inc(took);
        if (listeners != null) {
            for (IndexingOperationListener listener : listeners) {
                listener.postCreate(create);
            }
        }
    }

    public Engine.Index preIndex(Engine.Index index) {
        if (listeners != null) {
            for (IndexingOperationListener listener : listeners) {
                index = listener.preIndex(index);
            }
        }
        return index;
    }

    public void postIndex(Engine.Index index) {
        long took = index.endTime() - index.startTime();
        totalStats.indexMetric.inc(took);
        typeStats(index.type()).indexMetric.inc(took);
        if (listeners != null) {
            for (IndexingOperationListener listener : listeners) {
                listener.postIndex(index);
            }
        }
    }

    public Engine.Delete preDelete(Engine.Delete delete) {
        if (listeners != null) {
            for (IndexingOperationListener listener : listeners) {
                delete = listener.preDelete(delete);
            }
        }
        return delete;
    }

    public void postDelete(Engine.Delete delete) {
        long took = delete.endTime() - delete.startTime();
        totalStats.deleteMetric.inc(took);
        typeStats(delete.type()).deleteMetric.inc(took);
        if (listeners != null) {
            for (IndexingOperationListener listener : listeners) {
                listener.postDelete(delete);
            }
        }
    }

    public Engine.DeleteByQuery preDeleteByQuery(Engine.DeleteByQuery deleteByQuery) {
        if (listeners != null) {
            for (IndexingOperationListener listener : listeners) {
                deleteByQuery = listener.preDeleteByQuery(deleteByQuery);
            }
        }
        return deleteByQuery;
    }

    public void postDeleteByQuery(Engine.DeleteByQuery deleteByQuery) {
        if (listeners != null) {
            for (IndexingOperationListener listener : listeners) {
                listener.postDeleteByQuery(deleteByQuery);
            }
        }
    }

    public void clear() {
        totalStats.clear();
        synchronized (this) {
            typesStats = ImmutableMap.of();
        }
    }

    private StatsHolder typeStats(String type) {
        StatsHolder stats = typesStats.get(type);
        if (stats == null) {
            synchronized (this) {
                stats = typesStats.get(type);
                if (stats == null) {
                    stats = new StatsHolder();
                    typesStats = MapBuilder.newMapBuilder(typesStats).put(type, stats).immutableMap();
                }
            }
        }
        return stats;
    }

    static class StatsHolder {
        public final MeanMetric indexMetric = new MeanMetric();
        public final MeanMetric deleteMetric = new MeanMetric();

        public IndexingStats.Stats stats() {
            return new IndexingStats.Stats(
                    indexMetric.count(), TimeUnit.NANOSECONDS.toMillis(indexMetric.sum()),
                    deleteMetric.count(), TimeUnit.NANOSECONDS.toMillis(deleteMetric.sum()));
        }

        public void clear() {
            indexMetric.clear();
            deleteMetric.clear();
        }
    }
}
