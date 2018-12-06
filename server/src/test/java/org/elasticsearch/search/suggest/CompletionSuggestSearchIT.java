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
package org.elasticsearch.search.suggest;

import com.carrotsearch.randomizedtesting.generators.RandomStrings;
import org.apache.lucene.analysis.TokenStreamToAutomaton;
import org.apache.lucene.search.suggest.document.ContextSuggestField;
import org.apache.lucene.util.LuceneTestCase.SuppressCodecs;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeResponse;
import org.elasticsearch.action.admin.indices.segments.IndexShardSegments;
import org.elasticsearch.action.admin.indices.segments.ShardSegments;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.common.FieldMemoryStats;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregator.SubAggCollectionMode;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.suggest.completion.CompletionStats;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder;
import org.elasticsearch.search.suggest.completion.FuzzyOptions;
import org.elasticsearch.search.suggest.completion.context.CategoryContextMapping;
import org.elasticsearch.search.suggest.completion.context.ContextMapping;
import org.elasticsearch.search.suggest.completion.context.GeoContextMapping;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.InternalSettingsPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.common.util.CollectionUtils.iterableAsArrayList;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAllSuccessful;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasId;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasScore;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@SuppressCodecs("*") // requires custom completion format
public class CompletionSuggestSearchIT extends ESIntegTestCase {
    private final String INDEX = RandomStrings.randomAsciiOfLength(random(), 10).toLowerCase(Locale.ROOT);
    private final String TYPE = RandomStrings.randomAsciiOfLength(random(), 10).toLowerCase(Locale.ROOT);
    private final String FIELD = RandomStrings.randomAsciiOfLength(random(), 10).toLowerCase(Locale.ROOT);
    private final CompletionMappingBuilder completionMappingBuilder = new CompletionMappingBuilder();

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(InternalSettingsPlugin.class);
    }

    public void testTieBreak() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        mapping.indexAnalyzer("keyword");
        createIndexAndMapping(mapping);

        int numDocs = randomIntBetween(3, 50);
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        String[] entries = new String[numDocs];
        for (int i = 0; i < numDocs; i++) {
            String value = "a" + randomAlphaOfLengthBetween(1, 10);
            entries[i] = value;
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                .setSource(jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", value)
                    .field("weight", 10)
                    .endObject()
                    .endObject()
                ));
        }
        Arrays.sort(entries);
        indexRandom(true, indexRequestBuilders);
        for (int i = 1; i < numDocs; i++) {
            CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD)
                .prefix("a")
                .size(i);
            String[] topEntries = Arrays.copyOfRange(entries, 0, i);
            assertSuggestions("foo", prefix, topEntries);
        }
    }

    public void testPrefix() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 1; i <= numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(jsonBuilder()
                                    .startObject()
                                    .startObject(FIELD)
                                    .field("input", "suggestion" + i)
                                    .field("weight", i)
                                    .endObject()
                                    .endObject()
                    ));
        }
        indexRandom(true, indexRequestBuilders);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg");
        assertSuggestions("foo", prefix, "suggestion10", "suggestion9", "suggestion8", "suggestion7", "suggestion6");
    }

    /**
     * test that suggestion works if prefix is either provided via {@link CompletionSuggestionBuilder#text(String)} or
     * {@link SuggestBuilder#setGlobalText(String)}
     */
    public void testTextAndGlobalText() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 1; i <= numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i).setSource(jsonBuilder().startObject().startObject(FIELD)
                    .field("input", "suggestion" + i).field("weight", i).endObject().endObject()));
        }
        indexRandom(true, indexRequestBuilders);
        CompletionSuggestionBuilder noText = SuggestBuilders.completionSuggestion(FIELD);
        SearchResponse searchResponse = client().prepareSearch(INDEX)
                .suggest(new SuggestBuilder().addSuggestion("foo", noText).setGlobalText("sugg")).get();
        assertSuggestions(searchResponse, "foo", "suggestion10", "suggestion9", "suggestion8", "suggestion7", "suggestion6");

        CompletionSuggestionBuilder withText = SuggestBuilders.completionSuggestion(FIELD).text("sugg");
        searchResponse = client().prepareSearch(INDEX)
                .suggest(new SuggestBuilder().addSuggestion("foo", withText)).get();
        assertSuggestions(searchResponse, "foo", "suggestion10", "suggestion9", "suggestion8", "suggestion7", "suggestion6");

        // test that suggestion text takes precedence over global text
        searchResponse = client().prepareSearch(INDEX)
                .suggest(new SuggestBuilder().addSuggestion("foo", withText).setGlobalText("bogus")).get();
        assertSuggestions(searchResponse, "foo", "suggestion10", "suggestion9", "suggestion8", "suggestion7", "suggestion6");
    }

    public void testRegex() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 1; i <= numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(jsonBuilder()
                                    .startObject()
                                    .startObject(FIELD)
                                    .field("input", "sugg" + i + "estion")
                                    .field("weight", i)
                                    .endObject()
                                    .endObject()
                    ));
        }
        indexRandom(true, indexRequestBuilders);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).regex("sugg.*es");
        assertSuggestions("foo", prefix, "sugg10estion", "sugg9estion", "sugg8estion", "sugg7estion", "sugg6estion");
    }

    public void testFuzzy() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 1; i <= numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(jsonBuilder()
                                    .startObject()
                                    .startObject(FIELD)
                                    .field("input", "sugxgestion" + i)
                                    .field("weight", i)
                                    .endObject()
                                    .endObject()
                    ));
        }
        indexRandom(true, indexRequestBuilders);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg", Fuzziness.ONE);
        assertSuggestions("foo", prefix, "sugxgestion10", "sugxgestion9", "sugxgestion8", "sugxgestion7", "sugxgestion6");
    }

    public void testEarlyTermination() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        createIndexAndMapping(mapping);
        int numDocs = atLeast(100);
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                    .setSource(jsonBuilder()
                                    .startObject()
                                    .startObject(FIELD)
                                    .field("input", "suggestion" + (numDocs - i))
                                    .field("weight", numDocs - i)
                                    .endObject()
                                    .endObject()
                    ));
        }
        indexRandom(true, indexRequestBuilders);
        int size = randomIntBetween(3, 10);
        String[] outputs = new String[size];
        for (int i = 0; i < size; i++) {
            outputs[i] = "suggestion" + (numDocs - i);
        }
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sug").size(size);
        assertSuggestions("foo", prefix, outputs);

        CompletionSuggestionBuilder regex = SuggestBuilders.completionSuggestion(FIELD).regex("su[g|s]g").size(size);
        assertSuggestions("foo", regex, outputs);

        CompletionSuggestionBuilder fuzzyPrefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg", Fuzziness.ONE).size(size);
        assertSuggestions("foo", fuzzyPrefix, outputs);
    }

    public void testSuggestDocument() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        createIndexAndMapping(mapping);
        int numDocs = randomIntBetween(10, 100);
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 1; i <= numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                .setSource(jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i)
                    .endObject()
                    .endObject()
                ));
        }
        indexRandom(true, indexRequestBuilders);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg").size(numDocs);

        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(new SuggestBuilder().addSuggestion("foo", prefix)).get();
        CompletionSuggestion completionSuggestion = searchResponse.getSuggest().getSuggestion("foo");
        CompletionSuggestion.Entry options = completionSuggestion.getEntries().get(0);
        assertThat(options.getOptions().size(), equalTo(numDocs));
        int id = numDocs;
        for (CompletionSuggestion.Entry.Option option : options) {
            assertThat(option.getText().toString(), equalTo("suggestion" + id));
            assertThat(option.getHit(), hasId("" + id));
            assertThat(option.getHit(), hasScore((id)));
            assertNotNull(option.getHit().getSourceAsMap());
            id--;
        }
    }

    public void testSuggestDocumentNoSource() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        createIndexAndMapping(mapping);
        int numDocs = randomIntBetween(10, 100);
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 1; i <= numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                .setSource(jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i)
                    .endObject()
                    .endObject()
                ));
        }
        indexRandom(true, indexRequestBuilders);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg").size(numDocs);

        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo", prefix)
        ).setFetchSource(false).get();
        CompletionSuggestion completionSuggestion = searchResponse.getSuggest().getSuggestion("foo");
        CompletionSuggestion.Entry options = completionSuggestion.getEntries().get(0);
        assertThat(options.getOptions().size(), equalTo(numDocs));
        int id = numDocs;
        for (CompletionSuggestion.Entry.Option option : options) {
            assertThat(option.getText().toString(), equalTo("suggestion" + id));
            assertThat(option.getHit(), hasId("" + id));
            assertThat(option.getHit(), hasScore((id)));
            assertNull(option.getHit().getSourceAsMap());
            id--;
        }
    }

    public void testSuggestDocumentSourceFiltering() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        createIndexAndMapping(mapping);
        int numDocs = randomIntBetween(10, 100);
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 1; i <= numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                .setSource(jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .field("input", "suggestion" + i)
                    .field("weight", i)
                    .endObject()
                    .field("a", "include")
                    .field("b", "exclude")
                    .endObject()
                ));
        }
        indexRandom(true, indexRequestBuilders);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg").size(numDocs);

        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo", prefix)
        ).setFetchSource("a", "b").get();
        CompletionSuggestion completionSuggestion = searchResponse.getSuggest().getSuggestion("foo");
        CompletionSuggestion.Entry options = completionSuggestion.getEntries().get(0);
        assertThat(options.getOptions().size(), equalTo(numDocs));
        int id = numDocs;
        for (CompletionSuggestion.Entry.Option option : options) {
            assertThat(option.getText().toString(), equalTo("suggestion" + id));
            assertThat(option.getHit(), hasId("" + id));
            assertThat(option.getHit(), hasScore((id)));
            assertNotNull(option.getHit().getSourceAsMap());
            Set<String> sourceFields = option.getHit().getSourceAsMap().keySet();
            assertThat(sourceFields, contains("a"));
            assertThat(sourceFields, not(contains("b")));
            id--;
        }
    }

    public void testThatWeightsAreWorking() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        List<String> similarNames = Arrays.asList("the", "The Prodigy", "The Verve", "The the");
        // the weight is 1000 divided by string length, so the results are easy to to check
        for (String similarName : similarNames) {
            client().prepareIndex(INDEX, TYPE, similarName).setSource(jsonBuilder()
                    .startObject().startObject(FIELD)
                    .startArray("input").value(similarName).endArray()
                    .field("weight", 1000 / similarName.length())
                    .endObject().endObject()
            ).get();
        }

        refresh();

        assertSuggestions("the", "the", "The the", "The Verve", "The Prodigy");
    }

    public void testThatWeightMustBeAnInteger() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        try {
            client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                    .startObject().startObject(FIELD)
                    .startArray("input").value("sth").endArray()
                    .field("weight", 2.5)
                    .endObject().endObject()
            ).get();
            fail("Indexing with a float weight was successful, but should not be");
        } catch (MapperParsingException e) {
            assertThat(e.toString(), containsString("2.5"));
        }
    }

    public void testThatWeightCanBeAString() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                        .startObject().startObject(FIELD)
                        .startArray("input").value("testing").endArray()
                        .field("weight", "10")
                        .endObject().endObject()
        ).get();

        refresh();

        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("testSuggestions", new CompletionSuggestionBuilder(FIELD).text("test").size(10))
        ).get();

        assertSuggestions(searchResponse, "testSuggestions", "testing");
        Suggest.Suggestion.Entry.Option option = searchResponse.getSuggest().getSuggestion("testSuggestions").getEntries().get(0)
                .getOptions().get(0);
        assertThat(option, is(instanceOf(CompletionSuggestion.Entry.Option.class)));
        CompletionSuggestion.Entry.Option prefixOption = (CompletionSuggestion.Entry.Option) option;

        assertThat(prefixOption.getText().string(), equalTo("testing"));
        assertThat((long) prefixOption.getScore(), equalTo(10L));
    }


    public void testThatWeightMustNotBeANonNumberString() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        try {
            client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                            .startObject().startObject(FIELD)
                            .startArray("input").value("sth").endArray()
                            .field("weight", "thisIsNotValid")
                            .endObject().endObject()
            ).get();
            fail("Indexing with a non-number representing string as weight was successful, but should not be");
        } catch (MapperParsingException e) {
            assertThat(e.toString(), containsString("thisIsNotValid"));
        }
    }

    public void testThatWeightAsStringMustBeInt() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        String weight = String.valueOf(Long.MAX_VALUE - 4);
        try {
            client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                            .startObject().startObject(FIELD)
                            .startArray("input").value("testing").endArray()
                            .field("weight", weight)
                            .endObject().endObject()
            ).get();
            fail("Indexing with weight string representing value > Int.MAX_VALUE was successful, but should not be");
        } catch (MapperParsingException e) {
            assertThat(e.toString(), containsString(weight));
        }
    }

    public void testThatInputCanBeAStringInsteadOfAnArray() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                        .startObject().startObject(FIELD)
                        .field("input", "Foo Fighters")
                        .endObject().endObject()
        ).get();

        refresh();

        assertSuggestions("f", "Foo Fighters");
    }

    public void testDisabledPreserveSeparators() throws Exception {
        completionMappingBuilder.preserveSeparators(false);
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Foo Fighters").endArray()
                .field("weight", 10)
                .endObject().endObject()
        ).get();

        client().prepareIndex(INDEX, TYPE, "2").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Foof").endArray()
                .field("weight", 20)
                .endObject().endObject()
        ).get();

        refresh();

        assertSuggestions("foof", "Foof", "Foo Fighters");
    }

    public void testEnabledPreserveSeparators() throws Exception {
        completionMappingBuilder.preserveSeparators(true);
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Foo Fighters").endArray()
                .endObject().endObject()
        ).get();

        client().prepareIndex(INDEX, TYPE, "2").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Foof").endArray()
                .endObject().endObject()
        ).get();

        refresh();

        assertSuggestions("foof", "Foof");
    }

    public void testThatMultipleInputsAreSupported() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Foo Fighters").value("Fu Fighters").endArray()
                .endObject().endObject()
        ).get();

        refresh();

        assertSuggestions("foo", "Foo Fighters");
        assertSuggestions("fu", "Fu Fighters");
    }

    public void testThatShortSyntaxIsWorking() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startArray(FIELD)
                .value("The Prodigy Firestarter").value("Firestarter")
                .endArray().endObject()
        ).get();

        refresh();

        assertSuggestions("t", "The Prodigy Firestarter");
        assertSuggestions("f", "Firestarter");
    }

    public void testThatDisablingPositionIncrementsWorkForStopwords() throws Exception {
        // analyzer which removes stopwords... so may not be the simple one
        completionMappingBuilder.searchAnalyzer("classic").indexAnalyzer("classic").preservePositionIncrements(false);
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("The Beatles").endArray()
                .endObject().endObject()
        ).get();

        refresh();

        assertSuggestions("b", "The Beatles");
    }

    public void testThatSynonymsWork() throws Exception {
        Settings.Builder settingsBuilder = Settings.builder()
                .put("analysis.analyzer.suggest_analyzer_synonyms.type", "custom")
                .put("analysis.analyzer.suggest_analyzer_synonyms.tokenizer", "standard")
                .putList("analysis.analyzer.suggest_analyzer_synonyms.filter", "standard", "lowercase", "my_synonyms")
                .put("analysis.filter.my_synonyms.type", "synonym")
                .putList("analysis.filter.my_synonyms.synonyms", "foo,renamed");
        completionMappingBuilder.searchAnalyzer("suggest_analyzer_synonyms").indexAnalyzer("suggest_analyzer_synonyms");
        createIndexAndMappingAndSettings(settingsBuilder.build(), completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Foo Fighters").endArray()
                .endObject().endObject()
        ).get();

        refresh();

        // get suggestions for renamed
        assertSuggestions("r", "Foo Fighters");
    }

    public void testThatUpgradeToMultiFieldsWorks() throws Exception {
        final XContentBuilder mapping = jsonBuilder()
                .startObject()
                .startObject(TYPE)
                .startObject("properties")
                .startObject(FIELD)
                .field("type", "text")
                .endObject()
                .endObject()
                .endObject()
                .endObject();
        assertAcked(prepareCreate(INDEX).addMapping(TYPE, mapping));
        client().prepareIndex(INDEX, TYPE, "1").setRefreshPolicy(IMMEDIATE)
                .setSource(jsonBuilder().startObject().field(FIELD, "Foo Fighters").endObject()).get();
        ensureGreen(INDEX);

        AcknowledgedResponse putMappingResponse = client().admin().indices().preparePutMapping(INDEX).setType(TYPE)
                .setSource(jsonBuilder().startObject()
                .startObject(TYPE).startObject("properties")
                .startObject(FIELD)
                .field("type", "text")
                .startObject("fields")
                .startObject("suggest").field("type", "completion").field("analyzer", "simple").endObject()
                .endObject()
                .endObject()
                .endObject().endObject()
                .endObject())
                .get();
        assertThat(putMappingResponse.isAcknowledged(), is(true));

        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("suggs", SuggestBuilders.completionSuggestion(FIELD + ".suggest").text("f").size(10))
        ).get();
        assertSuggestions(searchResponse, "suggs");

        client().prepareIndex(INDEX, TYPE, "1").setRefreshPolicy(IMMEDIATE)
                .setSource(jsonBuilder().startObject().field(FIELD, "Foo Fighters").endObject()).get();
        ensureGreen(INDEX);

        SearchResponse afterReindexingResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("suggs", SuggestBuilders.completionSuggestion(FIELD + ".suggest").text("f").size(10))
        ).get();
        assertSuggestions(afterReindexingResponse, "suggs", "Foo Fighters");
    }

    public void testThatFuzzySuggesterWorks() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Nirvana").endArray()
                .endObject().endObject()
        ).get();

        refresh();

        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo", SuggestBuilders.completionSuggestion(FIELD).prefix("Nirv").size(10))
        ).get();
        assertSuggestions(searchResponse, false, "foo", "Nirvana");

        searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo", SuggestBuilders.completionSuggestion(FIELD).prefix("Nirw", Fuzziness.ONE).size(10))
        ).get();
        assertSuggestions(searchResponse, false, "foo", "Nirvana");
    }

    public void testThatFuzzySuggesterSupportsEditDistances() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Nirvana").endArray()
                .endObject().endObject()
        ).get();

        refresh();

        // edit distance 1
        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo", SuggestBuilders.completionSuggestion(FIELD).prefix("Norw", Fuzziness.ONE).size(10))
        ).get();
        assertSuggestions(searchResponse, false, "foo");

        // edit distance 2
        searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo", SuggestBuilders.completionSuggestion(FIELD).prefix("Norw", Fuzziness.TWO).size(10))
        ).get();
        assertSuggestions(searchResponse, false, "foo", "Nirvana");
    }

    public void testThatFuzzySuggesterSupportsTranspositions() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Nirvana").endArray()
                .endObject().endObject()
        ).get();

        refresh();

        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo",
                SuggestBuilders.completionSuggestion(FIELD).prefix("Nriv", FuzzyOptions.builder().setTranspositions(false).build())
                .size(10))
        ).get();
        assertSuggestions(searchResponse, false, "foo");

        searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo", SuggestBuilders.completionSuggestion(FIELD).prefix("Nriv", Fuzziness.ONE).size(10))
        ).get();
        assertSuggestions(searchResponse, false, "foo", "Nirvana");
    }

    public void testThatFuzzySuggesterSupportsMinPrefixLength() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Nirvana").endArray()
                .endObject().endObject()
        ).get();

        refresh();

        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo",
                SuggestBuilders.completionSuggestion(FIELD).prefix("Nriva", FuzzyOptions.builder().setFuzzyMinLength(6).build()).size(10))
        ).get();
        assertSuggestions(searchResponse, false, "foo");

        searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo",
                SuggestBuilders.completionSuggestion(FIELD).prefix("Nrivan", FuzzyOptions.builder().setFuzzyMinLength(6).build()).size(10))
        ).get();
        assertSuggestions(searchResponse, false, "foo", "Nirvana");
    }

    public void testThatFuzzySuggesterSupportsNonPrefixLength() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Nirvana").endArray()
                .endObject().endObject()
        ).get();

        refresh();

        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo",
                SuggestBuilders.completionSuggestion(FIELD).prefix("Nirw", FuzzyOptions.builder().setFuzzyPrefixLength(4).build()).size(10))
        ).get();
        assertSuggestions(searchResponse, false, "foo");

        searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion("foo",
                SuggestBuilders.completionSuggestion(FIELD).prefix("Nirvo", FuzzyOptions.builder().setFuzzyPrefixLength(4).build())
                .size(10))
        ).get();
        assertSuggestions(searchResponse, false, "foo", "Nirvana");
    }

    public void testThatFuzzySuggesterIsUnicodeAware() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("ööööö").endArray()
                .endObject().endObject()
        ).get();

        refresh();

        // suggestion with a character, which needs unicode awareness
        org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder completionSuggestionBuilder =
                SuggestBuilders.completionSuggestion(FIELD).prefix("öööи", FuzzyOptions.builder().setUnicodeAware(true).build()).size(10);

        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(new SuggestBuilder()
                .addSuggestion("foo", completionSuggestionBuilder)).get();
        assertSuggestions(searchResponse, false, "foo", "ööööö");

        // removing unicode awareness leads to no result
        completionSuggestionBuilder = SuggestBuilders.completionSuggestion(FIELD)
                .prefix("öööи", FuzzyOptions.builder().setUnicodeAware(false).build()).size(10);
        searchResponse = client().prepareSearch(INDEX).suggest(new SuggestBuilder()
                .addSuggestion("foo", completionSuggestionBuilder)).get();
        assertSuggestions(searchResponse, false, "foo");

        // increasing edit distance instead of unicode awareness works again, as this is only a single character
        completionSuggestionBuilder = SuggestBuilders.completionSuggestion(FIELD)
                .prefix("öööи", FuzzyOptions.builder().setUnicodeAware(false).setFuzziness(Fuzziness.TWO).build()).size(10);
        searchResponse = client().prepareSearch(INDEX).suggest(new SuggestBuilder()
                .addSuggestion("foo", completionSuggestionBuilder)).get();
        assertSuggestions(searchResponse, false, "foo", "ööööö");
    }

    public void testThatStatsAreWorking() throws Exception {
        String otherField = "testOtherField";
        client().admin().indices().prepareCreate(INDEX)
                .setSettings(Settings.builder().put("index.number_of_replicas", 0).put("index.number_of_shards", 2))
                .get();
        ensureGreen();
        AcknowledgedResponse putMappingResponse = client().admin().indices().preparePutMapping(INDEX).setType(TYPE)
                .setSource(jsonBuilder().startObject()
                .startObject(TYPE).startObject("properties")
                .startObject(FIELD)
                .field("type", "completion").field("analyzer", "simple")
                .endObject()
                .startObject(otherField)
                .field("type", "completion").field("analyzer", "simple")
                .endObject()
                .endObject().endObject().endObject())
                .get();
        assertThat(putMappingResponse.isAcknowledged(), is(true));

        // Index two entities
        client().prepareIndex(INDEX, TYPE, "1")
            .setSource(jsonBuilder().startObject().field(FIELD, "Foo Fighters").field(otherField, "WHATEVER").endObject()).get();
        client().prepareIndex(INDEX, TYPE, "2")
            .setSource(jsonBuilder().startObject().field(FIELD, "Bar Fighters").field(otherField, "WHATEVER2").endObject()).get();

        refresh();
        ensureGreen();
        // load the fst index into ram
        client().prepareSearch(INDEX).suggest(new SuggestBuilder()
                .addSuggestion("foo", SuggestBuilders.completionSuggestion(FIELD).prefix("f"))).get();
        client().prepareSearch(INDEX).suggest(new SuggestBuilder()
                .addSuggestion("foo", SuggestBuilders.completionSuggestion(otherField).prefix("f"))).get();

        // Get all stats
        IndicesStatsResponse indicesStatsResponse = client().admin().indices().prepareStats(INDEX).setIndices(INDEX).setCompletion(true)
                .get();
        CompletionStats completionStats = indicesStatsResponse.getIndex(INDEX).getPrimaries().completion;
        assertThat(completionStats, notNullValue());
        long totalSizeInBytes = completionStats.getSizeInBytes();
        assertThat(totalSizeInBytes, is(greaterThan(0L)));

        IndicesStatsResponse singleFieldStats = client().admin().indices().prepareStats(INDEX).setIndices(INDEX).setCompletion(true)
                .setCompletionFields(FIELD).get();
        long singleFieldSizeInBytes = singleFieldStats.getIndex(INDEX).getPrimaries().completion.getFields().get(FIELD);
        IndicesStatsResponse otherFieldStats = client().admin().indices().prepareStats(INDEX).setIndices(INDEX).setCompletion(true)
                .setCompletionFields(otherField).get();
        long otherFieldSizeInBytes = otherFieldStats.getIndex(INDEX).getPrimaries().completion.getFields().get(otherField);
        assertThat(singleFieldSizeInBytes + otherFieldSizeInBytes, is(totalSizeInBytes));

        // regexes
        IndicesStatsResponse regexFieldStats = client().admin().indices().prepareStats(INDEX).setIndices(INDEX).setCompletion(true)
                .setCompletionFields("*").get();
        FieldMemoryStats fields = regexFieldStats.getIndex(INDEX).getPrimaries().completion.getFields();
        long regexSizeInBytes = fields.get(FIELD) + fields.get(otherField);
        assertThat(regexSizeInBytes, is(totalSizeInBytes));
    }

    public void testThatSortingOnCompletionFieldReturnsUsefulException() throws Exception {
        createIndexAndMapping(completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Nirvana").endArray()
                .endObject().endObject()
        ).get();

        refresh();
        try {
            client().prepareSearch(INDEX).setTypes(TYPE).addSort(new FieldSortBuilder(FIELD)).get();
            fail("Expected an exception due to trying to sort on completion field, but did not happen");
        } catch (SearchPhaseExecutionException e) {
            assertThat(e.status().getStatus(), is(400));
            assertThat(e.toString(), containsString("Fielddata is not supported on field [" + FIELD + "] of type [completion]"));
        }
    }

    public void testThatSuggestStopFilterWorks() throws Exception {
        Settings.Builder settingsBuilder = Settings.builder()
                .put("index.analysis.analyzer.stoptest.tokenizer", "standard")
                .putList("index.analysis.analyzer.stoptest.filter", "standard", "suggest_stop_filter")
                .put("index.analysis.filter.suggest_stop_filter.type", "stop")
                .put("index.analysis.filter.suggest_stop_filter.remove_trailing", false);

        CompletionMappingBuilder completionMappingBuilder = new CompletionMappingBuilder();
        completionMappingBuilder.preserveSeparators(true).preservePositionIncrements(true);
        completionMappingBuilder.searchAnalyzer("stoptest");
        completionMappingBuilder.indexAnalyzer("simple");
        createIndexAndMappingAndSettings(settingsBuilder.build(), completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Feed trolls").endArray()
                .field("weight", 5).endObject().endObject()
        ).get();

        // Higher weight so it's ranked first:
        client().prepareIndex(INDEX, TYPE, "2").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("Feed the trolls").endArray()
                .field("weight", 10).endObject().endObject()
        ).get();

        refresh();

        assertSuggestions("f", "Feed the trolls", "Feed trolls");
        assertSuggestions("fe", "Feed the trolls", "Feed trolls");
        assertSuggestions("fee", "Feed the trolls", "Feed trolls");
        assertSuggestions("feed", "Feed the trolls", "Feed trolls");
        assertSuggestions("feed t", "Feed the trolls", "Feed trolls");
        assertSuggestions("feed the", "Feed the trolls");
        // stop word complete, gets ignored on query time, makes it "feed" only
        assertSuggestions("feed the ", "Feed the trolls", "Feed trolls");
        // stopword gets removed, but position increment kicks in, which doesnt work for the prefix suggester
        assertSuggestions("feed the t");
    }

    public void testThatIndexingInvalidFieldsInCompletionFieldResultsInException() throws Exception {
        CompletionMappingBuilder completionMappingBuilder = new CompletionMappingBuilder();
        createIndexAndMapping(completionMappingBuilder);

        try {
            client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                    .startObject().startObject(FIELD)
                    .startArray("FRIGGININVALID").value("Nirvana").endArray()
                    .endObject().endObject()).get();
            fail("Expected MapperParsingException");
        } catch (MapperParsingException e) {
            assertThat(e.getMessage(), containsString("failed to parse"));
        }
    }

    public void testSkipDuplicates() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        createIndexAndMapping(mapping);
        int numDocs = randomIntBetween(10, 100);
        int numUnique = randomIntBetween(1, numDocs);
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 1; i <= numDocs; i++) {
            int id = i % numUnique;
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                .setSource(jsonBuilder()
                    .startObject()
                        .startObject(FIELD)
                            .field("input", "suggestion" + id)
                            .field("weight", id)
                        .endObject()
                    .endObject()
                ));
        }
        String[] expected = new String[numUnique];
        int sugg = numUnique - 1;
        for (int i = 0; i < numUnique; i++) {
            expected[i] = "suggestion" + sugg--;
        }
        indexRandom(true, indexRequestBuilders);
        CompletionSuggestionBuilder completionSuggestionBuilder =
            SuggestBuilders.completionSuggestion(FIELD).prefix("sugg").skipDuplicates(true).size(numUnique);

        SearchResponse searchResponse = client().prepareSearch(INDEX)
            .suggest(new SuggestBuilder().addSuggestion("suggestions", completionSuggestionBuilder)).get();
        assertSuggestions(searchResponse, true, "suggestions", expected);
    }

    public void assertSuggestions(String suggestionName, SuggestionBuilder suggestBuilder, String... suggestions) {
        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(new SuggestBuilder()
                .addSuggestion(suggestionName, suggestBuilder)).get();
        assertSuggestions(searchResponse, suggestionName, suggestions);
    }

    public void assertSuggestions(String suggestion, String... suggestions) {
        String suggestionName = RandomStrings.randomAsciiOfLength(random(), 10);
        CompletionSuggestionBuilder suggestionBuilder = SuggestBuilders.completionSuggestion(FIELD).text(suggestion).size(10);
        assertSuggestions(suggestionName, suggestionBuilder, suggestions);
    }

    public void assertSuggestionsNotInOrder(String suggestString, String... suggestions) {
        String suggestionName = RandomStrings.randomAsciiOfLength(random(), 10);
        SearchResponse searchResponse = client().prepareSearch(INDEX).suggest(
            new SuggestBuilder().addSuggestion(suggestionName,
                SuggestBuilders.completionSuggestion(FIELD).text(suggestString).size(10))
        ).get();

        assertSuggestions(searchResponse, false, suggestionName, suggestions);
    }

    static void assertSuggestions(SearchResponse searchResponse, String name, String... suggestions) {
        assertSuggestions(searchResponse, true, name, suggestions);
    }

    private static void assertSuggestions(SearchResponse searchResponse, boolean suggestionOrderStrict, String name,
            String... suggestions) {
        assertAllSuccessful(searchResponse);

        List<String> suggestionNames = new ArrayList<>();
        for (Suggest.Suggestion<? extends Suggest.Suggestion.Entry<? extends Suggest.Suggestion.Entry.Option>> suggestion :
                iterableAsArrayList(searchResponse.getSuggest())) {
            suggestionNames.add(suggestion.getName());
        }
        String expectFieldInResponseMsg = String.format(Locale.ROOT, "Expected suggestion named %s in response, got %s", name,
                suggestionNames);
        assertThat(expectFieldInResponseMsg, searchResponse.getSuggest().getSuggestion(name), is(notNullValue()));

        Suggest.Suggestion<Suggest.Suggestion.Entry<Suggest.Suggestion.Entry.Option>> suggestion = searchResponse.getSuggest()
                .getSuggestion(name);

        List<String> suggestionList = getNames(suggestion.getEntries().get(0));
        List<Suggest.Suggestion.Entry.Option> options = suggestion.getEntries().get(0).getOptions();

        String assertMsg = String.format(Locale.ROOT, "Expected options %s length to be %s, but was %s", suggestionList,
                suggestions.length, options.size());
        assertThat(assertMsg, options.size(), is(suggestions.length));
        if (suggestionOrderStrict) {
            for (int i = 0; i < suggestions.length; i++) {
                String errMsg = String.format(Locale.ROOT, "Expected elem %s in list %s to be [%s] score: %s", i, suggestionList,
                        suggestions[i], options.get(i).getScore());
                assertThat(errMsg, options.get(i).getText().toString(), is(suggestions[i]));
            }
        } else {
            for (String expectedSuggestion : suggestions) {
                String errMsg = String.format(Locale.ROOT, "Expected elem %s to be in list %s", expectedSuggestion, suggestionList);
                assertThat(errMsg, suggestionList, hasItem(expectedSuggestion));
            }
        }
    }

    private static List<String> getNames(Suggest.Suggestion.Entry<Suggest.Suggestion.Entry.Option> suggestEntry) {
        List<String> names = new ArrayList<>();
        for (Suggest.Suggestion.Entry.Option entry : suggestEntry.getOptions()) {
            names.add(entry.getText().string());
        }
        return names;
    }

    private void createIndexAndMappingAndSettings(Settings settings, CompletionMappingBuilder completionMappingBuilder) throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject()
                .startObject(TYPE).startObject("properties")
                .startObject("test_field")
                    .field("type", "keyword")
                .endObject()
                .startObject("title")
                    .field("type", "keyword")
                .endObject()
                .startObject(FIELD)
                .field("type", "completion")
                .field("analyzer", completionMappingBuilder.indexAnalyzer)
                .field("search_analyzer", completionMappingBuilder.searchAnalyzer)
                .field("preserve_separators", completionMappingBuilder.preserveSeparators)
                .field("preserve_position_increments", completionMappingBuilder.preservePositionIncrements);

        if (completionMappingBuilder.contextMappings != null) {
            mapping = mapping.startArray("contexts");
            for (Map.Entry<String, ContextMapping> contextMapping : completionMappingBuilder.contextMappings.entrySet()) {
                mapping = mapping.startObject()
                        .field("name", contextMapping.getValue().name())
                        .field("type", contextMapping.getValue().type().name());
                switch (contextMapping.getValue().type()) {
                    case CATEGORY:
                                mapping = mapping.field("path", ((CategoryContextMapping) contextMapping.getValue()).getFieldName());
                        break;
                    case GEO:
                        mapping = mapping
                                .field("path", ((GeoContextMapping) contextMapping.getValue()).getFieldName())
                                .field("precision", ((GeoContextMapping) contextMapping.getValue()).getPrecision());
                        break;
                }

                mapping = mapping.endObject();
            }

            mapping = mapping.endArray();
        }
        mapping = mapping.endObject()
                .endObject().endObject()
                .endObject();

        assertAcked(client().admin().indices().prepareCreate(INDEX)
                .setSettings(Settings.builder().put(indexSettings()).put(settings))
                .addMapping(TYPE, mapping)
                .get());
    }

    private void createIndexAndMapping(CompletionMappingBuilder completionMappingBuilder) throws IOException {
        createIndexAndMappingAndSettings(Settings.EMPTY, completionMappingBuilder);
    }

    // see #3555
    public void testPrunedSegments() throws IOException {
        createIndexAndMappingAndSettings(Settings.builder().put(SETTING_NUMBER_OF_SHARDS, 1).put(SETTING_NUMBER_OF_REPLICAS, 0).build(),
                completionMappingBuilder);

        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value("The Beatles").endArray()
                .endObject().endObject()
        ).get();
        client().prepareIndex(INDEX, TYPE, "2").setSource(jsonBuilder()
                .startObject()
                .field("somefield", "somevalue")
                .endObject()
        ).get(); // we have 2 docs in a segment...
        ForceMergeResponse actionGet = client().admin().indices().prepareForceMerge().setFlush(true).setMaxNumSegments(1).get();
        assertAllSuccessful(actionGet);
        refresh();
        // update the first one and then merge.. the target segment will have no value in FIELD
        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject()
                .field("somefield", "somevalue")
                .endObject()
        ).get();
        actionGet = client().admin().indices().prepareForceMerge().setFlush(true).setMaxNumSegments(1).get();
        assertAllSuccessful(actionGet);
        refresh();

        assertSuggestions("b");
        assertThat(2L, equalTo(client().prepareSearch(INDEX).setSize(0).get().getHits().getTotalHits()));
        for (IndexShardSegments seg : client().admin().indices().prepareSegments().get().getIndices().get(INDEX)) {
            ShardSegments[] shards = seg.getShards();
            for (ShardSegments shardSegments : shards) {
                assertThat(shardSegments.getSegments().size(), equalTo(1));
            }
        }
    }

    // see #3596
    public void testVeryLongInput() throws IOException {
        assertAcked(client().admin().indices().prepareCreate(INDEX).addMapping(TYPE, jsonBuilder().startObject()
                .startObject(TYPE).startObject("properties")
                .startObject(FIELD)
                .field("type", "completion")
                .endObject()
                .endObject().endObject()
                .endObject()).get());
        // can cause stack overflow without the default max_input_length
        String longString = replaceReservedChars(randomRealisticUnicodeOfLength(randomIntBetween(5000, 10000)), (char) 0x01);
        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                .startObject().startObject(FIELD)
                .startArray("input").value(longString).endArray()
                .endObject().endObject()
        ).setRefreshPolicy(IMMEDIATE).get();

    }

    // see #3648
    public void testReservedChars() throws IOException {
        assertAcked(client().admin().indices().prepareCreate(INDEX).addMapping(TYPE, jsonBuilder().startObject()
                .startObject(TYPE).startObject("properties")
                .startObject(FIELD)
                .field("type", "completion")
                .endObject()
                .endObject().endObject()
                .endObject()).get());
        // can cause stack overflow without the default max_input_length
        String string = "foo" + (char) 0x00 + "bar";
        try {
            client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                    .startObject().startObject(FIELD)
                    .startArray("input").value(string).endArray()
                    .field("output", "foobar")
                    .endObject().endObject()
            ).get();
            fail("Expected MapperParsingException");
        } catch (MapperParsingException e) {
            assertThat(e.getMessage(), containsString("failed to parse"));
        }
    }

    // see #5930
    public void testIssue5930() throws IOException {
        assertAcked(client().admin().indices().prepareCreate(INDEX).addMapping(TYPE, jsonBuilder().startObject()
                .startObject(TYPE).startObject("properties")
                .startObject(FIELD)
                .field("type", "completion")
                .endObject()
                .endObject().endObject()
                .endObject()).get());
        String string = "foo bar";
        client().prepareIndex(INDEX, TYPE, "1").setSource(jsonBuilder()
                        .startObject()
                        .field(FIELD, string)
                        .endObject()
        ).setRefreshPolicy(IMMEDIATE).get();

        try {
            client().prepareSearch(INDEX).addAggregation(AggregationBuilders.terms("suggest_agg").field(FIELD)
                    .collectMode(randomFrom(SubAggCollectionMode.values()))).get();
            // Exception must be thrown
            assertFalse(true);
        } catch (SearchPhaseExecutionException e) {
            assertThat(e.toString(), containsString("Fielddata is not supported on field [" + FIELD + "] of type [completion]"));
        }
    }

    public void testMultiDocSuggestions() throws Exception {
        final CompletionMappingBuilder mapping = new CompletionMappingBuilder();
        createIndexAndMapping(mapping);
        int numDocs = 10;
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        for (int i = 1; i <= numDocs; i++) {
            indexRequestBuilders.add(client().prepareIndex(INDEX, TYPE, "" + i)
                .setSource(jsonBuilder()
                    .startObject()
                    .startObject(FIELD)
                    .array("input", "suggestion" + i, "suggestions" + i, "suggester" + i)
                    .field("weight", i)
                    .endObject()
                    .endObject()
                ));
        }
        indexRandom(true, indexRequestBuilders);
        CompletionSuggestionBuilder prefix = SuggestBuilders.completionSuggestion(FIELD).prefix("sugg").shardSize(15);
        assertSuggestions("foo", prefix, "suggester10", "suggester9", "suggester8", "suggester7", "suggester6");
    }

    public void testSuggestWithFieldAlias() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder()
            .startObject()
                .startObject(TYPE)
                    .startObject("properties")
                        .startObject(FIELD)
                            .field("type", "completion")
                        .endObject()
                        .startObject("alias")
                            .field("type", "alias")
                            .field("path", FIELD)
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
        assertAcked(prepareCreate(INDEX).addMapping(TYPE, mapping));

        List<IndexRequestBuilder> builders = new ArrayList<>();
        builders.add(client().prepareIndex(INDEX, TYPE).setSource(FIELD, "apple"));
        builders.add(client().prepareIndex(INDEX, TYPE).setSource(FIELD, "mango"));
        builders.add(client().prepareIndex(INDEX, TYPE).setSource(FIELD, "papaya"));
        indexRandom(true, false, builders);

        CompletionSuggestionBuilder suggestionBuilder = SuggestBuilders.completionSuggestion("alias").text("app");
        assertSuggestions("suggestion", suggestionBuilder, "apple");
    }

    public static boolean isReservedChar(char c) {
        switch (c) {
            case '\u001F':
            case TokenStreamToAutomaton.HOLE:
            case 0x0:
            case ContextSuggestField.CONTEXT_SEPARATOR:
                return true;
            default:
                return false;
        }
    }

    private static String replaceReservedChars(String input, char replacement) {
        char[] charArray = input.toCharArray();
        for (int i = 0; i < charArray.length; i++) {
            if (isReservedChar(charArray[i])) {
                charArray[i] = replacement;
            }
        }
        return new String(charArray);
    }

    static class CompletionMappingBuilder {
        String searchAnalyzer = "simple";
        String indexAnalyzer = "simple";
        Boolean preserveSeparators = random().nextBoolean();
        Boolean preservePositionIncrements = random().nextBoolean();
        LinkedHashMap<String, ContextMapping> contextMappings = null;

        public CompletionMappingBuilder searchAnalyzer(String searchAnalyzer) {
            this.searchAnalyzer = searchAnalyzer;
            return this;
        }
        public CompletionMappingBuilder indexAnalyzer(String indexAnalyzer) {
            this.indexAnalyzer = indexAnalyzer;
            return this;
        }
        public CompletionMappingBuilder preserveSeparators(Boolean preserveSeparators) {
            this.preserveSeparators = preserveSeparators;
            return this;
        }
        public CompletionMappingBuilder preservePositionIncrements(Boolean preservePositionIncrements) {
            this.preservePositionIncrements = preservePositionIncrements;
            return this;
        }

        public CompletionMappingBuilder context(LinkedHashMap<String, ContextMapping> contextMappings) {
            this.contextMappings = contextMappings;
            return this;
        }
    }
}
