/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.queries;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.mapper.SourceToParse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.index.search.ESToParentBlockJoinQuery;
import org.elasticsearch.inference.InputType;
import org.elasticsearch.inference.SimilarityMeasure;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.elasticsearch.test.index.IndexVersionUtils;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.elasticsearch.xpack.core.inference.action.InferenceAction;
import org.elasticsearch.xpack.core.inference.results.SparseEmbeddingResults;
import org.elasticsearch.xpack.core.ml.inference.results.TextEmbeddingResults;
import org.elasticsearch.xpack.core.ml.inference.results.TextExpansionResults;
import org.elasticsearch.xpack.inference.InferencePlugin;
import org.elasticsearch.xpack.inference.mapper.SemanticTextField;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.apache.lucene.search.BooleanClause.Occur.FILTER;
import static org.apache.lucene.search.BooleanClause.Occur.MUST;
import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;
import static org.elasticsearch.index.IndexVersions.NEW_SPARSE_VECTOR;
import static org.elasticsearch.xpack.core.ml.inference.trainedmodel.InferenceConfig.DEFAULT_RESULTS_FIELD;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

public class SemanticQueryBuilderTests extends AbstractQueryTestCase<SemanticQueryBuilder> {
    private static final String SEMANTIC_TEXT_FIELD = "semantic";
    private static final float TOKEN_WEIGHT = 0.5f;
    private static final int QUERY_TOKEN_LENGTH = 4;
    private static final int TEXT_EMBEDDING_DIMENSION_COUNT = 10;
    private static final String INFERENCE_ID = "test_service";

    private static InferenceResultType inferenceResultType;

    private enum InferenceResultType {
        NONE,
        SPARSE_EMBEDDING,
        TEXT_EMBEDDING
    }

    private Integer queryTokenCount;

    @BeforeClass
    public static void setInferenceResultType() {
        // The inference result type is a class variable because it is used when initializing additional mappings,
        // which happens once per test suite run in AbstractBuilderTestCase#beforeTest as part of service holder creation.
        inferenceResultType = randomFrom(InferenceResultType.values());
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        queryTokenCount = null;
    }

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return List.of(InferencePlugin.class);
    }

    @Override
    protected Settings createTestIndexSettings() {
        // Randomize index version within compatible range
        // we have to prefer CURRENT since with the range of versions we support it's rather unlikely to get the current actually.
        IndexVersion indexVersionCreated = randomBoolean()
            ? IndexVersion.current()
            : IndexVersionUtils.randomVersionBetween(random(), NEW_SPARSE_VECTOR, IndexVersion.current());
        return Settings.builder().put(IndexMetadata.SETTING_VERSION_CREATED, indexVersionCreated).build();
    }

    @Override
    protected void initializeAdditionalMappings(MapperService mapperService) throws IOException {
        mapperService.merge(
            "_doc",
            new CompressedXContent(
                Strings.toString(PutMappingRequest.simpleMapping(SEMANTIC_TEXT_FIELD, "type=semantic_text,inference_id=" + INFERENCE_ID))
            ),
            MapperService.MergeReason.MAPPING_UPDATE
        );

        applyRandomInferenceResults(mapperService);
    }

    private void applyRandomInferenceResults(MapperService mapperService) throws IOException {
        // Parse random inference results (or no inference results) to set up the dynamic inference result mappings under the semantic text
        // field
        SourceToParse sourceToParse = buildSemanticTextFieldWithInferenceResults(inferenceResultType);
        if (sourceToParse != null) {
            ParsedDocument parsedDocument = mapperService.documentMapper().parse(sourceToParse);
            mapperService.merge(
                "_doc",
                parsedDocument.dynamicMappingsUpdate().toCompressedXContent(),
                MapperService.MergeReason.MAPPING_UPDATE
            );
        }
    }

    @Override
    protected SemanticQueryBuilder doCreateTestQueryBuilder() {
        queryTokenCount = randomIntBetween(1, 5);
        List<String> queryTokens = new ArrayList<>(queryTokenCount);
        for (int i = 0; i < queryTokenCount; i++) {
            queryTokens.add(randomAlphaOfLength(QUERY_TOKEN_LENGTH));
        }

        SemanticQueryBuilder builder = new SemanticQueryBuilder(SEMANTIC_TEXT_FIELD, String.join(" ", queryTokens));
        if (randomBoolean()) {
            builder.boost((float) randomDoubleBetween(0.1, 10.0, true));
        }
        if (randomBoolean()) {
            builder.queryName(randomAlphaOfLength(4));
        }

        return builder;
    }

    @Override
    protected void doAssertLuceneQuery(SemanticQueryBuilder queryBuilder, Query query, SearchExecutionContext context) throws IOException {
        assertThat(queryTokenCount, notNullValue());
        assertThat(query, notNullValue());
        assertThat(query, instanceOf(ESToParentBlockJoinQuery.class));

        ESToParentBlockJoinQuery nestedQuery = (ESToParentBlockJoinQuery) query;
        assertThat(nestedQuery.getScoreMode(), equalTo(ScoreMode.Total));

        switch (inferenceResultType) {
            case NONE -> assertThat(nestedQuery.getChildQuery(), instanceOf(MatchNoDocsQuery.class));
            case SPARSE_EMBEDDING -> assertSparseEmbeddingLuceneQuery(nestedQuery.getChildQuery());
            case TEXT_EMBEDDING -> assertTextEmbeddingLuceneQuery(nestedQuery.getChildQuery());
        }
    }

    private void assertSparseEmbeddingLuceneQuery(Query query) {
        Query innerQuery = assertOuterBooleanQuery(query);
        assertThat(innerQuery, instanceOf(BooleanQuery.class));

        BooleanQuery innerBooleanQuery = (BooleanQuery) innerQuery;
        assertThat(innerBooleanQuery.clauses().size(), equalTo(queryTokenCount));
        innerBooleanQuery.forEach(c -> {
            assertThat(c.getOccur(), equalTo(SHOULD));
            assertThat(c.getQuery(), instanceOf(BoostQuery.class));
            assertThat(((BoostQuery) c.getQuery()).getBoost(), equalTo(TOKEN_WEIGHT));
        });
    }

    private void assertTextEmbeddingLuceneQuery(Query query) {
        Query innerQuery = assertOuterBooleanQuery(query);
        assertThat(innerQuery, instanceOf(KnnFloatVectorQuery.class));
    }

    private Query assertOuterBooleanQuery(Query query) {
        assertThat(query, instanceOf(BooleanQuery.class));
        BooleanQuery outerBooleanQuery = (BooleanQuery) query;

        List<BooleanClause> outerMustClauses = new ArrayList<>();
        List<BooleanClause> outerFilterClauses = new ArrayList<>();
        for (BooleanClause clause : outerBooleanQuery.clauses()) {
            BooleanClause.Occur occur = clause.getOccur();
            if (occur == MUST) {
                outerMustClauses.add(clause);
            } else if (occur == FILTER) {
                outerFilterClauses.add(clause);
            } else {
                fail("Unexpected boolean " + occur + " clause");
            }
        }

        assertThat(outerMustClauses.size(), equalTo(1));
        assertThat(outerFilterClauses.size(), equalTo(1));

        return outerMustClauses.get(0).getQuery();
    }

    @Override
    protected boolean canSimulateMethod(Method method, Object[] args) throws NoSuchMethodException {
        return method.equals(Client.class.getMethod("execute", ActionType.class, ActionRequest.class, ActionListener.class))
            && (args[0] instanceof InferenceAction);
    }

    @Override
    protected Object simulateMethod(Method method, Object[] args) {
        InferenceAction.Request request = (InferenceAction.Request) args[1];
        assertThat(request.getTaskType(), equalTo(TaskType.ANY));
        assertThat(request.getInputType(), equalTo(InputType.SEARCH));

        List<String> input = request.getInput();
        assertThat(input.size(), equalTo(1));
        String query = input.get(0);

        InferenceAction.Response response = switch (inferenceResultType) {
            case NONE -> randomBoolean() ? generateSparseEmbeddingInferenceResponse(query) : generateTextEmbeddingInferenceResponse();
            case SPARSE_EMBEDDING -> generateSparseEmbeddingInferenceResponse(query);
            case TEXT_EMBEDDING -> generateTextEmbeddingInferenceResponse();
        };

        @SuppressWarnings("unchecked")  // We matched the method above.
        ActionListener<InferenceAction.Response> listener = (ActionListener<InferenceAction.Response>) args[2];
        listener.onResponse(response);

        return null;
    }

    private InferenceAction.Response generateSparseEmbeddingInferenceResponse(String query) {
        List<TextExpansionResults.WeightedToken> weightedTokens = Arrays.stream(query.split("\\s+"))
            .map(s -> new TextExpansionResults.WeightedToken(s, TOKEN_WEIGHT))
            .toList();
        TextExpansionResults textExpansionResults = new TextExpansionResults(DEFAULT_RESULTS_FIELD, weightedTokens, false);

        return new InferenceAction.Response(SparseEmbeddingResults.of(List.of(textExpansionResults)));
    }

    private InferenceAction.Response generateTextEmbeddingInferenceResponse() {
        double[] inference = new double[TEXT_EMBEDDING_DIMENSION_COUNT];
        Arrays.fill(inference, 1.0);
        TextEmbeddingResults textEmbeddingResults = new TextEmbeddingResults(DEFAULT_RESULTS_FIELD, inference, false);

        return new InferenceAction.Response(
            org.elasticsearch.xpack.core.inference.results.TextEmbeddingResults.of(List.of(textEmbeddingResults))
        );
    }

    @Override
    public void testMustRewrite() throws IOException {
        SearchExecutionContext context = createSearchExecutionContext();
        SemanticQueryBuilder builder = new SemanticQueryBuilder("foo", "bar");
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> builder.toQuery(context));
        assertThat(e.getMessage(), equalTo(SemanticQueryBuilder.NAME + " should have been rewritten to another query type"));
    }

    public void testIllegalValues() {
        {
            IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> new SemanticQueryBuilder(null, "query"));
            assertThat(e.getMessage(), equalTo("[semantic] requires a fieldName"));
        }
        {
            IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> new SemanticQueryBuilder("fieldName", null));
            assertThat(e.getMessage(), equalTo("[semantic] requires a query value"));
        }
    }

    public void testToXContent() throws IOException {
        QueryBuilder queryBuilder = new SemanticQueryBuilder("foo", "bar");
        checkGeneratedJson("""
            {
              "semantic": {
                "foo": {
                  "query": "bar"
                }
              }
            }""", queryBuilder);
    }

    private static SourceToParse buildSemanticTextFieldWithInferenceResults(InferenceResultType inferenceResultType) throws IOException {
        SemanticTextField.ModelSettings modelSettings = switch (inferenceResultType) {
            case NONE -> null;
            case SPARSE_EMBEDDING -> new SemanticTextField.ModelSettings(TaskType.SPARSE_EMBEDDING, null, null);
            case TEXT_EMBEDDING -> new SemanticTextField.ModelSettings(
                TaskType.TEXT_EMBEDDING,
                TEXT_EMBEDDING_DIMENSION_COUNT,
                SimilarityMeasure.COSINE
            );
        };

        SourceToParse sourceToParse = null;
        if (modelSettings != null) {
            SemanticTextField semanticTextField = new SemanticTextField(
                SEMANTIC_TEXT_FIELD,
                List.of(),
                new SemanticTextField.InferenceResult(INFERENCE_ID, modelSettings, List.of()),
                XContentType.JSON
            );

            XContentBuilder builder = JsonXContent.contentBuilder().startObject();
            builder.field(semanticTextField.fieldName());
            builder.value(semanticTextField);
            builder.endObject();
            sourceToParse = new SourceToParse("test", BytesReference.bytes(builder), XContentType.JSON);
        }

        return sourceToParse;
    }
}
