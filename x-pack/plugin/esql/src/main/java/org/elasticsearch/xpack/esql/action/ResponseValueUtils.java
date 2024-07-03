/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.action;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BlockFactory;
import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.data.DoubleBlock;
import org.elasticsearch.compute.data.IntBlock;
import org.elasticsearch.compute.data.LongBlock;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.lucene.UnsupportedValueSource;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.elasticsearch.xpack.esql.EsqlIllegalArgumentException;
import org.elasticsearch.xpack.esql.core.type.DataType;
import org.elasticsearch.xpack.esql.planner.PlannerUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.xpack.esql.core.util.NumericUtils.unsignedLongAsNumber;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.dateTimeToLong;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.dateTimeToString;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.ipToString;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.longToUnsignedLong;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.spatialToString;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.stringToIP;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.stringToSpatial;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.stringToVersion;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.versionToString;

/**
 * Collection of static utility methods for helping transform response data between pages and values.
 */
public final class ResponseValueUtils {

    /**
     * Returns an iterator of iterators over the values in the given pages. There is one iterator
     * for each block.
     */
    public static Iterator<Iterator<Object>> pagesToValues(List<DataType> dataTypes, List<Page> pages) {
        BytesRef scratch = new BytesRef();
        return Iterators.flatMap(
            pages.iterator(),
            page -> Iterators.forRange(
                0,
                page.getPositionCount(),
                pos -> Iterators.forRange(0, page.getBlockCount(), b -> valueAtPosition(page.getBlock(b), pos, dataTypes.get(b), scratch))
            )
        );
    }

    /** Returns an iterable of iterables over the values in the given pages. There is one iterables for each row. */
    static Iterable<Iterable<Object>> valuesForRowsInPages(List<DataType> dataTypes, List<Page> pages) {
        BytesRef scratch = new BytesRef();
        return () -> Iterators.flatMap(pages.iterator(), page -> valuesForRowsInPage(dataTypes, page, scratch));
    }

    /** Returns an iterable of iterables over the values in the given page. There is one iterables for each row. */
    static Iterator<Iterable<Object>> valuesForRowsInPage(List<DataType> dataTypes, Page page, BytesRef scratch) {
        return Iterators.forRange(0, page.getPositionCount(), position -> valuesForRow(dataTypes, page, position, scratch));
    }

    /** Returns an iterable over the values in the given row in a page. */
    static Iterable<Object> valuesForRow(List<DataType> dataTypes, Page page, int position, BytesRef scratch) {
        return () -> Iterators.forRange(
            0,
            page.getBlockCount(),
            blockIdx -> valueAtPosition(page.getBlock(blockIdx), position, dataTypes.get(blockIdx), scratch)
        );
    }

    /**  Returns an iterator of values for the given column. */
    static Iterator<Object> valuesForColumn(int columnIndex, DataType dataType, List<Page> pages) {
        BytesRef scratch = new BytesRef();
        return Iterators.flatMap(
            pages.iterator(),
            page -> Iterators.forRange(
                0,
                page.getPositionCount(),
                pos -> valueAtPosition(page.getBlock(columnIndex), pos, dataType, scratch)
            )
        );
    }

    /** Returns the value that the position and with the given data type, in the block. */
    static Object valueAtPosition(Block block, int position, DataType dataType, BytesRef scratch) {
        if (block.isNull(position)) {
            return null;
        }
        int count = block.getValueCount(position);
        int start = block.getFirstValueIndex(position);
        if (count == 1) {
            return valueAt(dataType, block, start, scratch);
        }
        List<Object> values = new ArrayList<>(count);
        int end = count + start;
        for (int i = start; i < end; i++) {
            values.add(valueAt(dataType, block, i, scratch));
        }
        return values;
    }

    private static Object valueAt(DataType dataType, Block block, int offset, BytesRef scratch) {
        return switch (dataType) {
            case UNSIGNED_LONG -> unsignedLongAsNumber(((LongBlock) block).getLong(offset));
            case LONG, COUNTER_LONG -> ((LongBlock) block).getLong(offset);
            case INTEGER, COUNTER_INTEGER -> ((IntBlock) block).getInt(offset);
            case DOUBLE, COUNTER_DOUBLE -> ((DoubleBlock) block).getDouble(offset);
            case KEYWORD, TEXT -> ((BytesRefBlock) block).getBytesRef(offset, scratch).utf8ToString();
            case IP -> {
                BytesRef val = ((BytesRefBlock) block).getBytesRef(offset, scratch);
                yield ipToString(val);
            }
            case DATETIME -> {
                long longVal = ((LongBlock) block).getLong(offset);
                yield dateTimeToString(longVal);
            }
            case BOOLEAN -> ((BooleanBlock) block).getBoolean(offset);
            case VERSION -> versionToString(((BytesRefBlock) block).getBytesRef(offset, scratch));
            case GEO_POINT, GEO_SHAPE, CARTESIAN_POINT, CARTESIAN_SHAPE -> spatialToString(
                ((BytesRefBlock) block).getBytesRef(offset, scratch)
            );
            case UNSUPPORTED -> UnsupportedValueSource.UNSUPPORTED_OUTPUT;
            case SOURCE -> {
                BytesRef val = ((BytesRefBlock) block).getBytesRef(offset, scratch);
                try {
                    try (XContentParser parser = XContentHelper.createParser(XContentParserConfiguration.EMPTY, new BytesArray(val))) {
                        parser.nextToken();
                        yield parser.mapOrdered();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            case SHORT, BYTE, FLOAT, HALF_FLOAT, SCALED_FLOAT, OBJECT, NESTED, DATE_PERIOD, TIME_DURATION, DOC_DATA_TYPE, TSID_DATA_TYPE,
                NULL, PARTIAL_AGG, AGGREGATE_DOUBLE_METRIC -> throw EsqlIllegalArgumentException.illegalDataType(dataType);
        };
    }

    /**
     * Converts a list of values to Pages so that we can parse from xcontent. It's not
     * super efficient, but it doesn't really have to be.
     */
    static Page valuesToPage(BlockFactory blockFactory, List<ColumnInfoImpl> columns, List<List<Object>> values) {
        List<DataType> dataTypes = columns.stream().map(ColumnInfoImpl::type).toList();
        List<Block.Builder> results = dataTypes.stream()
            .map(c -> PlannerUtils.toElementType(c).newBlockBuilder(values.size(), blockFactory))
            .toList();

        for (List<Object> row : values) {
            for (int c = 0; c < row.size(); c++) {
                var builder = results.get(c);
                var value = row.get(c);
                switch (dataTypes.get(c)) {
                    case UNSIGNED_LONG -> ((LongBlock.Builder) builder).appendLong(longToUnsignedLong(((Number) value).longValue(), true));
                    case LONG, COUNTER_LONG -> ((LongBlock.Builder) builder).appendLong(((Number) value).longValue());
                    case INTEGER, COUNTER_INTEGER -> ((IntBlock.Builder) builder).appendInt(((Number) value).intValue());
                    case DOUBLE, COUNTER_DOUBLE -> ((DoubleBlock.Builder) builder).appendDouble(((Number) value).doubleValue());
                    case KEYWORD, TEXT, UNSUPPORTED -> ((BytesRefBlock.Builder) builder).appendBytesRef(new BytesRef(value.toString()));
                    case IP -> ((BytesRefBlock.Builder) builder).appendBytesRef(stringToIP(value.toString()));
                    case DATETIME -> {
                        long longVal = dateTimeToLong(value.toString());
                        ((LongBlock.Builder) builder).appendLong(longVal);
                    }
                    case BOOLEAN -> ((BooleanBlock.Builder) builder).appendBoolean(((Boolean) value));
                    case NULL -> builder.appendNull();
                    case VERSION -> ((BytesRefBlock.Builder) builder).appendBytesRef(stringToVersion(new BytesRef(value.toString())));
                    case SOURCE -> {
                        @SuppressWarnings("unchecked")
                        Map<String, ?> o = (Map<String, ?>) value;
                        try {
                            try (XContentBuilder sourceBuilder = JsonXContent.contentBuilder()) {
                                sourceBuilder.map(o);
                                ((BytesRefBlock.Builder) builder).appendBytesRef(BytesReference.bytes(sourceBuilder).toBytesRef());
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                    case GEO_POINT, GEO_SHAPE, CARTESIAN_POINT, CARTESIAN_SHAPE -> {
                        // This just converts WKT to WKB, so does not need CRS knowledge, we could merge GEO and CARTESIAN here
                        BytesRef wkb = stringToSpatial(value.toString());
                        ((BytesRefBlock.Builder) builder).appendBytesRef(wkb);
                    }
                }
            }
        }
        return new Page(results.stream().map(Block.Builder::build).toArray(Block[]::new));
    }
}
