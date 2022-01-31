/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.search.Query;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.script.CompositeFieldScript;
import org.elasticsearch.search.fetch.subphase.FieldAndFormat;
import org.elasticsearch.search.fetch.subphase.LookupField;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.xcontent.XContentBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.elasticsearch.search.SearchService.ALLOW_EXPENSIVE_QUERIES;

/**
 * A runtime field that retrieves fields from related indices.
 * <pre>
 * {
 *     "type": "lookup",
 *     "index": "an_external_index",
 *     "query_type": "term",
 *     "query_input_field": "ip_address",
 *     "query_target_field": "host_ip",
 *     "fetch_fields": [
 *         "field-1",
 *         "field-2"
 *     ]
 * }
 * </pre>
 */
public final class LookupRuntimeFieldType extends MappedFieldType {

    public static final RuntimeField.Parser PARSER = new RuntimeField.Parser(Builder::new);
    public static final String CONTENT_TYPE = "lookup";
    public static final Set<String> SUPPORTED_QUERY_TYPES = Set.of("term");

    private static class Builder extends RuntimeField.Builder {
        private final FieldMapper.Parameter<String> index = FieldMapper.Parameter.stringParam(
            "index",
            false,
            RuntimeField.initializerNotSupported(),
            null
        ).addValidator(v -> {
            if (Strings.isEmpty(v)) {
                throw new IllegalArgumentException("[index] parameter must be specified");
            }
        });

        private final FieldMapper.Parameter<String> queryType = FieldMapper.Parameter.stringParam(
            "query_type",
            false,
            RuntimeField.initializerNotSupported(),
            null
        ).addValidator(queryType -> {
            if (SUPPORTED_QUERY_TYPES.contains(queryType) == false) {
                throw new IllegalArgumentException("Supported query types: [" + SUPPORTED_QUERY_TYPES + "]; got [" + queryType + "]");
            }
        });

        private final FieldMapper.Parameter<String> queryInputField = FieldMapper.Parameter.stringParam(
            "query_input_field",
            false,
            RuntimeField.initializerNotSupported(),
            null
        ).addValidator(v -> {
            if (Strings.isEmpty(v)) {
                throw new IllegalArgumentException("[query_input_field] parameter must be specified");
            }
        });

        private final FieldMapper.Parameter<String> queryTargetField = FieldMapper.Parameter.stringParam(
            "query_target_field",
            false,
            RuntimeField.initializerNotSupported(),
            null
        ).addValidator(v -> {
            if (Strings.isEmpty(v)) {
                throw new IllegalArgumentException("[query_target_field] parameter must be specified");
            }
        });

        private static FieldMapper.Parameter<List<FieldAndFormat>> newFetchFields() {
            final FieldMapper.Parameter<List<FieldAndFormat>> fetchFields = new FieldMapper.Parameter<>(
                "fetch_fields",
                false,
                List::of,
                (s, ctx, o) -> parseFetchFields(o),
                RuntimeField.initializerNotSupported(),
                XContentBuilder::field,
                Object::toString
            );
            fetchFields.addValidator(fields -> {
                if (fields.isEmpty()) {
                    throw new MapperParsingException("[fetch_fields] parameter must not be empty");
                }
            });
            return fetchFields;
        }

        @SuppressWarnings("rawtypes")
        private static List<FieldAndFormat> parseFetchFields(Object o) {
            final List<?> values = (List<?>) o;
            return values.stream().map(v -> {
                if (v instanceof Map m) {
                    final String field = (String) m.get(FieldAndFormat.FIELD_FIELD.getPreferredName());
                    final String format = (String) m.get(FieldAndFormat.FORMAT_FIELD.getPreferredName());
                    if (field == null) {
                        throw new MapperParsingException("[field] parameter of [fetch_fields] must be provided");
                    }
                    return new FieldAndFormat(field, format);
                } else if (v instanceof String s) {
                    return new FieldAndFormat(s, null);
                } else {
                    throw new MapperParsingException("unexpected value [" + v + "] for [fetch_fields] parameter");
                }
            }).toList();
        }

        private final FieldMapper.Parameter<List<FieldAndFormat>> fetchFields = newFetchFields();

        Builder(String name) {
            super(name);

        }

        @Override
        protected List<FieldMapper.Parameter<?>> getParameters() {
            final List<FieldMapper.Parameter<?>> parameters = new ArrayList<>(super.getParameters());
            parameters.add(index);
            parameters.add(queryType);
            parameters.add(queryInputField);
            parameters.add(queryTargetField);
            parameters.add(fetchFields);
            return parameters;
        }

        @Override
        protected RuntimeField createRuntimeField(MappingParserContext parserContext) {
            final LookupRuntimeFieldType ft = new LookupRuntimeFieldType(
                name,
                meta(),
                index.get(),
                queryInputField.get(),
                queryTargetField.get(),
                fetchFields.get()
            );
            return new LeafRuntimeField(name, ft, getParameters());
        }

        @Override
        protected RuntimeField createChildRuntimeField(
            MappingParserContext parserContext,
            String parentName,
            Function<SearchLookup, CompositeFieldScript.LeafFactory> parentScriptFactory
        ) {
            return createRuntimeField(parserContext);
        }
    }

    private static final ValueFetcher EMPTY_VALUE_FETCHER = (lookup, ignoredValues) -> List.of();
    private final String index;
    private final String queryInputField;
    private final String queryTargetField;
    private final List<FieldAndFormat> fetchFields;

    private LookupRuntimeFieldType(
        String name,
        Map<String, String> meta,
        String index,
        String queryInputField,
        String queryTargetField,
        List<FieldAndFormat> fetchFields
    ) {
        super(name, false, false, false, TextSearchInfo.NONE, meta);
        this.index = index;
        this.queryInputField = queryInputField;
        this.queryTargetField = queryTargetField;
        this.fetchFields = fetchFields;
    }

    @Override
    public ValueFetcher valueFetcher(SearchExecutionContext context, String format) {
        if (context.allowExpensiveQueries() == false) {
            throw new ElasticsearchException(
                "cannot be executed against lookup field ["
                    + name()
                    + "] while ["
                    + ALLOW_EXPENSIVE_QUERIES.getKey()
                    + "] is set to [false]."
            );
        }
        return new LookupFieldValueFetcher(context);
    }

    @Override
    public String typeName() {
        return CONTENT_TYPE;
    }

    @Override
    public Query termQuery(Object value, SearchExecutionContext context) {
        throw new IllegalArgumentException("Cannot search on field [" + name() + "] since it is a lookup field.");
    }

    private class LookupFieldValueFetcher implements ValueFetcher {
        private final ValueFetcher inputValueFetcher;

        LookupFieldValueFetcher(SearchExecutionContext context) {
            this.inputValueFetcher = context.getFieldType(queryInputField).valueFetcher(context, null);
        }

        @Override
        public List<Object> fetchValues(SourceLookup lookup, List<Object> ignoredValues) throws IOException {
            assert false : "call #fetchDocumentField() instead";
            throw new UnsupportedOperationException("call #fetchDocumentField() instead");
        }

        @Override
        public DocumentField fetchDocumentField(String docName, SourceLookup lookup) throws IOException {
            final DocumentField inputDoc = inputValueFetcher.fetchDocumentField(queryInputField, lookup);
            if (inputDoc == null || inputDoc.getValues().isEmpty()) {
                return null;
            }
            final List<LookupField> lookupFields = inputDoc.getValues().stream().map(input -> {
                final TermQueryBuilder query = new TermQueryBuilder(queryTargetField, input.toString());
                return new LookupField(lookupIndex, query, fetchFields);
            }).toList();
            return new DocumentField(docName, List.of(), List.of(), lookupFields);
        }

        @Override
        public void setNextReader(LeafReaderContext context) {
            inputValueFetcher.setNextReader(context);
        }
    }
}
