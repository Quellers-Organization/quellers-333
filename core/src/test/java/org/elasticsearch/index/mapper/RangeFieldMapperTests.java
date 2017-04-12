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
package org.elasticsearch.index.mapper;

import org.apache.lucene.index.IndexableField;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

import static org.elasticsearch.index.query.RangeQueryBuilder.GT_FIELD;
import static org.elasticsearch.index.query.RangeQueryBuilder.GTE_FIELD;
import static org.elasticsearch.index.query.RangeQueryBuilder.LT_FIELD;
import static org.elasticsearch.index.query.RangeQueryBuilder.LTE_FIELD;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;

public class RangeFieldMapperTests extends AbstractNumericFieldMapperTestCase {
    private static String FROM_DATE = "2016-10-31";
    private static String TO_DATE = "2016-11-01 20:00:00";
    private static int FROM = 5;
    private static String FROM_STR = FROM + "";
    private static int TO = 10;
    private static String TO_STR = TO + "";
    private static String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis";

    @Override
    protected void setTypeList() {
        TYPES = new HashSet<>(Arrays.asList("date_range", "float_range", "double_range", "integer_range", "long_range"));
    }

    private Object getFrom(String type) {
        if (type.equals("date_range")) {
            return FROM_DATE;
        }
        return random().nextBoolean() ? FROM : FROM_STR;
    }

    private String getFromField() {
        return random().nextBoolean() ? GT_FIELD.getPreferredName() : GTE_FIELD.getPreferredName();
    }

    private String getToField() {
        return random().nextBoolean() ? LT_FIELD.getPreferredName() : LTE_FIELD.getPreferredName();
    }

    private Object getTo(String type) {
        if (type.equals("date_range")) {
            return TO_DATE;
        }
        return random().nextBoolean() ? TO : TO_STR;
    }

    private Number getMax(String type) {
        if (type.equals("date_range") || type.equals("long_range")) {
            return Long.MAX_VALUE;
        } else if (type.equals("integer_range")) {
            return Integer.MAX_VALUE;
        } else if (type.equals("float_range")) {
            return Float.POSITIVE_INFINITY;
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public void doTestDefaults(String type) throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("properties").startObject("field").field("type", type);
        if (type.equals("date_range")) {
            mapping = mapping.field("format", DATE_FORMAT);
        }
        mapping = mapping.endObject().endObject().endObject().endObject();

        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping.string()));
        assertEquals(mapping.string(), mapper.mappingSource().toString());

        ParsedDocument doc = mapper.parse(SourceToParse.source("test", "type", "1", XContentFactory.jsonBuilder()
            .startObject()
            .startObject("field")
            .field(getFromField(), getFrom(type))
            .field(getToField(), getTo(type))
            .endObject()
            .endObject().bytes(),
            XContentType.JSON));

        IndexableField[] fields = doc.rootDoc().getFields("field");
        assertEquals(1, fields.length);
        IndexableField pointField = fields[0];
        assertEquals(2, pointField.fieldType().pointDimensionCount());
        assertFalse(pointField.fieldType().stored());
    }

    @Override
    protected void doTestNotIndexed(String type) throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("properties").startObject("field").field("type", type).field("index", false);
        if (type.equals("date_range")) {
            mapping = mapping.field("format", DATE_FORMAT);
        }
        mapping = mapping.endObject().endObject().endObject().endObject();

        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping.string()));
        assertEquals(mapping.string(), mapper.mappingSource().toString());

        ParsedDocument doc = mapper.parse(SourceToParse.source("test", "type", "1", XContentFactory.jsonBuilder()
            .startObject()
            .startObject("field")
            .field(getFromField(), getFrom(type))
            .field(getToField(), getTo(type))
            .endObject()
            .endObject().bytes(),
            XContentType.JSON));

        IndexableField[] fields = doc.rootDoc().getFields("field");
        assertEquals(0, fields.length);
    }

    @Override
    protected void doTestNoDocValues(String type) throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("properties").startObject("field").field("type", type).field("doc_values", false);
        if (type.equals("date_range")) {
            mapping = mapping.field("format", DATE_FORMAT);
        }
        mapping = mapping.endObject().endObject().endObject().endObject();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping.string()));
        assertEquals(mapping.string(), mapper.mappingSource().toString());

        ParsedDocument doc = mapper.parse(SourceToParse.source("test", "type", "1", XContentFactory.jsonBuilder()
            .startObject()
            .startObject("field")
            .field(getFromField(), getFrom(type))
            .field(getToField(), getTo(type))
            .endObject()
            .endObject().bytes(),
            XContentType.JSON));

        IndexableField[] fields = doc.rootDoc().getFields("field");
        assertEquals(1, fields.length);
        IndexableField pointField = fields[0];
        assertEquals(2, pointField.fieldType().pointDimensionCount());
    }

    @Override
    protected void doTestStore(String type) throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("properties").startObject("field").field("type", type).field("store", true);
        if (type.equals("date_range")) {
            mapping = mapping.field("format", DATE_FORMAT);
        }
        mapping = mapping.endObject().endObject().endObject().endObject();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping.string()));
        assertEquals(mapping.string(), mapper.mappingSource().toString());

        ParsedDocument doc = mapper.parse(SourceToParse.source("test", "type", "1", XContentFactory.jsonBuilder()
            .startObject()
            .startObject("field")
            .field(getFromField(), getFrom(type))
            .field(getToField(), getTo(type))
            .endObject()
            .endObject().bytes(),
            XContentType.JSON));

        IndexableField[] fields = doc.rootDoc().getFields("field");
        assertEquals(2, fields.length);
        IndexableField pointField = fields[0];
        assertEquals(2, pointField.fieldType().pointDimensionCount());
        IndexableField storedField = fields[1];
        assertTrue(storedField.fieldType().stored());
        assertThat(storedField.stringValue(), containsString(type.equals("date_range") ? "1477872000000" : "5"));
    }

    @Override
    public void doTestCoerce(String type) throws IOException {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("properties").startObject("field").field("type", type);
        if (type.equals("date_range")) {
            mapping = mapping.field("format", DATE_FORMAT);
        }
        mapping = mapping.endObject().endObject().endObject().endObject();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping.string()));

        assertEquals(mapping.string(), mapper.mappingSource().toString());

        ParsedDocument doc = mapper.parse(SourceToParse.source("test", "type", "1", XContentFactory.jsonBuilder()
            .startObject()
            .startObject("field")
            .field(getFromField(), getFrom(type))
            .field(getToField(), getTo(type))
            .endObject()
            .endObject().bytes(),
            XContentType.JSON));

        IndexableField[] fields = doc.rootDoc().getFields("field");
        assertEquals(1, fields.length);
        IndexableField pointField = fields[0];
        assertEquals(2, pointField.fieldType().pointDimensionCount());

        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("properties").startObject("field").field("type", type).field("coerce", false).endObject().endObject()
            .endObject().endObject();
        DocumentMapper mapper2 = parser.parse("type", new CompressedXContent(mapping.string()));

        assertEquals(mapping.string(), mapper2.mappingSource().toString());

        ThrowingRunnable runnable = () -> mapper2.parse(SourceToParse.source("test", "type", "1", XContentFactory.jsonBuilder()
            .startObject()
            .startObject("field")
            .field(getFromField(), "5.2")
            .field(getToField(), "10")
            .endObject()
            .endObject().bytes(),
            XContentType.JSON));
        MapperParsingException e = expectThrows(MapperParsingException.class, runnable);
        assertThat(e.getCause().getMessage(), anyOf(containsString("passed as String"), containsString("failed to parse date")));
    }

    @Override
    protected void doTestNullValue(String type) throws IOException {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("properties").startObject("field").field("type", type).field("store", true);
        if (type.equals("date_range")) {
            mapping = mapping.field("format", DATE_FORMAT);
        }
        mapping = mapping.endObject().endObject().endObject().endObject();

        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping.string()));
        assertEquals(mapping.string(), mapper.mappingSource().toString());

        // test null value for min and max
        ParsedDocument doc = mapper.parse(SourceToParse.source("test", "type", "1", XContentFactory.jsonBuilder()
            .startObject()
            .startObject("field")
            .nullField(getFromField())
            .nullField(getToField())
            .endObject()
            .endObject().bytes(),
            XContentType.JSON));
        assertEquals(2, doc.rootDoc().getFields("field").length);
        IndexableField[] fields = doc.rootDoc().getFields("field");
        IndexableField storedField = fields[1];
        assertThat(storedField.stringValue(), containsString(type.equals("date_range") ? Long.MAX_VALUE+"" : getMax(type)+""));

        // test null max value
        doc = mapper.parse(SourceToParse.source("test", "type", "1", XContentFactory.jsonBuilder()
            .startObject()
            .startObject("field")
            .field(getFromField(), getFrom(type))
            .nullField(getToField())
            .endObject()
            .endObject().bytes(),
            XContentType.JSON));

        fields = doc.rootDoc().getFields("field");
        assertEquals(2, fields.length);
        IndexableField pointField = fields[0];
        assertEquals(2, pointField.fieldType().pointDimensionCount());
        assertFalse(pointField.fieldType().stored());
        storedField = fields[1];
        assertTrue(storedField.fieldType().stored());
        assertThat(storedField.stringValue(), containsString(type.equals("date_range") ? "1477872000000" : "5"));
        assertThat(storedField.stringValue(), containsString(getMax(type) + ""));
    }

    public void testNoBounds() throws Exception {
        for (String type : TYPES) {
            doTestNoBounds(type);
        }
    }

    public void doTestNoBounds(String type) throws IOException {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("properties").startObject("field").field("type", type).field("store", true);
        if (type.equals("date_range")) {
            mapping = mapping.field("format", DATE_FORMAT);
        }
        mapping = mapping.endObject().endObject().endObject().endObject();

        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping.string()));
        assertEquals(mapping.string(), mapper.mappingSource().toString());

        // test no bounds specified
        ParsedDocument doc = mapper.parse(SourceToParse.source("test", "type", "1", XContentFactory.jsonBuilder()
            .startObject()
            .startObject("field")
            .endObject()
            .endObject().bytes(),
            XContentType.JSON));

        IndexableField[] fields = doc.rootDoc().getFields("field");
        assertEquals(2, fields.length);
        IndexableField pointField = fields[0];
        assertEquals(2, pointField.fieldType().pointDimensionCount());
        assertFalse(pointField.fieldType().stored());
        IndexableField storedField = fields[1];
        assertTrue(storedField.fieldType().stored());
        assertThat(storedField.stringValue(), containsString(type.equals("date_range") ? Long.MAX_VALUE+"" : getMax(type)+""));
        assertThat(storedField.stringValue(), containsString(getMax(type) + ""));
    }

    public void testIllegalArguments() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("properties").startObject("field").field("type", RangeFieldMapper.RangeType.INTEGER.name)
            .field("format", DATE_FORMAT).endObject().endObject().endObject().endObject();

        ThrowingRunnable runnable = () -> parser.parse("type", new CompressedXContent(mapping.string()));
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, runnable);
        assertThat(e.getMessage(), containsString("should not define a dateTimeFormatter"));
    }

    public void testSerializeDefaults() throws Exception {
        for (String type : TYPES) {
            String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", type).endObject().endObject()
                .endObject().endObject().string();

            DocumentMapper docMapper = parser.parse("type", new CompressedXContent(mapping));
            RangeFieldMapper mapper = (RangeFieldMapper) docMapper.root().getMapper("field");
            XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
            mapper.doXContentBody(builder, true, ToXContent.EMPTY_PARAMS);
            String got = builder.endObject().string();

            // if type is date_range we check that the mapper contains the default format and locale
            // otherwise it should not contain a locale or format
            assertTrue(got, got.contains("\"format\":\"strict_date_optional_time||epoch_millis\"") == type.equals("date_range"));
            assertTrue(got, got.contains("\"locale\":" + "\"" + Locale.ROOT + "\"") == type.equals("date_range"));
        }
    }
}
