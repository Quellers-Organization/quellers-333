/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.retriever;

import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.MatchNoneQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.RandomQueryBuilder;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.rank.RankDoc;
import org.elasticsearch.search.rank.RankDocsRankBuilder;
import org.elasticsearch.search.retriever.rankdoc.RankDocsAndScoreSortBuilder;
import org.elasticsearch.search.retriever.rankdoc.RankDocsQueryBuilder;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.elasticsearch.search.SearchService.DEFAULT_SIZE;
import static org.elasticsearch.search.vectors.KnnSearchBuilderTests.randomVector;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;

public class RankDocsRetrieverBuilderTests extends ESTestCase {

    private Supplier<RankDoc[]> rankDocsSupplier() {
        final int rankDocsCount = randomIntBetween(0, 10);
        final int shardIndex = 0;
        RankDoc[] rankDocs = new RankDoc[rankDocsCount];
        int docId = 0;
        for (int i = 0; i < rankDocsCount; i++) {
            RankDoc testRankDoc = new RankDoc(docId, randomFloat(), shardIndex);
            docId += randomInt(100);
            rankDocs[i] = testRankDoc;
        }
        return () -> rankDocs;
    }

    private List<RetrieverBuilder> innerRetrievers() {
        List<RetrieverBuilder> retrievers = new ArrayList<>();
        int numRetrievers = randomIntBetween(1, 10);
        for (int i = 0; i < numRetrievers; i++) {
            if (randomBoolean()) {
                StandardRetrieverBuilder standardRetrieverBuilder = new StandardRetrieverBuilder();
                standardRetrieverBuilder.queryBuilder = RandomQueryBuilder.createQuery(random());
                if (randomBoolean()) {
                    standardRetrieverBuilder.preFilterQueryBuilders = preFilters();
                }
                retrievers.add(standardRetrieverBuilder);
            } else {
                KnnRetrieverBuilder knnRetrieverBuilder = new KnnRetrieverBuilder(
                    randomAlphaOfLength(10),
                    randomVector(randomInt(10)),
                    null,
                    randomInt(10),
                    randomIntBetween(10, 100),
                    randomFloat()
                );
                if (randomBoolean()) {
                    knnRetrieverBuilder.preFilterQueryBuilders = preFilters();
                }
                knnRetrieverBuilder.rankDocs = rankDocsSupplier().get();
                retrievers.add(knnRetrieverBuilder);
            }
        }
        return retrievers;
    }

    private List<QueryBuilder> preFilters() {
        List<QueryBuilder> preFilters = new ArrayList<>();
        int numPreFilters = randomInt(10);
        for (int i = 0; i < numPreFilters; i++) {
            preFilters.add(RandomQueryBuilder.createQuery(random()));
        }
        return preFilters;
    }

    private RankDocsRetrieverBuilder createRandomRankDocsRetrieverBuilder() {
        return new RankDocsRetrieverBuilder(randomIntBetween(1, 100), innerRetrievers(), rankDocsSupplier(), preFilters());
    }

    public void testExtractToSearchSourceBuilder() {
        RankDocsRetrieverBuilder retriever = createRandomRankDocsRetrieverBuilder();
        SearchSourceBuilder source = new SearchSourceBuilder();
        if (randomBoolean()) {
            source.aggregation(new TermsAggregationBuilder("name").field("field"));
        }
        if (randomBoolean()) {
            source.explain(true);
        }
        if (randomBoolean()) {
            source.profile(true);
        }
        source.trackTotalHits(randomBoolean());
        retriever.extractToSearchSourceBuilder(source, randomBoolean());
        assertThat(source.sorts().size(), equalTo(1));
        assertThat(source.sorts().get(0), instanceOf(RankDocsAndScoreSortBuilder.class));
        assertThat(source.rankBuilder(), instanceOf(RankDocsRankBuilder.class));
        assertThat(source.query(), instanceOf(BoolQueryBuilder.class));
        BoolQueryBuilder bq = (BoolQueryBuilder) source.query();
        if (source.aggregations() != null
            || (source.explain() != null && source.explain())
            || source.profile()
            || (source.trackTotalHitsUpTo() != null && source.trackTotalHitsUpTo() > 0)) {
            assertThat(bq.must().size(), equalTo(1));
            assertThat(bq.must().get(0), instanceOf(BoolQueryBuilder.class));
            assertThat(((BoolQueryBuilder) bq.must().get(0)).should().size(), equalTo(retriever.sources.size()));
            assertThat(bq.filter().size(), equalTo(retriever.preFilterQueryBuilders.size()));
            assertThat(bq.should().size(), greaterThanOrEqualTo(1));
            assertThat(bq.should().get(0), instanceOf(RankDocsQueryBuilder.class));
            assertNull(source.postFilter());
        } else {
            assertThat(bq.must().size(), equalTo(1));
            assertThat(bq.must().get(0), instanceOf(BoolQueryBuilder.class));
            assertThat(bq.should().size(), equalTo(1));
            assertThat(bq.should().get(0), instanceOf(RankDocsQueryBuilder.class));
            assertNull(source.postFilter());
            assertThat(bq.filter().size(), equalTo(retriever.preFilterQueryBuilders.size()));
        }
    }

    public void testTopDocsQuery() {
        RankDocsRetrieverBuilder retriever = createRandomRankDocsRetrieverBuilder();
        QueryBuilder topDocs = retriever.topDocsQuery();
        assertNotNull(topDocs);
        assertThat(topDocs, instanceOf(BoolQueryBuilder.class));
        assertThat(((BoolQueryBuilder) topDocs).should(), hasSize(retriever.sources.size()));
    }

    public void testRewrite() throws IOException {
        RankDocsRetrieverBuilder retriever = createRandomRankDocsRetrieverBuilder();
        boolean compoundAdded = false;
        if (randomBoolean()) {
            compoundAdded = true;
            retriever.sources.add(new TestRetrieverBuilder("compound_retriever") {
                @Override
                public boolean isCompound() {
                    return true;
                }
            });
        }
        SearchSourceBuilder source = new SearchSourceBuilder().retriever(retriever);
        QueryRewriteContext queryRewriteContext = mock(QueryRewriteContext.class);
        int size = source.size() < 0 ? DEFAULT_SIZE : source.size();
        if (retriever.rankWindowSize < size) {
            expectThrows(ActionRequestValidationException.class, () -> Rewriteable.rewrite(source, queryRewriteContext));
        } else if (compoundAdded) {
            expectThrows(AssertionError.class, () -> Rewriteable.rewrite(source, queryRewriteContext));
        } else {
            SearchSourceBuilder rewrittenSource = Rewriteable.rewrite(source, queryRewriteContext);
            assertNull(rewrittenSource.retriever());
            assertTrue(rewrittenSource.knnSearch().isEmpty());
            assertThat(
                rewrittenSource.query(),
                anyOf(instanceOf(BoolQueryBuilder.class), instanceOf(MatchAllQueryBuilder.class), instanceOf(MatchNoneQueryBuilder.class))
            );
            if (rewrittenSource.query() instanceof BoolQueryBuilder) {
                BoolQueryBuilder bq = (BoolQueryBuilder) rewrittenSource.query();
                assertThat(bq.filter().size(), equalTo(retriever.preFilterQueryBuilders.size()));
                // we don't have any aggregations so the RankDocs query is set as a must clause
                assertThat(bq.must().size(), equalTo(1));
                assertThat(bq.must().get(0), instanceOf(BoolQueryBuilder.class));
                assertThat(bq.should().size(), equalTo(1));
                assertThat(bq.should().get(0), instanceOf(RankDocsQueryBuilder.class));
            }
        }
    }
}
