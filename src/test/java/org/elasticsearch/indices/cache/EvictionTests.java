
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

package org.elasticsearch.indices.cache;

import com.google.common.base.Predicate;
import org.apache.lucene.util.English;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.common.metrics.EvictionStats;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.elasticsearch.test.ElasticsearchIntegrationTest.Scope;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;
import static org.hamcrest.Matchers.*;

/**
 */
@ElasticsearchIntegrationTest.ClusterScope(scope= Scope.TEST, minNumDataNodes=1, maxNumDataNodes = 4, numClientNodes = 0)
public class EvictionTests extends ElasticsearchIntegrationTest {

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        // Set the fielddata and filter size to 1b and expire to 1ms, forces evictions immediately
        return  ImmutableSettings.settingsBuilder()
                .put(super.nodeSettings(nodeOrdinal))
                .put("indices.fielddata.cache.expire", "10ms")
                .put("indices.fielddata.cache.size", "10b")
                .put("indices.cache.filter.expire", "10ms")
                .put("indices.cache.filter.size", "10b")
                .build();
    }

    @Override
    public Settings indexSettings() {
        ImmutableSettings.Builder builder = ImmutableSettings.builder();
        if (randomizeNumberOfShardsAndReplicas()) {
            int numberOfShards = between(4, 10);    // We need to override this so that all nodes have at least one shard
            if (numberOfShards > 0) {
                builder.put(SETTING_NUMBER_OF_SHARDS, numberOfShards).build();
            }
            int numberOfReplicas = numberOfReplicas();
            if (numberOfReplicas >= 0) {
                builder.put(SETTING_NUMBER_OF_REPLICAS, numberOfReplicas).build();
            }
        }
        return builder.build();
    }


    @Test
    public void testFieldDataEvictions() throws Exception {
        createIndex("test");

        NodesStatsResponse nodesStats = client().admin().cluster().prepareNodesStats().setIndices(true).execute().actionGet();

        //Should be no evictions to start
        for (NodeStats n : nodesStats.getNodes()) {
            EvictionStats ev = n.getIndices().getFieldData().getEvictionStats();
            assertThat(ev.getEvictions(), equalTo(0l));
            assertThat(ev.getEvictionsOneMinuteRate(), equalTo(0D));
            assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(0D));
            assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(0D));
        }

        int numDocs = randomIntBetween(500, 5000);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                "field1", English.intToEnglish(i),
                "field2", i
            );
        }

        indexRandom(true, docs);
        ensureGreen();

        // sort to load it to field data...run multiple queries to thrash the evictions
        for (int i = 0; i < 50; i++) {
            client().prepareSearch().addSort("field1", SortOrder.ASC).execute().actionGet();
            client().prepareSearch().addSort("field2", SortOrder.ASC).execute().actionGet();
        }

        waitForEvictions(CacheType.FIELDDATA);

        nodesStats = client().admin().cluster().prepareNodesStats().setIndices(true).execute().actionGet();

        //Should have some more evictions now
        for (NodeStats n : nodesStats.getNodes()) {
            EvictionStats ev = n.getIndices().getFieldData().getEvictionStats();
            assertThat(ev.getEvictions(), greaterThan(0l));
            assertThat(ev.getEvictionsOneMinuteRate(), greaterThan(0D));
            assertThat(ev.getEvictionsFiveMinuteRate(), greaterThan(0D));
            assertThat(ev.getEvictionsFifteenMinuteRate(), greaterThan(0D));
        }
    }

    @Test
    public void testFilterEvictions() throws Exception {
        createIndex("test");

        NodesStatsResponse nodesStats = client().admin().cluster().prepareNodesStats().setIndices(true).execute().actionGet();

        //Should be no evictions to start
        for (NodeStats n : nodesStats.getNodes()) {
            EvictionStats ev = n.getIndices().getFilterCache().getEvictionStats();
            assertThat(ev.getEvictions(), equalTo(0l));
            assertThat(ev.getEvictionsOneMinuteRate(), equalTo(0D));
            assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(0D));
            assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(0D));
        }

        int numDocs = randomIntBetween(500, 1000);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        indexRandom(true, docs);
        ensureGreen();

        // Run some searches to cache and evict filters
        for (int i = 0; i < 100; i++) {
            client().prepareSearch().setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), FilterBuilders.termFilter("field1", English.intToEnglish(i)))).execute().actionGet();
            client().prepareSearch().setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), FilterBuilders.termFilter("field2", i))).execute().actionGet();
        }

        waitForEvictions(CacheType.FILTER);

        nodesStats = client().admin().cluster().prepareNodesStats().setIndices(true).execute().actionGet();

        //Should have some more evictions now
        for (NodeStats n : nodesStats.getNodes()) {
            EvictionStats ev = n.getIndices().getFilterCache().getEvictionStats();
            assertThat(ev.getEvictions(), greaterThan(0l));
            assertThat(ev.getEvictionsOneMinuteRate(), greaterThan(0D));
            assertThat(ev.getEvictionsFiveMinuteRate(), greaterThan(0D));
            assertThat(ev.getEvictionsFifteenMinuteRate(), greaterThan(0D));
        }
    }

    private enum CacheType {
        FILTER, FIELDDATA
    }

    private void waitForEvictions(final CacheType cacheType) throws Exception {
        assertTrue(awaitBusy(new Predicate<Object>() {
            public boolean apply(Object obj) {
                boolean success = true;
                NodesStatsResponse nodesStats = client().admin().cluster().prepareNodesStats().setIndices(true).execute().actionGet();
                EvictionStats ev;
                for (NodeStats n : nodesStats.getNodes()) {
                    if (cacheType == CacheType.FILTER) {
                        ev = n.getIndices().getFilterCache().getEvictionStats();
                    } else {
                        ev = n.getIndices().getFieldData().getEvictionStats();
                    }
                    if (ev.getEvictions() <= 0 || ev.getEvictionsOneMinuteRate() == 0) {
                        success = false;
                        break;
                    }
                }

                return success;
            }
        }, 10, TimeUnit.SECONDS));
    }


}
