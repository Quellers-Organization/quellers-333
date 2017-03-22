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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.ObjectObjectHashMap;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.grouping.CollapseTopFieldDocs;
import org.elasticsearch.common.collect.HppcMaps;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.concurrent.AtomicArray;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.InternalAggregation.ReduceContext;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.aggregations.pipeline.SiblingPipelineAggregator;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.dfs.AggregatedDfs;
import org.elasticsearch.search.dfs.DfsSearchResult;
import org.elasticsearch.search.fetch.FetchSearchResult;
import org.elasticsearch.search.internal.InternalSearchResponse;
import org.elasticsearch.search.profile.ProfileShardResult;
import org.elasticsearch.search.profile.SearchProfileShardResults;
import org.elasticsearch.search.query.QuerySearchResult;
import org.elasticsearch.search.query.QuerySearchResultProvider;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.Suggest.Suggestion;
import org.elasticsearch.search.suggest.Suggest.Suggestion.Entry;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SearchPhaseController extends AbstractComponent {

    private static final ScoreDoc[] EMPTY_DOCS = new ScoreDoc[0];

    private final BigArrays bigArrays;
    private final ScriptService scriptService;

    public SearchPhaseController(Settings settings, BigArrays bigArrays, ScriptService scriptService) {
        super(settings);
        this.bigArrays = bigArrays;
        this.scriptService = scriptService;
    }

    public AggregatedDfs aggregateDfs(AtomicArray<DfsSearchResult> results) {
        ObjectObjectHashMap<Term, TermStatistics> termStatistics = HppcMaps.newNoNullKeysMap();
        ObjectObjectHashMap<String, CollectionStatistics> fieldStatistics = HppcMaps.newNoNullKeysMap();
        long aggMaxDoc = 0;
        for (AtomicArray.Entry<DfsSearchResult> lEntry : results.asList()) {
            final Term[] terms = lEntry.value.terms();
            final TermStatistics[] stats = lEntry.value.termStatistics();
            assert terms.length == stats.length;
            for (int i = 0; i < terms.length; i++) {
                assert terms[i] != null;
                TermStatistics existing = termStatistics.get(terms[i]);
                if (existing != null) {
                    assert terms[i].bytes().equals(existing.term());
                    // totalTermFrequency is an optional statistic we need to check if either one or both
                    // are set to -1 which means not present and then set it globally to -1
                    termStatistics.put(terms[i], new TermStatistics(existing.term(),
                            existing.docFreq() + stats[i].docFreq(),
                            optionalSum(existing.totalTermFreq(), stats[i].totalTermFreq())));
                } else {
                    termStatistics.put(terms[i], stats[i]);
                }

            }

            assert !lEntry.value.fieldStatistics().containsKey(null);
            final Object[] keys = lEntry.value.fieldStatistics().keys;
            final Object[] values = lEntry.value.fieldStatistics().values;
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] != null) {
                    String key = (String) keys[i];
                    CollectionStatistics value = (CollectionStatistics) values[i];
                    assert key != null;
                    CollectionStatistics existing = fieldStatistics.get(key);
                    if (existing != null) {
                        CollectionStatistics merged = new CollectionStatistics(
                                key, existing.maxDoc() + value.maxDoc(),
                                optionalSum(existing.docCount(), value.docCount()),
                                optionalSum(existing.sumTotalTermFreq(), value.sumTotalTermFreq()),
                                optionalSum(existing.sumDocFreq(), value.sumDocFreq())
                        );
                        fieldStatistics.put(key, merged);
                    } else {
                        fieldStatistics.put(key, value);
                    }
                }
            }
            aggMaxDoc += lEntry.value.maxDoc();
        }
        return new AggregatedDfs(termStatistics, fieldStatistics, aggMaxDoc);
    }

    private static long optionalSum(long left, long right) {
        return Math.min(left, right) == -1 ? -1 : left + right;
    }

    /**
     * Returns a score doc array of top N search docs across all shards, followed by top suggest docs for each
     * named completion suggestion across all shards. If more than one named completion suggestion is specified in the
     * request, the suggest docs for a named suggestion are ordered by the suggestion name.
     *
     * Note: The order of the sorted score docs depends on the shard index in the result array if the merge process needs to disambiguate
     * the result. In oder to obtain stable results the shard index (index of the result in the result array) must be the same.
     *
     * @param ignoreFrom Whether to ignore the from and sort all hits in each shard result.
     *                   Enabled only for scroll search, because that only retrieves hits of length 'size' in the query phase.
     * @param resultsArr Shard result holder
     */
    public ScoreDoc[] sortDocs(boolean ignoreFrom, AtomicArray<? extends QuerySearchResultProvider> resultsArr) throws IOException {
        List<? extends AtomicArray.Entry<? extends QuerySearchResultProvider>> results = resultsArr.asList();
        if (results.isEmpty()) {
            return EMPTY_DOCS;
        }

        final QuerySearchResult result;
        boolean canOptimize = false;
        int shardIndex = -1;
        if (results.size() == 1) {
            canOptimize = true;
            result = results.get(0).value.queryResult();
            shardIndex = results.get(0).index;
        } else {
            boolean hasResult = false;
            QuerySearchResult resultToOptimize = null;
            // lets see if we only got hits from a single shard, if so, we can optimize...
            for (AtomicArray.Entry<? extends QuerySearchResultProvider> entry : results) {
                if (entry.value.queryResult().hasHits()) {
                    if (hasResult) { // we already have one, can't really optimize
                        canOptimize = false;
                        break;
                    }
                    canOptimize = true;
                    hasResult = true;
                    resultToOptimize = entry.value.queryResult();
                    shardIndex = entry.index;
                }
            }
            result = canOptimize ? resultToOptimize : results.get(0).value.queryResult();
            assert result != null;
        }
        if (canOptimize) {
            int offset = result.from();
            if (ignoreFrom) {
                offset = 0;
            }
            ScoreDoc[] scoreDocs = result.topDocs().scoreDocs;
            ScoreDoc[] docs;
            int numSuggestDocs = 0;
            final Suggest suggest = result.queryResult().suggest();
            final List<CompletionSuggestion> completionSuggestions;
            if (suggest != null) {
                completionSuggestions = suggest.filter(CompletionSuggestion.class);
                for (CompletionSuggestion suggestion : completionSuggestions) {
                    numSuggestDocs += suggestion.getOptions().size();
                }
            } else {
                completionSuggestions = Collections.emptyList();
            }
            int docsOffset = 0;
            if (scoreDocs.length == 0 || scoreDocs.length < offset) {
                docs = new ScoreDoc[numSuggestDocs];
            } else {
                int resultDocsSize = result.size();
                if ((scoreDocs.length - offset) < resultDocsSize) {
                    resultDocsSize = scoreDocs.length - offset;
                }
                docs = new ScoreDoc[resultDocsSize + numSuggestDocs];
                for (int i = 0; i < resultDocsSize; i++) {
                    ScoreDoc scoreDoc = scoreDocs[offset + i];
                    scoreDoc.shardIndex = shardIndex;
                    docs[i] = scoreDoc;
                    docsOffset++;
                }
            }
            for (CompletionSuggestion suggestion: completionSuggestions) {
                for (CompletionSuggestion.Entry.Option option : suggestion.getOptions()) {
                    ScoreDoc doc = option.getDoc();
                    doc.shardIndex = shardIndex;
                    docs[docsOffset++] = doc;
                }
            }
            return docs;
        }

        final int topN = result.queryResult().size();
        final int from =  ignoreFrom ? 0 : result.queryResult().from();

        final TopDocs mergedTopDocs;
        final int numShards = resultsArr.length();
        if (result.queryResult().topDocs() instanceof CollapseTopFieldDocs) {
            CollapseTopFieldDocs firstTopDocs = (CollapseTopFieldDocs) result.queryResult().topDocs();
            final Sort sort = new Sort(firstTopDocs.fields);
            final CollapseTopFieldDocs[] shardTopDocs = new CollapseTopFieldDocs[numShards];
            fillTopDocs(shardTopDocs, results, new CollapseTopFieldDocs(firstTopDocs.field, 0, new FieldDoc[0],
                sort.getSort(), new Object[0], Float.NaN));
            mergedTopDocs = CollapseTopFieldDocs.merge(sort, from, topN, shardTopDocs);
        } else if (result.queryResult().topDocs() instanceof TopFieldDocs) {
            TopFieldDocs firstTopDocs = (TopFieldDocs) result.queryResult().topDocs();
            final Sort sort = new Sort(firstTopDocs.fields);
            final TopFieldDocs[] shardTopDocs = new TopFieldDocs[resultsArr.length()];
            fillTopDocs(shardTopDocs, results, new TopFieldDocs(0, new FieldDoc[0], sort.getSort(), Float.NaN));
            mergedTopDocs = TopDocs.merge(sort, from, topN, shardTopDocs, true);
        } else {
            final TopDocs[] shardTopDocs = new TopDocs[resultsArr.length()];
            fillTopDocs(shardTopDocs, results, Lucene.EMPTY_TOP_DOCS);
            mergedTopDocs = TopDocs.merge(from, topN, shardTopDocs, true);
        }

        ScoreDoc[] scoreDocs = mergedTopDocs.scoreDocs;
        final Map<String, List<Suggestion<CompletionSuggestion.Entry>>> groupedCompletionSuggestions = new HashMap<>();
        // group suggestions and assign shard index
        for (AtomicArray.Entry<? extends QuerySearchResultProvider> sortedResult : results) {
            Suggest shardSuggest = sortedResult.value.queryResult().suggest();
            if (shardSuggest != null) {
                for (CompletionSuggestion suggestion : shardSuggest.filter(CompletionSuggestion.class)) {
                    suggestion.setShardIndex(sortedResult.index);
                    List<Suggestion<CompletionSuggestion.Entry>> suggestions =
                        groupedCompletionSuggestions.computeIfAbsent(suggestion.getName(), s -> new ArrayList<>());
                    suggestions.add(suggestion);
                }
            }
        }
        if (groupedCompletionSuggestions.isEmpty() == false) {
            int numSuggestDocs = 0;
            List<Suggestion<? extends Entry<? extends Entry.Option>>> completionSuggestions =
                new ArrayList<>(groupedCompletionSuggestions.size());
            for (List<Suggestion<CompletionSuggestion.Entry>> groupedSuggestions : groupedCompletionSuggestions.values()) {
                final CompletionSuggestion completionSuggestion = CompletionSuggestion.reduceTo(groupedSuggestions);
                assert completionSuggestion != null;
                numSuggestDocs += completionSuggestion.getOptions().size();
                completionSuggestions.add(completionSuggestion);
            }
            scoreDocs = new ScoreDoc[mergedTopDocs.scoreDocs.length + numSuggestDocs];
            System.arraycopy(mergedTopDocs.scoreDocs, 0, scoreDocs, 0, mergedTopDocs.scoreDocs.length);
            int offset = mergedTopDocs.scoreDocs.length;
            Suggest suggestions = new Suggest(completionSuggestions);
            for (CompletionSuggestion completionSuggestion : suggestions.filter(CompletionSuggestion.class)) {
                for (CompletionSuggestion.Entry.Option option : completionSuggestion.getOptions()) {
                    scoreDocs[offset++] = option.getDoc();
                }
            }
        }
        return scoreDocs;
    }

    static <T extends TopDocs> void fillTopDocs(T[] shardTopDocs,
                                                        List<? extends AtomicArray.Entry<? extends QuerySearchResultProvider>> results,
                                                        T empytTopDocs) {
        if (results.size() != shardTopDocs.length) {
            // TopDocs#merge can't deal with null shard TopDocs
            Arrays.fill(shardTopDocs, empytTopDocs);
        }
        for (AtomicArray.Entry<? extends QuerySearchResultProvider> resultProvider : results) {
            final T topDocs = (T) resultProvider.value.queryResult().topDocs();
            assert topDocs != null : "top docs must not be null in a valid result";
            // the 'index' field is the position in the resultsArr atomic array
            shardTopDocs[resultProvider.index] = topDocs;
        }
    }
    public ScoreDoc[] getLastEmittedDocPerShard(ReducedQueryPhase reducedQueryPhase,
                                                ScoreDoc[] sortedScoreDocs, int numShards) {
        ScoreDoc[] lastEmittedDocPerShard = new ScoreDoc[numShards];
        if (reducedQueryPhase.isEmpty() == false) {
            // from is always zero as when we use scroll, we ignore from
            long size = Math.min(reducedQueryPhase.fetchHits, reducedQueryPhase.oneResult.size());
            // with collapsing we can have more hits than sorted docs
            size = Math.min(sortedScoreDocs.length, size);
            for (int sortedDocsIndex = 0; sortedDocsIndex < size; sortedDocsIndex++) {
                ScoreDoc scoreDoc = sortedScoreDocs[sortedDocsIndex];
                lastEmittedDocPerShard[scoreDoc.shardIndex] = scoreDoc;
            }
        }
        return lastEmittedDocPerShard;

    }

    /**
     * Builds an array, with potential null elements, with docs to load.
     */
    public IntArrayList[] fillDocIdsToLoad(int numShards, ScoreDoc[] shardDocs) {
        IntArrayList[] docIdsToLoad = new IntArrayList[numShards];
        for (ScoreDoc shardDoc : shardDocs) {
            IntArrayList shardDocIdsToLoad = docIdsToLoad[shardDoc.shardIndex];
            if (shardDocIdsToLoad == null) {
                shardDocIdsToLoad = docIdsToLoad[shardDoc.shardIndex] = new IntArrayList();
            }
            shardDocIdsToLoad.add(shardDoc.doc);
        }
        return docIdsToLoad;
    }

    /**
     * Enriches search hits and completion suggestion hits from <code>sortedDocs</code> using <code>fetchResultsArr</code>,
     * merges suggestions, aggregations and profile results
     *
     * Expects sortedDocs to have top search docs across all shards, optionally followed by top suggest docs for each named
     * completion suggestion ordered by suggestion name
     */
    public InternalSearchResponse merge(boolean ignoreFrom, ScoreDoc[] sortedDocs,
                                        ReducedQueryPhase reducedQueryPhase,
                                        AtomicArray<? extends QuerySearchResultProvider> fetchResultsArr) {
        if (reducedQueryPhase.isEmpty()) {
            return InternalSearchResponse.empty();
        }
        List<? extends AtomicArray.Entry<? extends QuerySearchResultProvider>> fetchResults = fetchResultsArr.asList();
        SearchHits hits = getHits(reducedQueryPhase, ignoreFrom, sortedDocs, fetchResultsArr);
        if (reducedQueryPhase.suggest != null) {
            if (!fetchResults.isEmpty()) {
                int currentOffset = hits.getHits().length;
                for (CompletionSuggestion suggestion : reducedQueryPhase.suggest.filter(CompletionSuggestion.class)) {
                    final List<CompletionSuggestion.Entry.Option> suggestionOptions = suggestion.getOptions();
                    for (int scoreDocIndex = currentOffset; scoreDocIndex < currentOffset + suggestionOptions.size(); scoreDocIndex++) {
                        ScoreDoc shardDoc = sortedDocs[scoreDocIndex];
                        QuerySearchResultProvider searchResultProvider = fetchResultsArr.get(shardDoc.shardIndex);
                        if (searchResultProvider == null) {
                            continue;
                        }
                        FetchSearchResult fetchResult = searchResultProvider.fetchResult();
                        int fetchResultIndex = fetchResult.counterGetAndIncrement();
                        if (fetchResultIndex < fetchResult.hits().internalHits().length) {
                            SearchHit hit = fetchResult.hits().internalHits()[fetchResultIndex];
                            CompletionSuggestion.Entry.Option suggestOption =
                                suggestionOptions.get(scoreDocIndex - currentOffset);
                            hit.score(shardDoc.score);
                            hit.shard(fetchResult.shardTarget());
                            suggestOption.setHit(hit);
                        }
                    }
                    currentOffset += suggestionOptions.size();
                }
                assert currentOffset == sortedDocs.length : "expected no more score doc slices";
            }
        }
        return reducedQueryPhase.buildResponse(hits);
    }

    private SearchHits getHits(ReducedQueryPhase reducedQueryPhase, boolean ignoreFrom, ScoreDoc[] sortedDocs,
                               AtomicArray<? extends QuerySearchResultProvider> fetchResultsArr) {
        List<? extends AtomicArray.Entry<? extends QuerySearchResultProvider>> fetchResults = fetchResultsArr.asList();
        boolean sorted = false;
        int sortScoreIndex = -1;
        if (reducedQueryPhase.oneResult.topDocs() instanceof TopFieldDocs) {
            TopFieldDocs fieldDocs = (TopFieldDocs) reducedQueryPhase.oneResult.queryResult().topDocs();
            if (fieldDocs instanceof CollapseTopFieldDocs &&
                fieldDocs.fields.length == 1 && fieldDocs.fields[0].getType() == SortField.Type.SCORE) {
                sorted = false;
            } else {
                sorted = true;
                for (int i = 0; i < fieldDocs.fields.length; i++) {
                    if (fieldDocs.fields[i].getType() == SortField.Type.SCORE) {
                        sortScoreIndex = i;
                    }
                }
            }
        }
        // clean the fetch counter
        for (AtomicArray.Entry<? extends QuerySearchResultProvider> entry : fetchResults) {
            entry.value.fetchResult().initCounter();
        }
        int from = ignoreFrom ? 0 : reducedQueryPhase.oneResult.queryResult().from();
        int numSearchHits = (int) Math.min(reducedQueryPhase.fetchHits - from, reducedQueryPhase.oneResult.size());
        // with collapsing we can have more fetch hits than sorted docs
        numSearchHits = Math.min(sortedDocs.length, numSearchHits);
        // merge hits
        List<SearchHit> hits = new ArrayList<>();
        if (!fetchResults.isEmpty()) {
            for (int i = 0; i < numSearchHits; i++) {
                ScoreDoc shardDoc = sortedDocs[i];
                QuerySearchResultProvider fetchResultProvider = fetchResultsArr.get(shardDoc.shardIndex);
                if (fetchResultProvider == null) {
                    continue;
                }
                FetchSearchResult fetchResult = fetchResultProvider.fetchResult();
                int index = fetchResult.counterGetAndIncrement();
                if (index < fetchResult.hits().internalHits().length) {
                    SearchHit searchHit = fetchResult.hits().internalHits()[index];
                    searchHit.score(shardDoc.score);
                    searchHit.shard(fetchResult.shardTarget());
                    if (sorted) {
                        FieldDoc fieldDoc = (FieldDoc) shardDoc;
                        searchHit.sortValues(fieldDoc.fields, reducedQueryPhase.oneResult.sortValueFormats());
                        if (sortScoreIndex != -1) {
                            searchHit.score(((Number) fieldDoc.fields[sortScoreIndex]).floatValue());
                        }
                    }
                    hits.add(searchHit);
                }
            }
        }
        return new SearchHits(hits.toArray(new SearchHit[hits.size()]), reducedQueryPhase.totalHits,
            reducedQueryPhase.maxScore);
    }

    /**
     * Reduces the given query results and consumes all aggregations and profile results.
     * @param queryResults a list of non-null query shard results
     */
    public final ReducedQueryPhase reducedQueryPhase(List<? extends AtomicArray.Entry<? extends QuerySearchResultProvider>> queryResults) {
        return reducedQueryPhase(queryResults, null, 0);
    }

    /**
     * Reduces the given query results and consumes all aggregations and profile results.
     * @param queryResults a list of non-null query shard results
     * @param bufferdAggs a list of pre-collected / buffered aggregations. if this list is non-null all aggregations have been consumed
     *                    from all non-null query results.
     * @param numReducePhases the number of non-final reduce phases applied to the query results.
     * @see QuerySearchResult#consumeAggs()
     * @see QuerySearchResult#consumeProfileResult()
     */
    private ReducedQueryPhase reducedQueryPhase(List<? extends AtomicArray.Entry<? extends QuerySearchResultProvider>> queryResults,
                                                     List<InternalAggregations> bufferdAggs, int numReducePhases) {
        assert numReducePhases >= 0 : "num reduce phases must be >= 0 but was: " + numReducePhases;
        numReducePhases++; // increment for this phase
        long totalHits = 0;
        long fetchHits = 0;
        float maxScore = Float.NEGATIVE_INFINITY;
        boolean timedOut = false;
        Boolean terminatedEarly = null;
        if (queryResults.isEmpty()) { // early terminate we have nothing to reduce
            return new ReducedQueryPhase(totalHits, fetchHits, maxScore, timedOut, terminatedEarly, null, null, null, null,
                numReducePhases);
        }
        final QuerySearchResult firstResult = queryResults.get(0).value.queryResult();
        final boolean hasSuggest = firstResult.suggest() != null;
        final boolean hasProfileResults = firstResult.hasProfileResults();
        final boolean consumeAggs;
        final List<InternalAggregations> aggregationsList;
        if (bufferdAggs != null) {
            consumeAggs = false;
            // we already have results from intermediate reduces and just need to perform the final reduce
            assert firstResult.hasAggs() : "firstResult has no aggs but we got non null buffered aggs?";
            aggregationsList = bufferdAggs;
        } else if (firstResult.hasAggs()) {
            // the number of shards was less than the buffer size so we reduce agg results directly
            aggregationsList = new ArrayList<>(queryResults.size());
            consumeAggs = true;
        } else {
            // no aggregations
            aggregationsList = Collections.emptyList();
            consumeAggs = false;
        }

        // count the total (we use the query result provider here, since we might not get any hits (we scrolled past them))
        final Map<String, List<Suggestion>> groupedSuggestions = hasSuggest ? new HashMap<>() : Collections.emptyMap();
        final Map<String, ProfileShardResult> profileResults = hasProfileResults ? new HashMap<>(queryResults.size())
            : Collections.emptyMap();
        for (AtomicArray.Entry<? extends QuerySearchResultProvider> entry : queryResults) {
            QuerySearchResult result = entry.value.queryResult();
            if (result.searchTimedOut()) {
                timedOut = true;
            }
            if (result.terminatedEarly() != null) {
                if (terminatedEarly == null) {
                    terminatedEarly = result.terminatedEarly();
                } else if (result.terminatedEarly()) {
                    terminatedEarly = true;
                }
            }
            totalHits += result.topDocs().totalHits;
            fetchHits += result.topDocs().scoreDocs.length;
            if (!Float.isNaN(result.topDocs().getMaxScore())) {
                maxScore = Math.max(maxScore, result.topDocs().getMaxScore());
            }
            if (hasSuggest) {
                assert result.suggest() != null;
                for (Suggestion<? extends Suggestion.Entry<? extends Suggestion.Entry.Option>> suggestion : result.suggest()) {
                    List<Suggestion> suggestionList = groupedSuggestions.computeIfAbsent(suggestion.getName(), s -> new ArrayList<>());
                    suggestionList.add(suggestion);
                }
            }
            if (consumeAggs) {
                aggregationsList.add((InternalAggregations) result.consumeAggs());
            }
            if (hasProfileResults) {
                String key = result.shardTarget().toString();
                profileResults.put(key, result.consumeProfileResult());
            }
        }
        final Suggest suggest = groupedSuggestions.isEmpty() ? null : new Suggest(Suggest.reduce(groupedSuggestions));
        ReduceContext reduceContext = new ReduceContext(bigArrays, scriptService, true);
        final InternalAggregations aggregations = aggregationsList.isEmpty() ? null : reduceAggs(aggregationsList,
            firstResult.pipelineAggregators(), reduceContext);
        final SearchProfileShardResults shardResults = profileResults.isEmpty() ? null : new SearchProfileShardResults(profileResults);
        return new ReducedQueryPhase(totalHits, fetchHits, maxScore, timedOut, terminatedEarly, firstResult, suggest, aggregations,
            shardResults, numReducePhases);
    }


    /**
     * Performs an intermediate reduce phase on the aggregations. For instance with this reduce phase never prune information
     * that relevant for the final reduce step. For final reduce see {@link #reduceAggs(List, List, ReduceContext)}
     */
    private InternalAggregations reduceAggsIncrementally(List<InternalAggregations> aggregationsList) {
        ReduceContext reduceContext = new ReduceContext(bigArrays, scriptService, false);
        return aggregationsList.isEmpty() ? null : reduceAggs(aggregationsList,
            null, reduceContext);
    }

    private InternalAggregations reduceAggs(List<InternalAggregations> aggregationsList,
                                            List<SiblingPipelineAggregator> pipelineAggregators, ReduceContext reduceContext) {
        InternalAggregations aggregations = InternalAggregations.reduce(aggregationsList, reduceContext);
        if (pipelineAggregators != null) {
            List<InternalAggregation> newAggs = StreamSupport.stream(aggregations.spliterator(), false)
                .map((p) -> (InternalAggregation) p)
                .collect(Collectors.toList());
            for (SiblingPipelineAggregator pipelineAggregator : pipelineAggregators) {
                InternalAggregation newAgg = pipelineAggregator.doReduce(new InternalAggregations(newAggs), reduceContext);
                newAggs.add(newAgg);
            }
            return new InternalAggregations(newAggs);
        }
        return aggregations;
    }

    public static final class ReducedQueryPhase {
        // the sum of all hits across all reduces shards
        final long totalHits;
        // the number of returned hits (doc IDs) across all reduces shards
        final long fetchHits;
        // the max score across all reduces hits or {@link Float#NaN} if no hits returned
        final float maxScore;
        // <code>true</code> if at least one reduced result timed out
        final boolean timedOut;
        // non null and true if at least one reduced result was terminated early
        final Boolean terminatedEarly;
        // an non-null arbitrary query result if was at least one reduced result
        final QuerySearchResult oneResult;
        // the reduced suggest results
        final Suggest suggest;
        // the reduced internal aggregations
        final InternalAggregations aggregations;
        // the reduced profile results
        final SearchProfileShardResults shardResults;
        // the number of reduces phases
        final int numReducePhases;

        ReducedQueryPhase(long totalHits, long fetchHits, float maxScore, boolean timedOut, Boolean terminatedEarly,
                                 QuerySearchResult oneResult, Suggest suggest, InternalAggregations aggregations,
                                 SearchProfileShardResults shardResults, int numReducePhases) {
            if (numReducePhases <= 0) {
                throw new IllegalArgumentException("at least one reduce phase must have been applied but was: " + numReducePhases);
            }
            this.totalHits = totalHits;
            this.fetchHits = fetchHits;
            if (Float.isInfinite(maxScore)) {
                this.maxScore = Float.NaN;
            } else {
                this.maxScore = maxScore;
            }
            this.timedOut = timedOut;
            this.terminatedEarly = terminatedEarly;
            this.oneResult = oneResult;
            this.suggest = suggest;
            this.aggregations = aggregations;
            this.shardResults = shardResults;
            this.numReducePhases = numReducePhases;
        }

        /**
         * Creates a new search response from the given merged hits.
         * @see #merge(boolean, ScoreDoc[], ReducedQueryPhase, AtomicArray)
         */
        public InternalSearchResponse buildResponse(SearchHits hits) {
            return new InternalSearchResponse(hits, aggregations, suggest, shardResults, timedOut, terminatedEarly, numReducePhases);
        }

        /**
         * Returns <code>true</code> iff the query phase had no results. Otherwise <code>false</code>
         */
        public boolean isEmpty() {
            return oneResult == null;
        }
    }

    /**
     * A {@link org.elasticsearch.action.search.InitialSearchPhase.SearchPhaseResults} implementation
     * that incrementally reduces aggregation results as shard results are consumed.
     * This implementation can be configured to batch up a certain amount of results and only reduce them
     * iff the buffer is exhausted.
     */
    static final class QueryPhaseResultConsumer
        extends InitialSearchPhase.SearchPhaseResults<QuerySearchResultProvider> {
        private final InternalAggregations[] buffer;
        private int index;
        private final SearchPhaseController controller;
        private int numReducePhases = 0;

        /**
         * Creates a new {@link QueryPhaseResultConsumer}
         * @param controller a controller instance to reduce the query response objects
         * @param expectedResultSize the expected number of query results. Corresponds to the number of shards queried
         * @param bufferSize the size of the reduce buffer. if the buffer size is smaller than the number of expected results
         *                   the buffer is used to incrementally reduce aggregation results before all shards responded.
         */
        private QueryPhaseResultConsumer(SearchPhaseController controller, int expectedResultSize, int bufferSize) {
            super(expectedResultSize);
            if (expectedResultSize != 1 && bufferSize < 2) {
                throw new IllegalArgumentException("buffer size must be >= 2 if there is more than one expected result");
            }
            if (expectedResultSize <= bufferSize) {
                throw new IllegalArgumentException("buffer size must be less than the expected result size");
            }
            this.controller = controller;
            // no need to buffer anything if we have less expected results. in this case we don't consume any results ahead of time.
            this.buffer = new InternalAggregations[bufferSize];
        }

        @Override
        public void consumeResult(int shardIndex, QuerySearchResultProvider result) {
            super.consumeResult(shardIndex, result);
            QuerySearchResult queryResult = result.queryResult();
            assert queryResult.hasAggs() : "this collector should only be used if aggs are requested";
            consumeInternal(queryResult);
        }

        private synchronized void consumeInternal(QuerySearchResult querySearchResult) {
            InternalAggregations aggregations = (InternalAggregations) querySearchResult.consumeAggs();
            if (index == buffer.length) {
                InternalAggregations reducedAggs = controller.reduceAggsIncrementally(Arrays.asList(buffer));
                Arrays.fill(buffer, null);
                numReducePhases++;
                buffer[0] = reducedAggs;
                index = 1;
            }
            final int i = index++;
            buffer[i] = aggregations;
        }

        private synchronized  List<InternalAggregations> getRemaining() {
            return Arrays.asList(buffer).subList(0, index);
        }

        @Override
        public ReducedQueryPhase reduce() {
            return controller.reducedQueryPhase(results.asList(), getRemaining(), numReducePhases);
        }

        /**
         * Returns the number of buffered results
         */
        int getNumBuffered() {
            return index;
        }

        int getNumReducePhases() { return numReducePhases; }
    }

    /**
     * Returns a new SearchPhaseResults instance. This might return an instance that reduces search responses incrementally.
     */
    InitialSearchPhase.SearchPhaseResults<QuerySearchResultProvider> newSearchPhaseResults(SearchRequest request, int numShards) {
        SearchSourceBuilder source = request.source();
        if (source != null && source.aggregations() != null) {
            if (request.getBatchedReduceSize() < numShards) {
                // only use this if there are aggs and if there are more shards than we should reduce at once
                return new QueryPhaseResultConsumer(this, numShards, request.getBatchedReduceSize());
            }
        }
        return new InitialSearchPhase.SearchPhaseResults(numShards) {
            @Override
            public ReducedQueryPhase reduce() {
                return reducedQueryPhase(results.asList());
            }
        };
    }
}
