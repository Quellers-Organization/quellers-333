/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.index.IndexMode;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.plain.SortedSetOrdinalsIndexFieldData;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.support.CoreValuesSourceType;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Mapper for {@code _tsid} field included generated when the index is
 * {@link IndexMode#TIME_SERIES organized into time series}.
 */
public class TimeSeriesIdFieldMapper extends MetadataFieldMapper {

    public static final String NAME = "_tsid";
    public static final String CONTENT_TYPE = "_tsid";
    public static final TimeSeriesIdFieldType FIELD_TYPE = new TimeSeriesIdFieldType();
    private static final TimeSeriesIdFieldMapper INSTANCE = new TimeSeriesIdFieldMapper();

    @Override
    public FieldMapper.Builder getMergeBuilder() {
        return new Builder().init(this);
    }

    public static class Builder extends MetadataFieldMapper.Builder {
        protected Builder() {
            super(NAME);
        }

        @Override
        protected List<Parameter<?>> getParameters() {
            return List.of();
        }

        @Override
        public TimeSeriesIdFieldMapper build() {
            return INSTANCE;
        }
    }

    public static final TypeParser PARSER = new ConfigurableTypeParser(c -> new TimeSeriesIdFieldMapper(), c -> new Builder());

    public static final class TimeSeriesIdFieldType extends MappedFieldType {
        private TimeSeriesIdFieldType() {
            super(NAME, false, false, true, TextSearchInfo.NONE, Collections.emptyMap());
        }

        @Override
        public String typeName() {
            return CONTENT_TYPE;
        }

        @Override
        public ValueFetcher valueFetcher(SearchExecutionContext context, String format) {
            return new DocValueFetcher(docValueFormat(format, null), context.getForField(this));
        }

        @Override
        public DocValueFormat docValueFormat(String format, ZoneId timeZone) {
            if (format != null) {
                throw new IllegalArgumentException("Field [" + name() + "] of type [" + typeName() + "] doesn't support formats.");
            }
            return DocValueFormat.TIME_SERIES_ID;
        }

        @Override
        public IndexFieldData.Builder fielddataBuilder(String fullyQualifiedIndexName, Supplier<SearchLookup> searchLookup) {
            failIfNoDocValues();
            // TODO don't leak the TSID's binary format into the script
            return new SortedSetOrdinalsIndexFieldData.Builder(name(), CoreValuesSourceType.KEYWORD);
        }

        @Override
        public Query termQuery(Object value, SearchExecutionContext context) {
            throw new IllegalArgumentException("[" + NAME + "] is not searchable");
        }
    }

    private TimeSeriesIdFieldMapper() {
        super(FIELD_TYPE);
    }

    @Override
    public void preParse(DocumentParserContext context) throws IOException {
        if (context.indexSettings().getMode() != IndexMode.TIME_SERIES) {
            return;
        }
        assert fieldType().isSearchable() == false;

        BytesReference timeSeriesId = context.mappingLookup()
            .getMapping()
            .generateTimeSeriesIdIfNeeded(context.sourceToParse().source(), context.sourceToParse().getXContentType());
        assert timeSeriesId != null : "In time series mode _tsid cannot be null";

        context.doc().add(new SortedSetDocValuesField(fieldType().name(), timeSeriesId.toBytesRef()));
    }

    @Override
    public void postParse(DocumentParserContext context) throws IOException {
        if (context.indexSettings().getMode() != IndexMode.TIME_SERIES) {
            return;
        }
        assert fieldType().isSearchable() == false;

        List<IndexableField> fields = context.rootDoc().getFields();
        if (fields.size() == 0) {
            throw new IllegalArgumentException("Dimension fields are missing");
        }

        BytesReference timeSeriesId = context.mappingLookup().getMapping().getTimeSeriesIdGenerator().generate(context.rootDoc());
        if (timeSeriesId != null) {
            timeSeriesId.utf8ToString();
        }
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }
}
