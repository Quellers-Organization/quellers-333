/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.fetch.subphase;

import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.ValueFetcher;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.search.lookup.Source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MetadataFetcher {

    private final Map<String, FieldContext> fieldContexts;

    public MetadataFetcher(Map<String, FieldContext> fieldContexts) {
        this.fieldContexts = fieldContexts;
    }

    private record MetadataField(String field, MappedFieldType mappedFieldType, String format) {}

    private record FieldContext(String fieldName, ValueFetcher valueFetcher) {}

    public static MetadataFetcher create(SearchExecutionContext context, List<FieldAndFormat> fieldAndFormats) {
        final List<MetadataField> metadataFields = new ArrayList<>(3);
        for (FieldAndFormat fieldAndFormat : fieldAndFormats) {
            for (final String field : context.getMatchingFieldNames(fieldAndFormat.field)) {
                if (context.getFieldType(field) != null) {
                    metadataFields.add(new MetadataFetcher.MetadataField(field, context.getFieldType(field), fieldAndFormat.format));
                }
            }
        }
        return new MetadataFetcher(buildFieldContexts(context, metadataFields));
    }

    private static Map<String, FieldContext> buildFieldContexts(
        final SearchExecutionContext context,
        final List<MetadataField> metadataFields
    ) {
        final Map<String, MetadataFetcher.FieldContext> contexts = new LinkedHashMap<>();
        for (MetadataFetcher.MetadataField metadataField : metadataFields) {
            contexts.put(
                metadataField.field,
                new MetadataFetcher.FieldContext(metadataField.field, buildValueFetcher(context, metadataField))
            );
        }
        return contexts;
    }

    private static ValueFetcher buildValueFetcher(
        final SearchExecutionContext context,
        final MetadataFetcher.MetadataField fieldAndFormat
    ) {
        try {
            return fieldAndFormat.mappedFieldType.valueFetcher(context, fieldAndFormat.format);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("error fetching [" + fieldAndFormat.field + ']', e);
        }
    }

    public Map<String, DocumentField> fetch(Source source, int doc) throws IOException {
        final Map<String, DocumentField> metadataFields = new HashMap<>();
        for (final MetadataFetcher.FieldContext context : fieldContexts.values()) {
            final DocumentField fieldValue = context.valueFetcher.fetchDocumentField(context.fieldName, source, doc);
            if (fieldValue != null) {
                metadataFields.put(context.fieldName, fieldValue);
            }
        }
        return metadataFields;
    }

    public void setNextReader(LeafReaderContext readerContext) {
        for (MetadataFetcher.FieldContext field : fieldContexts.values()) {
            field.valueFetcher.setNextReader(readerContext);
        }
    }
}
