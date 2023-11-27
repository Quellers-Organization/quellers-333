/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.test.ESTestCase;

public class DocumentParserContextTests extends ESTestCase {

    private final TestDocumentParserContext context = new TestDocumentParserContext();

    public void testDynamicMapperSizeMultipleMappers() {
        context.addDynamicMapper("foo", new TextFieldMapper.Builder("foo", createDefaultIndexAnalyzers()));
        assertEquals(1, context.getNewDynamicMappersSize());
        context.addDynamicMapper("bar", new TextFieldMapper.Builder("foo", createDefaultIndexAnalyzers()));
        assertEquals(2, context.getNewDynamicMappersSize());
        context.addDynamicRuntimeField(new TestRuntimeField("runtime1", "keyword"));
        assertEquals(3, context.getNewDynamicMappersSize());
        context.addDynamicRuntimeField(new TestRuntimeField("runtime2", "keyword"));
        assertEquals(4, context.getNewDynamicMappersSize());
    }

    public void testDynamicMapperSizeSameFieldMultipleRuntimeFields() {
        context.addDynamicRuntimeField(new TestRuntimeField("foo", "keyword"));
        context.addDynamicRuntimeField(new TestRuntimeField("foo", "keyword"));
        assertEquals(context.getNewDynamicMappersSize(), 1);
    }

    public void testDynamicMapperSizeSameFieldMultipleMappers() {
        context.addDynamicMapper("foo", new TextFieldMapper.Builder("foo", createDefaultIndexAnalyzers()));
        assertEquals(1, context.getNewDynamicMappersSize());
        context.addDynamicMapper("foo", new TextFieldMapper.Builder("foo", createDefaultIndexAnalyzers()));
        assertEquals(1, context.getNewDynamicMappersSize());
    }

    public void testDynamicMapperSizeSameFieldMultipleMappersDifferentSize() {
        context.addDynamicMapper(
            "foo",
            new TextFieldMapper.Builder("foo", createDefaultIndexAnalyzers()).addMultiField(
                new KeywordFieldMapper.Builder("keyword1", IndexVersion.current())
            )
        );
        assertEquals(2, context.getNewDynamicMappersSize());
        context.addDynamicMapper(
            "foo",
            new TextFieldMapper.Builder("foo", createDefaultIndexAnalyzers()).addMultiField(
                new KeywordFieldMapper.Builder("keyword1", IndexVersion.current())
            ).addMultiField(new KeywordFieldMapper.Builder("keyword2", IndexVersion.current()))
        );
        assertEquals(3, context.getNewDynamicMappersSize());
    }

}
