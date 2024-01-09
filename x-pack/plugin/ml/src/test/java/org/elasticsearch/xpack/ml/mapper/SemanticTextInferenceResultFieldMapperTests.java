/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.mapper;

import org.apache.lucene.search.join.QueryBitSetProducer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.index.IndexVersions;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.DocumentParsingException;
import org.elasticsearch.index.mapper.LuceneDocument;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.MetadataMapperTestCase;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.LeafNestedDocuments;
import org.elasticsearch.search.NestedDocuments;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xpack.core.inference.results.SparseEmbeddingResults;
import org.elasticsearch.xpack.ml.MachineLearning;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;

public class SemanticTextInferenceResultFieldMapperTests extends MetadataMapperTestCase {
    private record SemanticTextInferenceResults(String fieldName, SparseEmbeddingResults sparseEmbeddingResults, List<String> text) {
        private SemanticTextInferenceResults {
            if (sparseEmbeddingResults.embeddings().size() != text.size()) {
                throw new IllegalArgumentException("Sparse embeddings and text must be the same size");
            }
        }
    }

    private record VisitedChildDocInfo(String path, int sparseVectorDims) {}

    @Override
    protected String fieldName() {
        return SemanticTextInferenceResultFieldMapper.NAME;
    }

    @Override
    protected boolean isConfigurable() {
        return false;
    }

    @Override
    protected boolean isSupportedOn(IndexVersion version) {
        return version.onOrAfter(IndexVersions.ES_VERSION_8_13); // TODO: Switch to ES_VERSION_8_14 when available
    }

    @Override
    protected void registerParameters(ParameterChecker checker) throws IOException {

    }

    @Override
    protected Collection<? extends Plugin> getPlugins() {
        return List.of(new MachineLearning(Settings.EMPTY));
    }

    public void testSuccessfulParse() throws IOException {
        final String fieldName1 = randomAlphaOfLengthBetween(5, 15);
        final String fieldName2 = randomAlphaOfLengthBetween(5, 15);

        DocumentMapper documentMapper = createDocumentMapper(mapping(b -> {
            addSemanticTextMapping(b, fieldName1, randomAlphaOfLength(8));
            addSemanticTextMapping(b, fieldName2, randomAlphaOfLength(8));
        }));
        ParsedDocument doc = documentMapper.parse(
            source(
                b -> addSemanticTextInferenceResults(
                    b,
                    List.of(
                        generateSemanticTextinferenceResults(fieldName1, List.of("a b", "c")),
                        generateSemanticTextinferenceResults(fieldName2, List.of("d e f"))
                    )
                )
            )
        );

        Set<VisitedChildDocInfo> visitedChildDocs = new HashSet<>();
        Set<VisitedChildDocInfo> expectedVisitedChildDocs = Set.of(
            new VisitedChildDocInfo(fieldName1, 2),
            new VisitedChildDocInfo(fieldName1, 1),
            new VisitedChildDocInfo(fieldName2, 3)
        );

        List<LuceneDocument> luceneDocs = doc.docs();
        assertEquals(4, luceneDocs.size());
        assertValidChildDoc(luceneDocs.get(0), doc.rootDoc(), visitedChildDocs);
        assertValidChildDoc(luceneDocs.get(1), doc.rootDoc(), visitedChildDocs);
        assertValidChildDoc(luceneDocs.get(2), doc.rootDoc(), visitedChildDocs);
        assertEquals(doc.rootDoc(), luceneDocs.get(3));
        assertNull(luceneDocs.get(3).getParent());
        assertEquals(expectedVisitedChildDocs, visitedChildDocs);

        MapperService nestedMapperService = createMapperService(mapping(b -> {
            addInferenceResultsNestedMapping(b, fieldName1);
            addInferenceResultsNestedMapping(b, fieldName2);
        }));
        withLuceneIndex(nestedMapperService, iw -> iw.addDocuments(doc.docs()), reader -> {
            NestedDocuments nested = new NestedDocuments(
                nestedMapperService.mappingLookup(),
                QueryBitSetProducer::new,
                IndexVersion.current()
            );
            LeafNestedDocuments leaf = nested.getLeafNestedDocuments(reader.leaves().get(0));

            Set<SearchHit.NestedIdentity> visitedNestedIdentities = new HashSet<>();
            Set<SearchHit.NestedIdentity> expectedVisitedNestedIdentities = Set.of(
                new SearchHit.NestedIdentity(fieldName1, 0, null),
                new SearchHit.NestedIdentity(fieldName1, 1, null),
                new SearchHit.NestedIdentity(fieldName2, 0, null)
            );

            assertChildLeafNestedDocument(leaf, 0, 3, visitedNestedIdentities);
            assertChildLeafNestedDocument(leaf, 1, 3, visitedNestedIdentities);
            assertChildLeafNestedDocument(leaf, 2, 3, visitedNestedIdentities);
            assertEquals(expectedVisitedNestedIdentities, visitedNestedIdentities);

            assertNull(leaf.advance(3));
            assertEquals(3, leaf.doc());
            assertEquals(3, leaf.rootDoc());
            assertNull(leaf.nestedIdentity());
        });
    }

    public void testMissingSubfields() throws IOException {
        final String fieldName = randomAlphaOfLengthBetween(5, 15);

        DocumentMapper documentMapper = createDocumentMapper(mapping(b -> addSemanticTextMapping(b, fieldName, randomAlphaOfLength(8))));

        {
            DocumentParsingException ex = expectThrows(
                DocumentParsingException.class,
                DocumentParsingException.class,
                () -> documentMapper.parse(
                    source(
                        b -> addSemanticTextInferenceResults(
                            b,
                            List.of(generateSemanticTextinferenceResults(fieldName, List.of("a b"))),
                            false,
                            true,
                            null
                        )
                    )
                )
            );
            assertThat(
                ex.getMessage(),
                containsString("Missing required subfields: [" + SemanticTextInferenceResultFieldMapper.SPARSE_VECTOR_SUBFIELD_NAME + "]")
            );
        }

        {
            DocumentParsingException ex = expectThrows(
                DocumentParsingException.class,
                DocumentParsingException.class,
                () -> documentMapper.parse(
                    source(
                        b -> addSemanticTextInferenceResults(
                            b,
                            List.of(generateSemanticTextinferenceResults(fieldName, List.of("a b"))),
                            true,
                            false,
                            null
                        )
                    )
                )
            );
            assertThat(
                ex.getMessage(),
                containsString("Missing required subfields: [" + SemanticTextInferenceResultFieldMapper.TEXT_SUBFIELD_NAME + "]")
            );
        }

        {
            DocumentParsingException ex = expectThrows(
                DocumentParsingException.class,
                DocumentParsingException.class,
                () -> documentMapper.parse(
                    source(
                        b -> addSemanticTextInferenceResults(
                            b,
                            List.of(generateSemanticTextinferenceResults(fieldName, List.of("a b"))),
                            false,
                            false,
                            null
                        )
                    )
                )
            );
            assertThat(
                ex.getMessage(),
                containsString(
                    "Missing required subfields: ["
                        + SemanticTextInferenceResultFieldMapper.SPARSE_VECTOR_SUBFIELD_NAME
                        + ", "
                        + SemanticTextInferenceResultFieldMapper.TEXT_SUBFIELD_NAME
                        + "]"
                )
            );
        }

        // TODO: Check for missing subfields in sparse_embedding object
    }

    public void testExtraSubfields() throws IOException {
        final String fieldName = randomAlphaOfLengthBetween(5, 15);
        final List<SemanticTextInferenceResults> semanticTextInferenceResultsList = List.of(
            generateSemanticTextinferenceResults(fieldName, List.of("a b"))
        );

        DocumentMapper documentMapper = createDocumentMapper(mapping(b -> addSemanticTextMapping(b, fieldName, randomAlphaOfLength(8))));

        Consumer<ParsedDocument> checkParsedDocument = d -> {
            Set<VisitedChildDocInfo> visitedChildDocs = new HashSet<>();
            Set<VisitedChildDocInfo> expectedVisitedChildDocs = Set.of(new VisitedChildDocInfo(fieldName, 2));

            List<LuceneDocument> luceneDocs = d.docs();
            assertEquals(2, luceneDocs.size());
            assertValidChildDoc(luceneDocs.get(0), d.rootDoc(), visitedChildDocs);
            assertEquals(d.rootDoc(), luceneDocs.get(1));
            assertNull(luceneDocs.get(1).getParent());
            assertEquals(expectedVisitedChildDocs, visitedChildDocs);
        };

        {
            ParsedDocument doc = documentMapper.parse(
                source(
                    b -> addSemanticTextInferenceResults(
                        b,
                        semanticTextInferenceResultsList,
                        true,
                        true,
                        Map.of("extra_key", "extra_value")
                    )
                )
            );

            checkParsedDocument.accept(doc);
            LuceneDocument childDoc = doc.docs().get(0);
            assertEquals(0, childDoc.getFields(childDoc.getPath() + ".extra_key").size());
        }
        {
            ParsedDocument doc = documentMapper.parse(
                source(
                    b -> addSemanticTextInferenceResults(
                        b,
                        semanticTextInferenceResultsList,
                        true,
                        true,
                        Map.of("extra_key", Map.of("k1", "v1"))
                    )
                )
            );

            checkParsedDocument.accept(doc);
            LuceneDocument childDoc = doc.docs().get(0);
            assertEquals(0, childDoc.getFields(childDoc.getPath() + ".extra_key").size());
        }
        {
            ParsedDocument doc = documentMapper.parse(
                source(
                    b -> addSemanticTextInferenceResults(
                        b,
                        semanticTextInferenceResultsList,
                        true,
                        true,
                        Map.of("extra_key", List.of("v1"))
                    )
                )
            );

            checkParsedDocument.accept(doc);
            LuceneDocument childDoc = doc.docs().get(0);
            assertEquals(0, childDoc.getFields(childDoc.getPath() + ".extra_key").size());
        }
        {
            Map<String, Object> extraSubfields = new HashMap<>();
            extraSubfields.put("extra_key", null);

            ParsedDocument doc = documentMapper.parse(
                source(b -> addSemanticTextInferenceResults(b, semanticTextInferenceResultsList, true, true, extraSubfields))
            );

            checkParsedDocument.accept(doc);
            LuceneDocument childDoc = doc.docs().get(0);
            assertEquals(0, childDoc.getFields(childDoc.getPath() + ".extra_key").size());
        }

        // TODO: test parsing a document with an extra subfield containing invalid JSON
    }

    private static void addSemanticTextMapping(XContentBuilder mappingBuilder, String fieldName, String modelId) throws IOException {
        mappingBuilder.startObject(fieldName);
        mappingBuilder.field("type", SemanticTextFieldMapper.CONTENT_TYPE);
        mappingBuilder.field("model_id", modelId);
        mappingBuilder.endObject();
    }

    private static SemanticTextInferenceResults generateSemanticTextinferenceResults(String semanticTextFieldName, List<String> chunks) {
        List<SparseEmbeddingResults.Embedding> embeddings = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            String[] tokens = chunk.split("\\s+");
            List<SparseEmbeddingResults.WeightedToken> weightedTokens = Arrays.stream(tokens)
                .map(t -> new SparseEmbeddingResults.WeightedToken(t, randomFloat()))
                .toList();

            embeddings.add(new SparseEmbeddingResults.Embedding(weightedTokens, false));
        }

        return new SemanticTextInferenceResults(semanticTextFieldName, new SparseEmbeddingResults(embeddings), chunks);
    }

    private static void addSemanticTextInferenceResults(
        XContentBuilder sourceBuilder,
        List<SemanticTextInferenceResults> semanticTextInferenceResults
    ) throws IOException {
        addSemanticTextInferenceResults(sourceBuilder, semanticTextInferenceResults, true, true, null);
    }

    private static void addSemanticTextInferenceResults(
        XContentBuilder sourceBuilder,
        List<SemanticTextInferenceResults> semanticTextInferenceResults,
        boolean includeSparseVectorSubfield,
        boolean includeTextSubfield,
        Map<String, Object> extraSubfields
    ) throws IOException {

        Map<String, List<Map<String, Object>>> inferenceResultsMap = new HashMap<>();
        for (SemanticTextInferenceResults semanticTextInferenceResult : semanticTextInferenceResults) {
            List<Map<String, Object>> parsedInferenceResults = new ArrayList<>(semanticTextInferenceResult.text().size());

            Iterator<SparseEmbeddingResults.Embedding> embeddingsIterator = semanticTextInferenceResult.sparseEmbeddingResults()
                .embeddings()
                .iterator();
            Iterator<String> textIterator = semanticTextInferenceResult.text().iterator();
            while (embeddingsIterator.hasNext() && textIterator.hasNext()) {
                SparseEmbeddingResults.Embedding embedding = embeddingsIterator.next();
                String text = textIterator.next();

                Map<String, Object> subfieldMap = new HashMap<>();
                if (includeSparseVectorSubfield) {
                    subfieldMap.put(SemanticTextInferenceResultFieldMapper.SPARSE_VECTOR_SUBFIELD_NAME, embedding.asMap());
                }
                if (includeTextSubfield) {
                    subfieldMap.put(SemanticTextInferenceResultFieldMapper.TEXT_SUBFIELD_NAME, text);
                }
                if (extraSubfields != null) {
                    subfieldMap.putAll(extraSubfields);
                }

                parsedInferenceResults.add(subfieldMap);
            }

            inferenceResultsMap.put(semanticTextInferenceResult.fieldName(), parsedInferenceResults);
        }

        sourceBuilder.field(SemanticTextInferenceResultFieldMapper.NAME, inferenceResultsMap);
    }

    private static void addInferenceResultsNestedMapping(XContentBuilder mappingBuilder, String semanticTextFieldName) throws IOException {
        mappingBuilder.startObject(semanticTextFieldName);
        mappingBuilder.field("type", "nested");
        mappingBuilder.startObject("properties");
        mappingBuilder.startObject(SemanticTextInferenceResultFieldMapper.SPARSE_VECTOR_SUBFIELD_NAME);
        mappingBuilder.startObject("properties");
        mappingBuilder.startObject(SparseEmbeddingResults.Embedding.EMBEDDING);
        mappingBuilder.field("type", "sparse_vector");
        mappingBuilder.endObject();
        mappingBuilder.endObject();
        mappingBuilder.endObject();
        mappingBuilder.startObject(SemanticTextInferenceResultFieldMapper.TEXT_SUBFIELD_NAME);
        mappingBuilder.field("type", "text");
        mappingBuilder.field("index", false);
        mappingBuilder.endObject();
        mappingBuilder.endObject();
        mappingBuilder.endObject();
    }

    private static void assertValidChildDoc(
        LuceneDocument childDoc,
        LuceneDocument expectedParent,
        Set<VisitedChildDocInfo> visitedChildDocs
    ) {
        assertEquals(expectedParent, childDoc.getParent());
        visitedChildDocs.add(
            new VisitedChildDocInfo(
                childDoc.getPath(),
                childDoc.getFields(
                    childDoc.getPath()
                        + "."
                        + SemanticTextInferenceResultFieldMapper.SPARSE_VECTOR_SUBFIELD_NAME
                        + "."
                        + SparseEmbeddingResults.Embedding.EMBEDDING
                ).size()
            )
        );
    }

    private static void assertChildLeafNestedDocument(
        LeafNestedDocuments leaf,
        int advanceToDoc,
        int expectedRootDoc,
        Set<SearchHit.NestedIdentity> visitedNestedIdentities
    ) throws IOException {

        assertNotNull(leaf.advance(advanceToDoc));
        assertEquals(advanceToDoc, leaf.doc());
        assertEquals(expectedRootDoc, leaf.rootDoc());
        assertNotNull(leaf.nestedIdentity());
        visitedNestedIdentities.add(leaf.nestedIdentity());
    }
}
