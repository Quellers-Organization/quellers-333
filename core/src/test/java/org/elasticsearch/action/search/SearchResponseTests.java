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

package org.elasticsearch.action.search;

import org.elasticsearch.action.search.SearchResponse.InternalSearchResponse;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParserUtils;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.internal.InternalSearchHit;
import org.elasticsearch.search.internal.InternalSearchHits;
import org.elasticsearch.search.internal.InternalSearchHitsTests;
import org.elasticsearch.search.profile.SearchProfileShardResults;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.Collections;

import static org.elasticsearch.common.xcontent.XContentHelper.toXContent;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertToXContentEquivalent;

public class SearchResponseTests extends ESTestCase {

    public static SearchResponse createTestItem() {
        InternalSearchHits hits = InternalSearchHitsTests.createTestItem();
        boolean timedOut = randomBoolean();
        Boolean terminatedEarly = randomBoolean() ? null : randomBoolean();
        int tookInMillis = randomIntBetween(0, 1000);
        int successfulShards = tookInMillis;
        int totalShards = randomInt();
        int numFailures = randomIntBetween(0, 3);
        ShardSearchFailure[] failures = new ShardSearchFailure[numFailures];
        for (int i = 0; i < numFailures; i++) {
            failures[i] = ShardSearchFailureTests.createTestItem();
        }
        // TODO add random aggregations, suggest and profileShardResults once we are able to parse them from xContent
        InternalAggregations aggregations = null;
        Suggest suggest = null;
        SearchProfileShardResults profileShardResults = null;
        return new SearchResponse(new InternalSearchResponse(hits, aggregations, suggest, profileShardResults, timedOut, terminatedEarly),
                null, totalShards, successfulShards, tookInMillis, failures);
    }

    public void testFromXContent() throws IOException {
        SearchResponse response = createTestItem();
        XContentType xcontentType = randomFrom(XContentType.values());
        XContentBuilder builder = XContentFactory.contentBuilder(xcontentType);
        builder = response.toXContent(builder, ToXContent.EMPTY_PARAMS);

        XContentParser parser = createParser(builder);
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser::getTokenLocation);
        SearchResponse parsed = SearchResponse.fromXContent(parser);

        // the "_shard/total/failures" section makes if impossible to directly compare xContent, because
        // the failures in the parsed SearchResponse are wrapped in an extra ElasticSearchException on the client side.
        // Because of this we compare the "top level" fields for equality and the subsections xContent equivalence independently
        assertEquals(response.getScrollId(), parsed.getScrollId());
        assertEquals(response.getTookInMillis(), parsed.getTookInMillis());
        assertEquals(response.getTook(), parsed.getTook());
        assertEquals(response.isTimedOut(), parsed.isTimedOut());
        assertEquals(response.isTerminatedEarly(), parsed.isTerminatedEarly());
        assertEquals(response.getFailedShards(), parsed.getFailedShards());
        assertEquals(response.getSuccessfulShards(), parsed.getSuccessfulShards());
        assertEquals(response.getTotalShards(), parsed.getTotalShards());

        // compare the "hits" section by comparing xContent equivalence
        assertToXContentEquivalent(toXContent(response.getHits(), xcontentType), toXContent(parsed.getHits(), xcontentType),
                xcontentType);
        assertEquals(XContentParser.Token.END_OBJECT, parser.currentToken());
        assertNull(parser.nextToken());
    }

    public void testToXContent() throws IOException {
        InternalSearchHit hit = new InternalSearchHit(1, "id1", new Text("type"), Collections.emptyMap());
        hit.score(2.0f);
        InternalSearchHit[] hits = new InternalSearchHit[] { hit };
        SearchResponse response = new SearchResponse(
                new InternalSearchResponse(new InternalSearchHits(hits, 100, 1.5f), null, null, null, false, null), null, 0, 0, 0,
                new ShardSearchFailure[0]);
        BytesReference xContent = toXContent(response, XContentType.JSON);
        assertEquals(
                "{\"took\":0,"
                + "\"timed_out\":false,"
                + "\"_shards\":"
                    + "{\"total\":0,"
                    + "\"successful\":0,"
                    + "\"failed\":0"
                + "},"
                + "\"hits\":"
                    + "{\"total\":100,"
                    + "\"max_score\":1.5,"
                    + "\"hits\":[{\"_type\":\"type\",\"_id\":\"id1\",\"_score\":2.0}]"
                    + "}"
                + "}", xContent.utf8ToString());
    }

}
