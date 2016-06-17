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
package org.elasticsearch.percolator;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.PrefixCodedTerms;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queries.BlendedTermQuery;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.queries.TermsQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.spans.SpanFirstQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanNotQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.lucene.search.MatchNoDocsQuery;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.percolator.ExtractQueryTermsService.Result;
import org.elasticsearch.test.ESTestCase;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.percolator.ExtractQueryTermsService.EXTRACTION_COMPLETE;
import static org.elasticsearch.percolator.ExtractQueryTermsService.EXTRACTION_FAILED;
import static org.elasticsearch.percolator.ExtractQueryTermsService.EXTRACTION_PARTIAL;
import static org.elasticsearch.percolator.ExtractQueryTermsService.UnsupportedQueryException;
import static org.elasticsearch.percolator.ExtractQueryTermsService.extractQueryTerms;
import static org.elasticsearch.percolator.ExtractQueryTermsService.createQueryTermsQuery;
import static org.elasticsearch.percolator.ExtractQueryTermsService.selectTermListWithTheLongestShortestTerm;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

public class ExtractQueryTermsServiceTests extends ESTestCase {

    public final static String QUERY_TERMS_FIELD = "extracted_terms";
    public final static String EXTRACTION_RESULT_FIELD = "extraction_result";
    public final static FieldType QUERY_TERMS_FIELD_TYPE = new FieldType();

    static {
        QUERY_TERMS_FIELD_TYPE.setTokenized(false);
        QUERY_TERMS_FIELD_TYPE.setIndexOptions(IndexOptions.DOCS);
        QUERY_TERMS_FIELD_TYPE.freeze();
    }

    public void testExtractQueryMetadata() {
        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        TermQuery termQuery1 = new TermQuery(new Term("field1", "term1"));
        bq.add(termQuery1, BooleanClause.Occur.SHOULD);
        TermQuery termQuery2 = new TermQuery(new Term("field2", "term2"));
        bq.add(termQuery2, BooleanClause.Occur.SHOULD);

        ParseContext.Document document = new ParseContext.Document();
        extractQueryTerms(bq.build(), document, QUERY_TERMS_FIELD, EXTRACTION_RESULT_FIELD, QUERY_TERMS_FIELD_TYPE);
        assertThat(document.getField(EXTRACTION_RESULT_FIELD).stringValue(), equalTo(EXTRACTION_COMPLETE));
        List<IndexableField> fields = new ArrayList<>(Arrays.asList(document.getFields(QUERY_TERMS_FIELD)));
        Collections.sort(fields, (field1, field2) -> field1.binaryValue().compareTo(field2.binaryValue()));
        assertThat(fields.size(), equalTo(2));
        assertThat(fields.get(0).name(), equalTo(QUERY_TERMS_FIELD));
        assertThat(fields.get(0).binaryValue().utf8ToString(), equalTo("field1\u0000term1"));
        assertThat(fields.get(1).name(), equalTo(QUERY_TERMS_FIELD));
        assertThat(fields.get(1).binaryValue().utf8ToString(), equalTo("field2\u0000term2"));
    }

    public void testExtractQueryMetadata_unsupported() {
        TermRangeQuery query = new TermRangeQuery("field1", new BytesRef("a"), new BytesRef("z"), true, true);
        ParseContext.Document document = new ParseContext.Document();
        extractQueryTerms(query, document, QUERY_TERMS_FIELD, EXTRACTION_RESULT_FIELD, QUERY_TERMS_FIELD_TYPE);
        assertThat(document.getFields().size(), equalTo(1));
        assertThat(document.getField(EXTRACTION_RESULT_FIELD).stringValue(), equalTo(EXTRACTION_FAILED));
    }

    public void testExtractQueryMetadata_notVerified() {
        PhraseQuery phraseQuery = new PhraseQuery("field", "term");

        ParseContext.Document document = new ParseContext.Document();
        extractQueryTerms(phraseQuery, document, QUERY_TERMS_FIELD, EXTRACTION_RESULT_FIELD, QUERY_TERMS_FIELD_TYPE);
        assertThat(document.getFields().size(), equalTo(2));
        assertThat(document.getFields().get(0).name(), equalTo(QUERY_TERMS_FIELD));
        assertThat(document.getFields().get(0).binaryValue().utf8ToString(), equalTo("field\u0000term"));
        assertThat(document.getField(EXTRACTION_RESULT_FIELD).stringValue(), equalTo(EXTRACTION_PARTIAL));
    }

    public void testExtractQueryMetadata_termQuery() {
        TermQuery termQuery = new TermQuery(new Term("_field", "_term"));
        Result result = extractQueryTerms(termQuery);
        assertThat(result.verified, is(true));
        List<Term> terms = new ArrayList<>(result.terms);
        assertThat(terms.size(), equalTo(1));
        assertThat(terms.get(0).field(), equalTo(termQuery.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery.getTerm().bytes()));
    }

    public void testExtractQueryMetadata_termsQuery() {
        TermsQuery termsQuery = new TermsQuery("_field", new BytesRef("_term1"), new BytesRef("_term2"));
        Result result = extractQueryTerms(termsQuery);
        assertThat(result.verified, is(true));
        List<Term> terms = new ArrayList<>(result.terms);
        Collections.sort(terms);
        assertThat(terms.size(), equalTo(2));
        assertThat(terms.get(0).field(), equalTo("_field"));
        assertThat(terms.get(0).text(), equalTo("_term1"));
        assertThat(terms.get(1).field(), equalTo("_field"));
        assertThat(terms.get(1).text(), equalTo("_term2"));

        // test with different fields
        termsQuery = new TermsQuery(new Term("_field1", "_term1"), new Term("_field2", "_term2"));
        result = extractQueryTerms(termsQuery);
        assertThat(result.verified, is(true));
        terms = new ArrayList<>(result.terms);
        Collections.sort(terms);
        assertThat(terms.size(), equalTo(2));
        assertThat(terms.get(0).field(), equalTo("_field1"));
        assertThat(terms.get(0).text(), equalTo("_term1"));
        assertThat(terms.get(1).field(), equalTo("_field2"));
        assertThat(terms.get(1).text(), equalTo("_term2"));
    }

    public void testExtractQueryMetadata_phraseQuery() {
        PhraseQuery phraseQuery = new PhraseQuery("_field", "_term1", "term2");
        Result result = extractQueryTerms(phraseQuery);
        assertThat(result.verified, is(false));
        List<Term> terms = new ArrayList<>(result.terms);
        assertThat(terms.size(), equalTo(1));
        assertThat(terms.get(0).field(), equalTo(phraseQuery.getTerms()[0].field()));
        assertThat(terms.get(0).bytes(), equalTo(phraseQuery.getTerms()[0].bytes()));
    }

    public void testExtractQueryMetadata_booleanQuery() {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        builder.add(termQuery1, BooleanClause.Occur.SHOULD);
        PhraseQuery phraseQuery = new PhraseQuery("_field", "_term1", "term2");
        builder.add(phraseQuery, BooleanClause.Occur.SHOULD);

        BooleanQuery.Builder subBuilder = new BooleanQuery.Builder();
        TermQuery termQuery2 = new TermQuery(new Term("_field1", "_term"));
        subBuilder.add(termQuery2, BooleanClause.Occur.MUST);
        TermQuery termQuery3 = new TermQuery(new Term("_field3", "_long_term"));
        subBuilder.add(termQuery3, BooleanClause.Occur.MUST);
        builder.add(subBuilder.build(), BooleanClause.Occur.SHOULD);

        BooleanQuery booleanQuery = builder.build();
        Result result = extractQueryTerms(booleanQuery);
        assertThat("Should clause with phrase query isn't verified, so entire query can't be verified", result.verified, is(false));
        List<Term> terms = new ArrayList<>(result.terms);
        Collections.sort(terms);
        assertThat(terms.size(), equalTo(3));
        assertThat(terms.get(0).field(), equalTo(termQuery1.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery1.getTerm().bytes()));
        assertThat(terms.get(1).field(), equalTo(phraseQuery.getTerms()[0].field()));
        assertThat(terms.get(1).bytes(), equalTo(phraseQuery.getTerms()[0].bytes()));
        assertThat(terms.get(2).field(), equalTo(termQuery3.getTerm().field()));
        assertThat(terms.get(2).bytes(), equalTo(termQuery3.getTerm().bytes()));
    }

    public void testExtractQueryMetadata_booleanQuery_onlyShould() {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term1"));
        builder.add(termQuery1, BooleanClause.Occur.SHOULD);
        TermQuery termQuery2 = new TermQuery(new Term("_field", "_term2"));
        builder.add(termQuery2, BooleanClause.Occur.SHOULD);

        BooleanQuery.Builder subBuilder = new BooleanQuery.Builder();
        TermQuery termQuery3 = new TermQuery(new Term("_field1", "_term"));
        subBuilder.add(termQuery3, BooleanClause.Occur.SHOULD);
        TermQuery termQuery4 = new TermQuery(new Term("_field3", "_long_term"));
        subBuilder.add(termQuery4, BooleanClause.Occur.SHOULD);
        builder.add(subBuilder.build(), BooleanClause.Occur.SHOULD);

        BooleanQuery booleanQuery = builder.build();
        Result result = extractQueryTerms(booleanQuery);
        assertThat(result.verified, is(true));
        List<Term> terms = new ArrayList<>(result.terms);
        Collections.sort(terms);
        assertThat(terms.size(), equalTo(4));
        assertThat(terms.get(0).field(), equalTo(termQuery1.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery1.getTerm().bytes()));
        assertThat(terms.get(1).field(), equalTo(termQuery2.getTerm().field()));
        assertThat(terms.get(1).bytes(), equalTo(termQuery2.getTerm().bytes()));
        assertThat(terms.get(2).field(), equalTo(termQuery3.getTerm().field()));
        assertThat(terms.get(2).bytes(), equalTo(termQuery3.getTerm().bytes()));
        assertThat(terms.get(3).field(), equalTo(termQuery4.getTerm().field()));
        assertThat(terms.get(3).bytes(), equalTo(termQuery4.getTerm().bytes()));
    }

    public void testExtractQueryMetadata_booleanQueryWithMustNot() {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        builder.add(termQuery1, BooleanClause.Occur.MUST_NOT);
        PhraseQuery phraseQuery = new PhraseQuery("_field", "_term1", "term2");
        builder.add(phraseQuery, BooleanClause.Occur.SHOULD);

        BooleanQuery booleanQuery = builder.build();
        Result result = extractQueryTerms(booleanQuery);
        assertThat(result.verified, is(false));
        List<Term> terms = new ArrayList<>(result.terms);
        assertThat(terms.size(), equalTo(1));
        assertThat(terms.get(0).field(), equalTo(phraseQuery.getTerms()[0].field()));
        assertThat(terms.get(0).bytes(), equalTo(phraseQuery.getTerms()[0].bytes()));
    }

    public void testExactMatch_booleanQuery() {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term1"));
        builder.add(termQuery1, BooleanClause.Occur.SHOULD);
        TermQuery termQuery2 = new TermQuery(new Term("_field", "_term2"));
        builder.add(termQuery2, BooleanClause.Occur.SHOULD);
        Result result = extractQueryTerms(builder.build());
        assertThat("All clauses are exact, so candidate matches are verified", result.verified, is(true));

        builder = new BooleanQuery.Builder();
        builder.add(termQuery1, BooleanClause.Occur.SHOULD);
        PhraseQuery phraseQuery1 = new PhraseQuery("_field", "_term1", "_term2");
        builder.add(phraseQuery1, BooleanClause.Occur.SHOULD);
        result = extractQueryTerms(builder.build());
        assertThat("Clause isn't exact, so candidate matches are not verified", result.verified, is(false));

        builder = new BooleanQuery.Builder();
        builder.add(phraseQuery1, BooleanClause.Occur.SHOULD);
        PhraseQuery phraseQuery2 = new PhraseQuery("_field", "_term3", "_term4");
        builder.add(phraseQuery2, BooleanClause.Occur.SHOULD);
        result = extractQueryTerms(builder.build());
        assertThat("No clause is exact, so candidate matches are not verified", result.verified, is(false));

        builder = new BooleanQuery.Builder();
        builder.add(termQuery1, BooleanClause.Occur.MUST_NOT);
        builder.add(termQuery2, BooleanClause.Occur.SHOULD);
        result = extractQueryTerms(builder.build());
        assertThat("There is a must_not clause, so candidate matches are not verified", result.verified, is(false));

        builder = new BooleanQuery.Builder();
        builder.setMinimumNumberShouldMatch(randomIntBetween(2, 32));
        builder.add(termQuery1, BooleanClause.Occur.SHOULD);
        builder.add(termQuery2, BooleanClause.Occur.SHOULD);
        result = extractQueryTerms(builder.build());
        assertThat("Minimum match is >= 1, so candidate matches are not verified", result.verified, is(false));

        builder = new BooleanQuery.Builder();
        builder.add(termQuery1, randomBoolean() ? BooleanClause.Occur.MUST : BooleanClause.Occur.FILTER);
        result = extractQueryTerms(builder.build());
        assertThat("Single required clause, so candidate matches are verified", result.verified, is(true));

        builder = new BooleanQuery.Builder();
        builder.add(termQuery1, randomBoolean() ? BooleanClause.Occur.MUST : BooleanClause.Occur.FILTER);
        builder.add(termQuery2, randomBoolean() ? BooleanClause.Occur.MUST : BooleanClause.Occur.FILTER);
        result = extractQueryTerms(builder.build());
        assertThat("Two or more required clauses, so candidate matches are not verified", result.verified, is(false));

        builder = new BooleanQuery.Builder();
        builder.add(termQuery1, randomBoolean() ? BooleanClause.Occur.MUST : BooleanClause.Occur.FILTER);
        builder.add(termQuery2, BooleanClause.Occur.MUST_NOT);
        result = extractQueryTerms(builder.build());
        assertThat("Required and prohibited clauses, so candidate matches are not verified", result.verified, is(false));
    }

    public void testExtractQueryMetadata_constantScoreQuery() {
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        ConstantScoreQuery constantScoreQuery = new ConstantScoreQuery(termQuery1);
        Result result = extractQueryTerms(constantScoreQuery);
        assertThat(result.verified, is(true));
        List<Term> terms = new ArrayList<>(result.terms);
        assertThat(terms.size(), equalTo(1));
        assertThat(terms.get(0).field(), equalTo(termQuery1.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery1.getTerm().bytes()));
    }

    public void testExtractQueryMetadata_boostQuery() {
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        BoostQuery constantScoreQuery = new BoostQuery(termQuery1, 1f);
        Result result = extractQueryTerms(constantScoreQuery);
        assertThat(result.verified, is(true));
        List<Term> terms = new ArrayList<>(result.terms);
        assertThat(terms.size(), equalTo(1));
        assertThat(terms.get(0).field(), equalTo(termQuery1.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery1.getTerm().bytes()));
    }

    public void testExtractQueryMetadata_commonTermsQuery() {
        CommonTermsQuery commonTermsQuery = new CommonTermsQuery(BooleanClause.Occur.SHOULD, BooleanClause.Occur.SHOULD, 100);
        commonTermsQuery.add(new Term("_field", "_term1"));
        commonTermsQuery.add(new Term("_field", "_term2"));
        Result result = extractQueryTerms(commonTermsQuery);
        assertThat(result.verified, is(false));
        List<Term> terms = new ArrayList<>(result.terms);
        Collections.sort(terms);
        assertThat(terms.size(), equalTo(2));
        assertThat(terms.get(0).field(), equalTo("_field"));
        assertThat(terms.get(0).text(), equalTo("_term1"));
        assertThat(terms.get(1).field(), equalTo("_field"));
        assertThat(terms.get(1).text(), equalTo("_term2"));
    }

    public void testExtractQueryMetadata_blendedTermQuery() {
        Term[] termsArr = new Term[]{new Term("_field", "_term1"), new Term("_field", "_term2")};
        BlendedTermQuery commonTermsQuery = BlendedTermQuery.booleanBlendedQuery(termsArr, false);
        Result result = extractQueryTerms(commonTermsQuery);
        assertThat(result.verified, is(true));
        List<Term> terms = new ArrayList<>(result.terms);
        Collections.sort(terms);
        assertThat(terms.size(), equalTo(2));
        assertThat(terms.get(0).field(), equalTo("_field"));
        assertThat(terms.get(0).text(), equalTo("_term1"));
        assertThat(terms.get(1).field(), equalTo("_field"));
        assertThat(terms.get(1).text(), equalTo("_term2"));
    }

    public void testExtractQueryMetadata_spanTermQuery() {
        // the following span queries aren't exposed in the query dsl and are therefor not supported:
        // 1) SpanPositionRangeQuery
        // 2) PayloadScoreQuery
        // 3) SpanBoostQuery

        // The following span queries can't be supported because of how these queries work:
        // 1) SpanMultiTermQueryWrapper, not supported, because there is no support for MTQ typed queries yet.
        // 2) SpanContainingQuery, is kind of range of spans and we don't know what is between the little and big terms
        // 3) SpanWithinQuery, same reason as SpanContainingQuery
        // 4) FieldMaskingSpanQuery is a tricky query so we shouldn't optimize this

        SpanTermQuery spanTermQuery1 = new SpanTermQuery(new Term("_field", "_short_term"));
        Result result = extractQueryTerms(spanTermQuery1);
        assertThat(result.verified, is(false));
        assertTermsEqual(result.terms, spanTermQuery1.getTerm());
    }

    public void testExtractQueryMetadata_spanNearQuery() {
        SpanTermQuery spanTermQuery1 = new SpanTermQuery(new Term("_field", "_short_term"));
        SpanTermQuery spanTermQuery2 = new SpanTermQuery(new Term("_field", "_very_long_term"));
        SpanNearQuery spanNearQuery = new SpanNearQuery.Builder("_field", true)
                .addClause(spanTermQuery1).addClause(spanTermQuery2).build();

        Result result = extractQueryTerms(spanNearQuery);
        assertThat(result.verified, is(false));
        assertTermsEqual(result.terms, spanTermQuery2.getTerm());
    }

    public void testExtractQueryMetadata_spanOrQuery() {
        SpanTermQuery spanTermQuery1 = new SpanTermQuery(new Term("_field", "_short_term"));
        SpanTermQuery spanTermQuery2 = new SpanTermQuery(new Term("_field", "_very_long_term"));
        SpanOrQuery spanOrQuery = new SpanOrQuery(spanTermQuery1, spanTermQuery2);
        Result result = extractQueryTerms(spanOrQuery);
        assertThat(result.verified, is(false));
        assertTermsEqual(result.terms, spanTermQuery1.getTerm(), spanTermQuery2.getTerm());
    }

    public void testExtractQueryMetadata_spanFirstQuery() {
        SpanTermQuery spanTermQuery1 = new SpanTermQuery(new Term("_field", "_short_term"));
        SpanFirstQuery spanFirstQuery = new SpanFirstQuery(spanTermQuery1, 20);
        Result result = extractQueryTerms(spanFirstQuery);
        assertThat(result.verified, is(false));
        assertTermsEqual(result.terms, spanTermQuery1.getTerm());
    }

    public void testExtractQueryMetadata_spanNotQuery() {
        SpanTermQuery spanTermQuery1 = new SpanTermQuery(new Term("_field", "_short_term"));
        SpanTermQuery spanTermQuery2 = new SpanTermQuery(new Term("_field", "_very_long_term"));
        SpanNotQuery spanNotQuery = new SpanNotQuery(spanTermQuery1, spanTermQuery2);
        Result result = extractQueryTerms(spanNotQuery);
        assertThat(result.verified, is(false));
        assertTermsEqual(result.terms, spanTermQuery1.getTerm());
    }

    public void testExtractQueryMetadata_matchNoDocsQuery() {
        Result result = extractQueryTerms(new MatchNoDocsQuery("sometimes there is no reason at all"));
        assertThat(result.verified, is(false));
        assertEquals(0, result.terms.size());

        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        bq.add(new TermQuery(new Term("field", "value")), BooleanClause.Occur.MUST);
        bq.add(new MatchNoDocsQuery("sometimes there is no reason at all"), BooleanClause.Occur.MUST);
        result = extractQueryTerms(bq.build());
        assertThat(result.verified, is(false));
        assertEquals(0, result.terms.size());

        bq = new BooleanQuery.Builder();
        bq.add(new TermQuery(new Term("field", "value")), BooleanClause.Occur.SHOULD);
        bq.add(new MatchNoDocsQuery("sometimes there is no reason at all"), BooleanClause.Occur.SHOULD);
        result = extractQueryTerms(bq.build());
        assertThat(result.verified, is(true));
        assertTermsEqual(result.terms, new Term("field", "value"));

        DisjunctionMaxQuery disjunctionMaxQuery = new DisjunctionMaxQuery(
                Arrays.asList(new TermQuery(new Term("field", "value")), new MatchNoDocsQuery("sometimes there is no reason at all")),
                1f
        );
        result = extractQueryTerms(disjunctionMaxQuery);
        assertThat(result.verified, is(true));
        assertTermsEqual(result.terms, new Term("field", "value"));
    }

    public void testExtractQueryMetadata_matchAllDocsQuery() {
        Result result = extractQueryTerms(new MatchAllDocsQuery());
        assertThat(result.verified, is(true));
        assertTermsEqual(result.terms, new Term("", ""));

        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        bq.add(new TermQuery(new Term("field", "value")), BooleanClause.Occur.MUST);
        bq.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        result = extractQueryTerms(bq.build());
        assertThat(result.verified, is(false));
        assertTermsEqual(result.terms, new Term("field", "value"));

        bq = new BooleanQuery.Builder();
        bq.add(new TermQuery(new Term("field", "value")), BooleanClause.Occur.SHOULD);
        bq.add(new MatchAllDocsQuery(), BooleanClause.Occur.SHOULD);
        result = extractQueryTerms(bq.build());
        assertThat(result.verified, is(true));
        assertTermsEqual(result.terms, new Term("", ""), new Term("field", "value"));
    }

    public void testExtractQueryMetadata_unsupportedQuery() {
        TermRangeQuery termRangeQuery = new TermRangeQuery("_field", null, null, true, false);
        UnsupportedQueryException e = expectThrows(UnsupportedQueryException.class, () -> extractQueryTerms(termRangeQuery));
        assertThat(e.getUnsupportedQuery(), sameInstance(termRangeQuery));

        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(termQuery1, BooleanClause.Occur.SHOULD);
        builder.add(termRangeQuery, BooleanClause.Occur.SHOULD);
        BooleanQuery bq = builder.build();

        e = expectThrows(UnsupportedQueryException.class, () -> extractQueryTerms(bq));
        assertThat(e.getUnsupportedQuery(), sameInstance(termRangeQuery));
    }

    public void testExtractQueryMetadata_unsupportedQueryInBoolQueryWithMustClauses() {
        TermRangeQuery unsupportedQuery = new TermRangeQuery("_field", null, null, true, false);

        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(termQuery1, BooleanClause.Occur.MUST);
        builder.add(unsupportedQuery, BooleanClause.Occur.MUST);
        BooleanQuery bq1 = builder.build();

        Result result = extractQueryTerms(bq1);
        assertThat(result.verified, is(false));
        assertTermsEqual(result.terms, termQuery1.getTerm());

        TermQuery termQuery2 = new TermQuery(new Term("_field", "_longer_term"));
        builder = new BooleanQuery.Builder();
        builder.add(termQuery1, BooleanClause.Occur.MUST);
        builder.add(termQuery2, BooleanClause.Occur.MUST);
        builder.add(unsupportedQuery, BooleanClause.Occur.MUST);
        bq1 = builder.build();
        result = extractQueryTerms(bq1);
        assertThat(result.verified, is(false));
        assertTermsEqual(result.terms, termQuery2.getTerm());

        builder = new BooleanQuery.Builder();
        builder.add(unsupportedQuery, BooleanClause.Occur.MUST);
        builder.add(unsupportedQuery, BooleanClause.Occur.MUST);
        BooleanQuery bq2 = builder.build();
        UnsupportedQueryException e = expectThrows(UnsupportedQueryException.class, () -> extractQueryTerms(bq2));
        assertThat(e.getUnsupportedQuery(), sameInstance(unsupportedQuery));
    }

    public void testExtractQueryMetadata_disjunctionMaxQuery() {
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term1"));
        TermQuery termQuery2 = new TermQuery(new Term("_field", "_term2"));
        TermQuery termQuery3 = new TermQuery(new Term("_field", "_term3"));
        TermQuery termQuery4 = new TermQuery(new Term("_field", "_term4"));
        DisjunctionMaxQuery disjunctionMaxQuery = new DisjunctionMaxQuery(
                Arrays.asList(termQuery1, termQuery2, termQuery3, termQuery4), 0.1f
        );

        Result result = extractQueryTerms(disjunctionMaxQuery);
        assertThat(result.verified, is(true));
        List<Term> terms = new ArrayList<>(result.terms);
        Collections.sort(terms);
        assertThat(terms.size(), equalTo(4));
        assertThat(terms.get(0).field(), equalTo(termQuery1.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery1.getTerm().bytes()));
        assertThat(terms.get(1).field(), equalTo(termQuery2.getTerm().field()));
        assertThat(terms.get(1).bytes(), equalTo(termQuery2.getTerm().bytes()));
        assertThat(terms.get(2).field(), equalTo(termQuery3.getTerm().field()));
        assertThat(terms.get(2).bytes(), equalTo(termQuery3.getTerm().bytes()));
        assertThat(terms.get(3).field(), equalTo(termQuery4.getTerm().field()));
        assertThat(terms.get(3).bytes(), equalTo(termQuery4.getTerm().bytes()));

        disjunctionMaxQuery = new DisjunctionMaxQuery(
                Arrays.asList(termQuery1, termQuery2, termQuery3, new PhraseQuery("_field", "_term4")), 0.1f
        );

        result = extractQueryTerms(disjunctionMaxQuery);
        assertThat(result.verified, is(false));
        terms = new ArrayList<>(result.terms);
        Collections.sort(terms);
        assertThat(terms.size(), equalTo(4));
        assertThat(terms.get(0).field(), equalTo(termQuery1.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery1.getTerm().bytes()));
        assertThat(terms.get(1).field(), equalTo(termQuery2.getTerm().field()));
        assertThat(terms.get(1).bytes(), equalTo(termQuery2.getTerm().bytes()));
        assertThat(terms.get(2).field(), equalTo(termQuery3.getTerm().field()));
        assertThat(terms.get(2).bytes(), equalTo(termQuery3.getTerm().bytes()));
        assertThat(terms.get(3).field(), equalTo(termQuery4.getTerm().field()));
        assertThat(terms.get(3).bytes(), equalTo(termQuery4.getTerm().bytes()));
    }

    public void testCreateQueryMetadataQuery() throws Exception {
        MemoryIndex memoryIndex = new MemoryIndex(false);
        memoryIndex.addField("field1", "the quick brown fox jumps over the lazy dog", new WhitespaceAnalyzer());
        memoryIndex.addField("field2", "some more text", new WhitespaceAnalyzer());
        memoryIndex.addField("_field3", "unhide me", new WhitespaceAnalyzer());
        memoryIndex.addField("field4", "123", new WhitespaceAnalyzer());

        IndexReader indexReader = memoryIndex.createSearcher().getIndexReader();
        TermsQuery query = (TermsQuery)
                createQueryTermsQuery(indexReader, QUERY_TERMS_FIELD, new Term(EXTRACTION_RESULT_FIELD, EXTRACTION_FAILED));

        PrefixCodedTerms terms = query.getTermData();
        assertThat(terms.size(), equalTo(16L));
        PrefixCodedTerms.TermIterator termIterator = terms.iterator();
        assertTermIterator(termIterator, "\u0000", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "_field3\u0000me", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "_field3\u0000unhide", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field1\u0000brown", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field1\u0000dog", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field1\u0000fox", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field1\u0000jumps", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field1\u0000lazy", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field1\u0000over", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field1\u0000quick", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field1\u0000the", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field2\u0000more", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field2\u0000some", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field2\u0000text", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, "field4\u0000123", QUERY_TERMS_FIELD);
        assertTermIterator(termIterator, EXTRACTION_FAILED, EXTRACTION_RESULT_FIELD);
    }

    public void testSelectTermsListWithHighestSumOfTermLength() {
        Set<Term> terms1 = new HashSet<>();
        int shortestTerms1Length = Integer.MAX_VALUE;
        int sumTermLength = randomIntBetween(1, 128);
        while (sumTermLength > 0) {
            int length = randomInt(sumTermLength);
            shortestTerms1Length = Math.min(shortestTerms1Length, length);
            terms1.add(new Term("field", randomAsciiOfLength(length)));
            sumTermLength -= length;
        }

        Set<Term> terms2 = new HashSet<>();
        int shortestTerms2Length = Integer.MAX_VALUE;
        sumTermLength = randomIntBetween(1, 128);
        while (sumTermLength > 0) {
            int length = randomInt(sumTermLength);
            shortestTerms2Length = Math.min(shortestTerms2Length, length);
            terms2.add(new Term("field", randomAsciiOfLength(length)));
            sumTermLength -= length;
        }

        Set<Term> result = selectTermListWithTheLongestShortestTerm(terms1, terms2);
        Set<Term> expected = shortestTerms1Length >= shortestTerms2Length ? terms1 : terms2;
        assertThat(result, sameInstance(expected));
    }

    private void assertTermIterator(PrefixCodedTerms.TermIterator termIterator, String expectedValue, String expectedField) {
        assertThat(termIterator.next().utf8ToString(), equalTo(expectedValue));
        assertThat(termIterator.field(), equalTo(expectedField));
    }

    private static void assertTermsEqual(Set<Term> actual, Term... expected) {
        assertEquals(new HashSet<>(Arrays.asList(expected)), actual);
    }

}
