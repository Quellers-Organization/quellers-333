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

package org.elasticsearch.search.aggregations.bucket;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.test.ESIntegTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.is;

public abstract class ShardSizeTestCase extends ESIntegTestCase {

    @Override
    protected int numberOfShards() {
        // we need at least 2
        return randomIntBetween(2, DEFAULT_MAX_NUM_SHARDS);
    }

    protected void createIdx(String keyFieldMapping) {
        assertAcked(prepareCreate("idx")
                .addMapping("type", "key", keyFieldMapping));
    }

    protected static String routing1; // routing key to shard 1
    protected static String routing2; // routing key to shard 2

    protected void indexData() throws Exception {

        /*


        ||          ||           size = 3, shard_size = 5               ||           shard_size = size = 3               ||
        ||==========||==================================================||===============================================||
        || shard 1: ||  "1" - 5 | "2" - 4 | "3" - 3 | "4" - 2 | "5" - 1 || "1" - 5 | "3" - 3 | "2" - 4                   ||
        ||----------||--------------------------------------------------||-----------------------------------------------||
        || shard 2: ||  "1" - 3 | "2" - 1 | "3" - 5 | "4" - 2 | "5" - 1 || "1" - 3 | "3" - 5 | "4" - 2                   ||
        ||----------||--------------------------------------------------||-----------------------------------------------||
        || reduced: ||  "1" - 8 | "2" - 5 | "3" - 8 | "4" - 4 | "5" - 2 ||                                               ||
        ||          ||                                                  || "1" - 8, "3" - 8, "2" - 4    <= WRONG         ||
        ||          ||  "1" - 8 | "3" - 8 | "2" - 5     <= CORRECT      ||                                               ||


        */

        List<IndexRequestBuilder> docs = new ArrayList<>();

        routing1 = routingKeyForShard("idx", 0);
        routing2 = routingKeyForShard("idx", 1);

        docs.addAll(indexDoc(routing1, "1", 5));
        docs.addAll(indexDoc(routing1, "2", 4));
        docs.addAll(indexDoc(routing1, "3", 3));
        docs.addAll(indexDoc(routing1, "4", 2));
        docs.addAll(indexDoc(routing1, "5", 1));

        // total docs in shard "1" = 15

        docs.addAll(indexDoc(routing2, "1", 3));
        docs.addAll(indexDoc(routing2, "2", 1));
        docs.addAll(indexDoc(routing2, "3", 5));
        docs.addAll(indexDoc(routing2, "4", 2));
        docs.addAll(indexDoc(routing2, "5", 1));

        // total docs in shard "2"  = 12

        indexRandom(true, docs);

        SearchResponse resp = client().prepareSearch("idx").setTypes("type").setRouting(routing1).setQuery(matchAllQuery()).get();
        assertSearchResponse(resp);
        long totalOnOne = resp.getHits().getTotalHits();
        assertThat(totalOnOne, is(15L));
        resp = client().prepareSearch("idx").setTypes("type").setRouting(routing2).setQuery(matchAllQuery()).get();
        assertSearchResponse(resp);
        long totalOnTwo = resp.getHits().getTotalHits();
        assertThat(totalOnTwo, is(12L));
    }

    protected List<IndexRequestBuilder> indexDoc(String shard, String key, int times) throws Exception {
        IndexRequestBuilder[] builders = new IndexRequestBuilder[times];
        for (int i = 0; i < times; i++) {
            builders[i] = client().prepareIndex("idx", "type").setRouting(shard).setSource(jsonBuilder()
                    .startObject()
                    .field("key", key)
                    .field("value", 1)
                    .endObject());
        }
        return Arrays.asList(builders);
    }
}
