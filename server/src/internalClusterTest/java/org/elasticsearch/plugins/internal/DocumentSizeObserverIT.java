/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.plugins.internal;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.internal.document_size.spi.DocumentSizeAccumulator;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.xcontent.FilterXContentParserWrapper;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static org.elasticsearch.xcontent.XContentFactory.cborBuilder;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.equalTo;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.TEST)
public class DocumentSizeObserverIT extends ESIntegTestCase {

    private static String TEST_INDEX_NAME = "test-index-name";

    // the assertions are done in plugin which is static and will be created by ES server.
    // hence a static flag to make sure it is indeed used
    public static boolean hasWrappedParser;

    public void testDocumentIsReportedUponBulk() throws IOException {
        hasWrappedParser = false;
        client().index(
            new IndexRequest(TEST_INDEX_NAME).id("1").source(jsonBuilder().startObject().field("test", "I am sam i am").endObject())
        ).actionGet();
        assertTrue(hasWrappedParser);
        // there are more assertions in a TestDocumentParsingProviderPlugin

        hasWrappedParser = false;
        // the format of the request does not matter
        client().index(
            new IndexRequest(TEST_INDEX_NAME).id("2").source(cborBuilder().startObject().field("test", "I am sam i am").endObject())
        ).actionGet();
        assertTrue(hasWrappedParser);
        // there are more assertions in a TestDocumentParsingProviderPlugin

        hasWrappedParser = false;
        // white spaces does not matter
        client().index(new IndexRequest(TEST_INDEX_NAME).id("3").source("""
            {
            "test":

            "I am sam i am"
            }
            """, XContentType.JSON)).actionGet();
        assertTrue(hasWrappedParser);
        // there are more assertions in a TestDocumentParsingProviderPlugin
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return List.of(TestDocumentParsingProviderPlugin.class);
    }

    public static class TestDocumentParsingProviderPlugin extends Plugin implements DocumentParsingProviderPlugin, IngestPlugin {

        public TestDocumentParsingProviderPlugin() {}

        @Override
        public DocumentParsingProvider getDocumentParsingProvider() {
            return new DocumentParsingProvider() {

                @Override
                public DocumentSizeObserver newFixedSizeDocumentObserver(long normalisedBytesParsed) {
                    return new TestDocumentSizeObserver();
                }

                @Override
                public DocumentSizeObserver newDocumentSizeObserver() {
                    return new TestDocumentSizeObserver();
                }

                @Override
                public DocumentSizeReporter getDocumentParsingReporter(String indexName, DocumentSizeAccumulator documentSizeAccumulator) {
                    return new TestDocumentSizeReporter(indexName);
                }

                @Override
                public DocumentSizeAccumulator getDocumentSizeAccumulator() {
                    return DocumentSizeAccumulator.EMPTY_INSTANCE;
                }
            };
        }
    }

    public static class TestDocumentSizeReporter implements DocumentSizeReporter {

        private final String indexName;

        public TestDocumentSizeReporter(String indexName) {
            this.indexName = indexName;
        }

        @Override
        public void onParsingCompleted(ParsedDocument parsedDocument) {

        }

        @Override
        public void onIndexingCompleted(ParsedDocument parsedDocument) {
            DocumentSizeObserver documentSizeObserver = parsedDocument.getDocumentSizeObserver();
            assertThat(indexName, equalTo(TEST_INDEX_NAME));
            assertThat(documentSizeObserver.normalisedBytesParsed(), equalTo(1L));
        }
    }

    public static class TestDocumentSizeObserver implements DocumentSizeObserver {
        long counter = 0;

        @Override
        public XContentParser wrapParser(XContentParser xContentParser) {
            hasWrappedParser = true;
            return new FilterXContentParserWrapper(xContentParser) {
                @Override
                public Token nextToken() throws IOException {
                    counter++;
                    return super.nextToken();
                }
            };
        }

        @Override
        public long normalisedBytesParsed() {
            return counter;
        }

    }
}
