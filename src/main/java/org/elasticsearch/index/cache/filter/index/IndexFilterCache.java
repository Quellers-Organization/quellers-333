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

package org.elasticsearch.index.cache.filter.index;

import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.Weight;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.AbstractIndexComponent;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.cache.filter.FilterCache;
import org.elasticsearch.index.settings.IndexSettings;
import org.elasticsearch.indices.cache.filter.IndicesFilterCache;

/**
 * The index-level filter cache. This class mostly delegates to the node-level
 * filter cache: {@link IndicesFilterCache}.
 */
public class IndexFilterCache extends AbstractIndexComponent implements FilterCache {

    final IndicesFilterCache indicesFilterCache;

    @Inject
    public IndexFilterCache(Index index, @IndexSettings Settings indexSettings, IndicesFilterCache indicesFilterCache) {
        super(index, indexSettings);
        this.indicesFilterCache = indicesFilterCache;
    }

    @Override
    public void close() throws ElasticsearchException {
        clear("close");
    }

    @Override
    public void clear(String reason) {
        logger.debug("full cache clear, reason [{}]", reason);
        indicesFilterCache.clearIndex(index.getName());
    }

    @Override
    public Weight doCache(Weight weight, QueryCachingPolicy policy) {
        return indicesFilterCache.doCache(weight, policy);
    }

}
