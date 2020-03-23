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
package org.elasticsearch.search.aggregations.metrics;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocValuesFieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.elasticsearch.common.CheckedConsumer;
import org.elasticsearch.common.TriConsumer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.NumberFieldMapper;
import org.elasticsearch.index.mapper.NumberFieldMapper.NumberType;
import org.elasticsearch.script.MockScriptEngine;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptModule;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregatorTestCase;
import org.elasticsearch.search.aggregations.support.AggregationInspectionHelper;
import org.elasticsearch.search.aggregations.support.CoreValuesSourceType;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;
import org.elasticsearch.search.lookup.LeafDocLookup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.search.aggregations.AggregationBuilders.sum;

public class SumAggregatorTests extends AggregatorTestCase {

    private static final String FIELD_NAME = "field";
    private static final String VALUE_SCRIPT_NAME = "value_script";
    private static final String FIELD_SCRIPT_NAME = "field_script";

    public void testNoDocs() throws IOException {
        testCase(new MatchAllDocsQuery(), iw -> {
            // Intentionally not writing any docs
        }, count -> {
            assertEquals(0L, count.getValue(), 0d);
            assertFalse(AggregationInspectionHelper.hasValue(count));
        });
    }

    public void testNoMatchingField() throws IOException {
        testCase(new MatchAllDocsQuery(), iw -> {
            iw.addDocument(singleton(new NumericDocValuesField("wrong_number", 7)));
            iw.addDocument(singleton(new NumericDocValuesField("wrong_number", 1)));
        }, count -> {
            assertEquals(0L, count.getValue(), 0d);
            assertFalse(AggregationInspectionHelper.hasValue(count));
        });
    }

    public void testNumericDocValues() throws IOException {
        testCase(new MatchAllDocsQuery(), iw -> {
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 1)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 2)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 1)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 2)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 1)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 2)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 1)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 2)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 1)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 2)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 1)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 2)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 1)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 2)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 1)));
            iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, 2)));
        }, count -> {
            assertEquals(24L, count.getValue(), 0d);
            assertTrue(AggregationInspectionHelper.hasValue(count));
        });
    }

    public void testSortedNumericDocValues() throws IOException {
        testCase(new DocValuesFieldExistsQuery(FIELD_NAME), iw -> {
            iw.addDocument(Arrays.asList(new SortedNumericDocValuesField(FIELD_NAME, 3),
                new SortedNumericDocValuesField(FIELD_NAME, 4)));
            iw.addDocument(Arrays.asList(new SortedNumericDocValuesField(FIELD_NAME, 3),
                new SortedNumericDocValuesField(FIELD_NAME, 4)));
            iw.addDocument(singleton(new SortedNumericDocValuesField(FIELD_NAME, 1)));
        }, count -> {
            assertEquals(15L, count.getValue(), 0d);
            assertTrue(AggregationInspectionHelper.hasValue(count));
        });
    }

    public void testQueryFiltering() throws IOException {
        testCase(new TermQuery(new Term("match", "yes")), iw -> {
            iw.addDocument(Arrays.asList(new StringField("match", "yes", Field.Store.NO), new NumericDocValuesField(FIELD_NAME, 1)));
            iw.addDocument(Arrays.asList(new StringField("match", "no", Field.Store.NO), new NumericDocValuesField(FIELD_NAME, 2)));
            iw.addDocument(Arrays.asList(new StringField("match", "yes", Field.Store.NO), new NumericDocValuesField(FIELD_NAME, 3)));
            iw.addDocument(Arrays.asList(new StringField("match", "no", Field.Store.NO), new NumericDocValuesField(FIELD_NAME, 4)));
            iw.addDocument(Arrays.asList(new StringField("match", "yes", Field.Store.NO), new NumericDocValuesField(FIELD_NAME, 5)));
        }, count -> {
            assertEquals(9L, count.getValue(), 0d);
            assertTrue(AggregationInspectionHelper.hasValue(count));
        });
    }

    public void testStringField() throws IOException {
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> {
            testCase(new MatchAllDocsQuery(), iw -> {
                iw.addDocument(singleton(new SortedDocValuesField(FIELD_NAME, new BytesRef("1"))));
            }, count -> {
                assertEquals(0L, count.getValue(), 0d);
                assertFalse(AggregationInspectionHelper.hasValue(count));
            });
        });
        assertEquals("unexpected docvalues type SORTED for field 'field' (expected one of [SORTED_NUMERIC, NUMERIC]). " +
            "Re-index with correct docvalues type.", e.getMessage());
    }

    public void testSummationAccuracy() throws IOException {
        // Summing up a normal array and expect an accurate value
        double[] values = new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7};
        verifySummationOfDoubles(values, 15.3, 0d);

        // Summing up an array which contains NaN and infinities and expect a result same as naive summation
        int n = randomIntBetween(5, 10);
        values = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            values[i] = frequently()
                ? randomFrom(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
                : randomDoubleBetween(Double.MIN_VALUE, Double.MAX_VALUE, true);
            sum += values[i];
        }
        verifySummationOfDoubles(values, sum, 1e-10);

        // Summing up some big double values and expect infinity result
        n = randomIntBetween(5, 10);
        double[] largeValues = new double[n];
        for (int i = 0; i < n; i++) {
            largeValues[i] = Double.MAX_VALUE;
        }
        verifySummationOfDoubles(largeValues, Double.POSITIVE_INFINITY, 0d);

        for (int i = 0; i < n; i++) {
            largeValues[i] = -Double.MAX_VALUE;
        }
        verifySummationOfDoubles(largeValues, Double.NEGATIVE_INFINITY, 0d);
    }

    private void verifySummationOfDoubles(double[] values, double expected, double delta) throws IOException {
        testCase(new MatchAllDocsQuery(),
            sum("_name").field(FIELD_NAME),
            iw -> {
                for (double value : values) {
                    iw.addDocument(singleton(new NumericDocValuesField(FIELD_NAME, NumericUtils.doubleToSortableLong(value))));
                }
            },
            result -> assertEquals(expected, result.getValue(), delta),
            singleton(defaultFieldType(NumberType.DOUBLE))
        );
    }

    public void testUnmapped() throws IOException {
        sumRandomDocsTestCase(randomIntBetween(1, 5),
            sum("_name").field("unknown_field"),
            (sum, docs, result) -> {
                assertEquals(0d, result.getValue(), 0d);
                assertFalse(AggregationInspectionHelper.hasValue(result));
            }
        );
    }

    public void testPartiallyUnmapped() throws IOException {
        final MappedFieldType fieldType = new NumberFieldMapper.NumberFieldType(NumberType.LONG);
        fieldType.setName(FIELD_NAME);
        fieldType.setHasDocValues(true);

        final SumAggregationBuilder builder = sum("_name")
            .field(fieldType.name());

        final int numDocs = randomIntBetween(10, 100);
        final List<Set<IndexableField>> docs = new ArrayList<>(numDocs);
        int sum = 0;
        for (int i = 0; i < numDocs; i++) {
            final long value = randomLongBetween(0, 1000);
            sum += value;
            docs.add(singleton(new NumericDocValuesField(fieldType.name(), value)));
        }

        try (Directory mappedDirectory = newDirectory(); Directory unmappedDirectory = newDirectory()) {
            try (RandomIndexWriter mappedWriter = new RandomIndexWriter(random(), mappedDirectory)) {
                mappedWriter.addDocuments(docs);
            }

            new RandomIndexWriter(random(), unmappedDirectory).close();

            try (IndexReader mappedReader = DirectoryReader.open(mappedDirectory);
                 IndexReader unmappedReader =  DirectoryReader.open(unmappedDirectory);
                 MultiReader multiReader = new MultiReader(mappedReader, unmappedReader)) {

                final IndexSearcher searcher = newSearcher(multiReader, true, true);

                final InternalSum internalSum = search(searcher, new MatchAllDocsQuery(), builder, fieldType);
                assertEquals(sum, internalSum.getValue(), 0d);
                assertTrue(AggregationInspectionHelper.hasValue(internalSum));
            }
        }
    }

    public void testValueScriptSingleValuedField() throws IOException {
        sumRandomDocsTestCase(1,
            sum("_name")
                .field(FIELD_NAME)
                .script(new Script(ScriptType.INLINE, MockScriptEngine.NAME, VALUE_SCRIPT_NAME, emptyMap())),
            (sum, docs, result) -> {
                assertEquals(sum + docs.size(), result.getValue(), 0d);
                assertTrue(AggregationInspectionHelper.hasValue(result));
            }
        );
    }

    public void testValueScriptMultiValuedField() throws IOException {
        final int valuesPerField = randomIntBetween(2, 5);
        sumRandomDocsTestCase(valuesPerField,
            sum("_name")
                .field(FIELD_NAME)
                .script(new Script(ScriptType.INLINE, MockScriptEngine.NAME, VALUE_SCRIPT_NAME, emptyMap())),
            (sum, docs, result) -> {
                assertEquals(sum + (docs.size() * valuesPerField), result.getValue(), 0d);
                assertTrue(AggregationInspectionHelper.hasValue(result));
            }
        );
    }

    public void testFieldScriptSingleValuedField() throws IOException {
        sumRandomDocsTestCase(1,
            sum("_name")
                .script(new Script(ScriptType.INLINE, MockScriptEngine.NAME, FIELD_SCRIPT_NAME, singletonMap("field", FIELD_NAME))),
            (sum, docs, result) -> {
                assertEquals(sum + docs.size(), result.getValue(), 0d);
                assertTrue(AggregationInspectionHelper.hasValue(result));
            }
        );
    }

    public void testFieldScriptMultiValuedField() throws IOException {
        final int valuesPerField = randomIntBetween(2, 5);
        sumRandomDocsTestCase(valuesPerField,
            sum("_name")
                .script(new Script(ScriptType.INLINE, MockScriptEngine.NAME, FIELD_SCRIPT_NAME, singletonMap("field", FIELD_NAME))),
            (sum, docs, result) -> {
                assertEquals(sum + (docs.size() * valuesPerField), result.getValue(), 0d);
                assertTrue(AggregationInspectionHelper.hasValue(result));
            }
        );
    }

    public void testMissing() throws IOException {
        final MappedFieldType aggField = defaultFieldType();
        final MappedFieldType irrelevantField = new NumberFieldMapper.NumberFieldType(NumberType.LONG);
        irrelevantField.setName("irrelevant_field");

        final int numDocs = randomIntBetween(10, 100);
        final long missingValue = randomLongBetween(1, 1000);
        long sum = 0;
        final List<Set<IndexableField>> docs = new ArrayList<>(numDocs);
        for (int i = 0; i < numDocs; i++) {
            if (randomBoolean()) {
                final long value = randomLongBetween(0, 1000);
                sum += value;
                docs.add(singleton(new NumericDocValuesField(aggField.name(), value)));
            } else {
                sum += missingValue;
                docs.add(singleton(new NumericDocValuesField(irrelevantField.name(), randomLong())));
            }
        }
        final long finalSum = sum;

        testCase(new MatchAllDocsQuery(),
            sum("_name")
                .field(aggField.name())
                .missing(missingValue),
            writer -> writer.addDocuments(docs),
            internalSum -> {
                assertEquals(finalSum, internalSum.getValue(), 0d);
                assertTrue(AggregationInspectionHelper.hasValue(internalSum));
            },
            List.of(aggField, irrelevantField)
        );
    }

    public void testMissingUnmapped() throws IOException {
        final long missingValue = randomLongBetween(1, 1000);
        sumRandomDocsTestCase(randomIntBetween(1, 5),
            sum("_name")
                .field("unknown_field")
                .missing(missingValue),
            (sum, docs, result) -> {
                assertEquals(docs.size() * missingValue, result.getValue(), 0d);
                assertTrue(AggregationInspectionHelper.hasValue(result));
            }
        );
    }

    private void sumRandomDocsTestCase(int valuesPerField,
                                       SumAggregationBuilder builder,
                                       TriConsumer<Long, List<Set<IndexableField>>, InternalSum> verify) throws IOException {

        final MappedFieldType fieldType = defaultFieldType();

        final int numDocs = randomIntBetween(10, 100);
        final List<Set<IndexableField>> docs = new ArrayList<>(numDocs);
        long sum = 0;
        for (int iDoc = 0; iDoc < numDocs; iDoc++) {
            Set<IndexableField> doc = new HashSet<>();
            for (int iValue = 0; iValue < valuesPerField; iValue++) {
                final long value = randomLongBetween(0, 1000);
                sum += value;
                doc.add(new SortedNumericDocValuesField(fieldType.name(), value));
            }
            docs.add(doc);
        }
        final long finalSum = sum;

        testCase(new MatchAllDocsQuery(),
            builder,
            writer -> writer.addDocuments(docs),
            internalSum -> verify.apply(finalSum, docs, internalSum),
            singleton(fieldType)
        );
    }

    private void testCase(Query query,
                          CheckedConsumer<RandomIndexWriter, IOException> indexer,
                          Consumer<InternalSum> verify) throws IOException {
        testCase(query, sum("_name").field(FIELD_NAME), indexer, verify, singleton(defaultFieldType()));
    }

    private void testCase(Query query,
                          SumAggregationBuilder aggregationBuilder,
                          CheckedConsumer<RandomIndexWriter, IOException> indexer,
                          Consumer<InternalSum> verify,
                          Collection<MappedFieldType> fieldTypes) throws IOException {
        try (Directory directory = newDirectory()) {
            try (RandomIndexWriter indexWriter = new RandomIndexWriter(random(), directory)) {
                indexer.accept(indexWriter);
            }

            try (IndexReader indexReader = DirectoryReader.open(directory)) {
                IndexSearcher indexSearcher = newSearcher(indexReader, true, true);

                final MappedFieldType[] fieldTypesArray = fieldTypes.toArray(new MappedFieldType[0]);
                final InternalSum internalSum = search(indexSearcher, query, aggregationBuilder, fieldTypesArray);
                verify.accept(internalSum);
            }
        }
    }

    @Override
    protected List<ValuesSourceType> getSupportedValuesSourceTypes() {
        return List.of(
            CoreValuesSourceType.NUMERIC,
            CoreValuesSourceType.DATE,
            CoreValuesSourceType.BOOLEAN);
    }

    @Override
    protected AggregationBuilder createAggBuilderForTypeTest(MappedFieldType fieldType, String fieldName) {
        return new SumAggregationBuilder("_name")
            .field(fieldName);
    }

    @Override
    protected ScriptService getMockScriptService() {
        final Map<String, Function<Map<String, Object>, Object>> scripts = Map.of(
            VALUE_SCRIPT_NAME, vars -> ((Number) vars.get("_value")).doubleValue() + 1,
            FIELD_SCRIPT_NAME, vars -> {
                final String fieldName = (String) vars.get("field");
                final LeafDocLookup lookup = (LeafDocLookup) vars.get("doc");
                return lookup.get(fieldName).stream()
                    .map(value -> ((Number) value).longValue() + 1)
                    .collect(toList());
            }
        );
        final MockScriptEngine engine = new MockScriptEngine(MockScriptEngine.NAME, scripts, emptyMap());
        final Map<String, ScriptEngine> engines = singletonMap(engine.getType(), engine);
        return new ScriptService(Settings.EMPTY, engines, ScriptModule.CORE_CONTEXTS);
    }

    private static MappedFieldType defaultFieldType() {
        return defaultFieldType(NumberType.LONG);
    }

    private static MappedFieldType defaultFieldType(NumberType numberType) {
        final MappedFieldType fieldType = new NumberFieldMapper.NumberFieldType(numberType);
        fieldType.setName(FIELD_NAME);
        fieldType.setHasDocValues(true);
        return fieldType;
    }
}
