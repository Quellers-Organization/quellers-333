/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.retriever;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.SetOnce;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.TransportMultiSearchAction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.search.builder.PointInTimeBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.StoredFieldsContext;
import org.elasticsearch.search.rank.RankDoc;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.ScoreSortBuilder;
import org.elasticsearch.search.sort.ShardDocSortField;
import org.elasticsearch.search.sort.SortBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This abstract retriever defines a compound retriever. The idea is that it is not a leaf-retriever, i.e. it does not
 * perform actual searches itself. Instead, it is a container for a set of child retrievers and i responsible for combining
 * the results of the child retrievers according to the implementation of {@code combineQueryPhaseResults}.
 */
public abstract class CompoundRetrieverBuilder<T extends CompoundRetrieverBuilder<T>> extends RetrieverBuilder {

    public record RetrieverSource(RetrieverBuilder retriever, SearchSourceBuilder source) {}

    protected final int rankWindowSize;
    protected final List<RetrieverSource> innerRetrievers;

    protected CompoundRetrieverBuilder(List<RetrieverSource> innerRetrievers, int rankWindowSize) {
        this.rankWindowSize = rankWindowSize;
        this.innerRetrievers = innerRetrievers;
    }

    @SuppressWarnings("unchecked")
    public T addChild(RetrieverBuilder retrieverBuilder) {
        innerRetrievers.add(new RetrieverSource(retrieverBuilder, null));
        return (T) this;
    }

    /**
     * Returns a clone of the original retriever, replacing the sub-retrievers with
     * the provided {@code newChildRetrievers}.
     */
    public abstract T clone(List<RetrieverSource> newChildRetrievers);

    /**
     * Combines the provided {@code rankResults} to return the final top documents.
     */
    public abstract RankDoc[] combineInnerRetrieverResults(List<ScoreDoc[]> rankResults);

    @Override
    public final boolean isCompound() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final RetrieverBuilder rewrite(QueryRewriteContext ctx) throws IOException {
        if (ctx.getPointInTimeBuilder() == null) {
            throw new IllegalStateException("PIT is required");
        }

        // Rewrite prefilters
        boolean hasChanged = false;
        var newPreFilters = rewritePreFilters(ctx);
        hasChanged |= newPreFilters != preFilterQueryBuilders;

        // Rewrite retriever sources
        List<RetrieverSource> newRetrievers = new ArrayList<>();
        for (var entry : innerRetrievers) {
            RetrieverBuilder newRetriever = entry.retriever.rewrite(ctx);
            if (newRetriever != entry.retriever) {
                newRetrievers.add(new RetrieverSource(newRetriever, null));
                hasChanged |= true;
            } else {
                var sourceBuilder = entry.source != null
                    ? entry.source
                    : createSearchSourceBuilder(ctx.getPointInTimeBuilder(), newRetriever);
                var rewrittenSource = sourceBuilder.rewrite(ctx);
                newRetrievers.add(new RetrieverSource(newRetriever, rewrittenSource));
                hasChanged |= rewrittenSource != entry.source;
            }
        }
        if (hasChanged) {
            return clone(newRetrievers);
        }

        // execute searches
        final SetOnce<RankDoc[]> results = new SetOnce<>();
        final MultiSearchRequest multiSearchRequest = new MultiSearchRequest();
        for (var entry : innerRetrievers) {
            SearchRequest searchRequest = new SearchRequest().source(entry.source);
            // The can match phase can reorder shards, so we disable it to ensure the stable ordering
            // TODO: does this break if we query frozen tier? should we update/remove this
            searchRequest.setPreFilterShardSize(Integer.MAX_VALUE);
            multiSearchRequest.add(searchRequest);
        }
        ctx.registerAsyncAction((client, listener) -> {
            client.execute(TransportMultiSearchAction.TYPE, multiSearchRequest, new ActionListener<>() {
                @Override
                public void onResponse(MultiSearchResponse items) {
                    List<ScoreDoc[]> topDocs = new ArrayList<>();
                    List<Exception> failures = new ArrayList<>();
                    for (int i = 0; i < items.getResponses().length; i++) {
                        var item = items.getResponses()[i];
                        if (item.isFailure()) {
                            failures.add(item.getFailure());
                        } else {
                            assert item.getResponse() != null;
                            var rankDocs = getRankDocs(item.getResponse());
                            innerRetrievers.get(i).retriever().setRankDocs(rankDocs);
                            topDocs.add(rankDocs);
                        }
                    }
                    if (false == failures.isEmpty()) {
                        IllegalStateException ex = new IllegalStateException("Search failed - some nested retrievers returned errors.");
                        failures.forEach(ex::addSuppressed);
                        listener.onFailure(ex);
                    } else {
                        results.set(combineInnerRetrieverResults(topDocs));
                        listener.onResponse(null);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(e);
                }
            });
        });

        return new RankDocsRetrieverBuilder(
            rankWindowSize,
            newRetrievers.stream().map(s -> s.retriever).toList(),
            results::get,
            newPreFilters
        );
    }

    @Override
    public final QueryBuilder topDocsQuery() {
        throw new IllegalStateException(getName() + " cannot be nested");
    }

    @Override
    public final void extractToSearchSourceBuilder(SearchSourceBuilder searchSourceBuilder, boolean compoundUsed) {
        throw new IllegalStateException("Should not be called, missing a rewrite?");
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean doEquals(Object o) {
        CompoundRetrieverBuilder<?> that = (CompoundRetrieverBuilder<?>) o;
        return rankWindowSize == that.rankWindowSize && Objects.equals(innerRetrievers, that.innerRetrievers);
    }

    @Override
    public int doHashCode() {
        return Objects.hash(innerRetrievers);
    }

    private SearchSourceBuilder createSearchSourceBuilder(PointInTimeBuilder pit, RetrieverBuilder retrieverBuilder) {
        var sourceBuilder = new SearchSourceBuilder().pointInTimeBuilder(pit)
            .trackTotalHits(false)
            .storedFields(new StoredFieldsContext(false))
            .size(rankWindowSize);
        if (preFilterQueryBuilders.isEmpty() == false) {
            retrieverBuilder.getPreFilterQueryBuilders().addAll(preFilterQueryBuilders);
        }
        retrieverBuilder.extractToSearchSourceBuilder(sourceBuilder, true);

        // apply the pre-filters
        if (preFilterQueryBuilders.size() > 0) {
            QueryBuilder query = sourceBuilder.query();
            BoolQueryBuilder newQuery = new BoolQueryBuilder();
            if (query != null) {
                newQuery.must(query);
            }
            preFilterQueryBuilders.stream().forEach(newQuery::filter);
            sourceBuilder.query(newQuery);
        }

        // Record the shard id in the sort result
        List<SortBuilder<?>> sortBuilders = sourceBuilder.sorts() != null ? new ArrayList<>(sourceBuilder.sorts()) : new ArrayList<>();
        if (sortBuilders.isEmpty()) {
            sortBuilders.add(new ScoreSortBuilder());
        }
        sortBuilders.add(new FieldSortBuilder(FieldSortBuilder.SHARD_DOC_FIELD_NAME));
        sourceBuilder.sort(sortBuilders);
        return sourceBuilder;
    }

    private RankDoc[] getRankDocs(SearchResponse searchResponse) {
        int size = searchResponse.getHits().getHits().length;
        RankDoc[] docs = new RankDoc[size];
        for (int i = 0; i < size; i++) {
            var hit = searchResponse.getHits().getAt(i);
            long sortValue = (long) hit.getRawSortValues()[hit.getRawSortValues().length - 1];
            int doc = ShardDocSortField.decodeDoc(sortValue);
            int shardRequestIndex = ShardDocSortField.decodeShardRequestIndex(sortValue);
            docs[i] = new RankDoc(doc, hit.getScore(), shardRequestIndex);
            docs[i].rank = i + 1;
        }
        return docs;
    }
}
