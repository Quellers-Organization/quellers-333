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

package org.elasticsearch.index.rankeval;

import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ParseFieldRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.SearchRequestParsers;
import org.elasticsearch.search.aggregations.AggregatorParsers;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.suggest.Suggesters;
import org.elasticsearch.test.ESTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;

public class RankEvalSpecTests extends ESTestCase {
    private static SearchModule searchModule;
    private static SearchRequestParsers searchRequestParsers;

    /**
    * setup for the whole base test class
    */
    @BeforeClass
    public static void init() {
        AggregatorParsers aggsParsers = new AggregatorParsers(new ParseFieldRegistry<>("aggregation"),
                new ParseFieldRegistry<>("aggregation_pipes"));
        searchModule = new SearchModule(Settings.EMPTY, false, emptyList());
        IndicesQueriesRegistry queriesRegistry = searchModule.getQueryParserRegistry();
        Suggesters suggesters = searchModule.getSuggesters();
        searchRequestParsers = new SearchRequestParsers(queriesRegistry, aggsParsers, suggesters, null);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        searchModule = null;
        searchRequestParsers = null;
    }

    private static <T> List<T> randomList(Supplier<T> randomSupplier) {
        List<T> result = new ArrayList<>();
        int size = randomIntBetween(1, 20);
        for (int i = 0; i < size; i++) {
            result.add(randomSupplier.get());
        }
        return result;
    }

    private RankEvalSpec createTestItem() throws IOException {
        RankedListQualityMetric metric;
        if (randomBoolean()) {
            metric = PrecisionTests.createTestItem();
        } else {
            metric = DiscountedCumulativeGainTests.createTestItem();
        }

        Script template = null;
        List<RatedRequest> ratedRequests = null;
        if (randomBoolean()) {
            final Map<String, Object> params = randomBoolean() ? Collections.emptyMap() : Collections.singletonMap("key", "value");
            ScriptType scriptType = randomFrom(ScriptType.values());
            String script;
            if (scriptType == ScriptType.INLINE) {
                try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
                    builder.startObject();
                    builder.field("field", randomAsciiOfLengthBetween(1, 5));
                    builder.endObject();
                    script = builder.string();
                }
            } else {
                script = randomAsciiOfLengthBetween(1, 5);
            }

            template = new Script(scriptType, randomFrom("_lang1", "_lang2"), script, params);

            Map<String, Object> templateParams = new HashMap<>();
            templateParams.put("key", "value");
            RatedRequest ratedRequest = new RatedRequest(
                    "id", Arrays.asList(RatedDocumentTests.createRatedDocument()), templateParams);
            ratedRequests = Arrays.asList(ratedRequest);
        } else {
            RatedRequest ratedRequest = new RatedRequest(
                    "id", Arrays.asList(RatedDocumentTests.createRatedDocument()), new SearchSourceBuilder());
            ratedRequests = Arrays.asList(ratedRequest);
        }
        RankEvalSpec spec = new RankEvalSpec(ratedRequests, metric, template); 
        maybeSet(spec::setMaxConcurrentSearches, randomInt(100));
        return spec;
    }

    public void testRoundtripping() throws IOException {
        RankEvalSpec testItem = createTestItem();

        XContentBuilder shuffled = ESTestCase.shuffleXContent(testItem.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS));
        XContentParser itemParser = XContentHelper.createParser(shuffled.bytes());

        QueryParseContext queryContext = new QueryParseContext(searchRequestParsers.queryParsers, itemParser, ParseFieldMatcher.STRICT);
        RankEvalContext rankContext = new RankEvalContext(ParseFieldMatcher.STRICT, queryContext,
                searchRequestParsers, null);

        RankEvalSpec parsedItem = RankEvalSpec.parse(itemParser, rankContext);
        // IRL these come from URL parameters - see RestRankEvalAction
        // TODO Do we still need this? parsedItem.getRatedRequests().stream().forEach(e -> {e.setIndices(indices); e.setTypes(types);});
        assertNotSame(testItem, parsedItem);
        assertEquals(testItem, parsedItem);
        assertEquals(testItem.hashCode(), parsedItem.hashCode());
    }

    public void testMissingRatedRequestsFailsParsing() {
        RankedListQualityMetric metric = new Precision();
        expectThrows(IllegalStateException.class, () -> new RankEvalSpec(new ArrayList<>(), metric));
        expectThrows(IllegalStateException.class, () -> new RankEvalSpec(null, metric));
    }
    
    public void testMissingMetricFailsParsing() {
        List<String> strings = Arrays.asList("value");
        List<RatedRequest> ratedRequests = randomList(() -> RatedRequestsTests.createTestItem(strings, strings));
        expectThrows(IllegalStateException.class, () -> new RankEvalSpec(ratedRequests, null));
    }

    public void testMissingTemplateAndSearchRequestFailsParsing() {
        List<RatedDocument> ratedDocs = Arrays.asList(new RatedDocument(new DocumentKey("index1", "type1", "id1"), 1));
        Map<String, Object> params = new HashMap<>();
        params.put("key", "value");

        RatedRequest request = new RatedRequest("id", ratedDocs, params);
        List<RatedRequest> ratedRequests = Arrays.asList(request);
        
        expectThrows(IllegalStateException.class, () -> new RankEvalSpec(ratedRequests, new Precision()));
    }
}
