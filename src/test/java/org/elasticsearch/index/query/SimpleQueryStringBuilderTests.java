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

package org.elasticsearch.index.query;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.inject.util.Providers;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.cache.IndexCacheModule;
import org.elasticsearch.index.query.functionscore.FunctionScoreModule;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.index.similarity.SimilarityModule;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.indices.query.IndicesQueriesModule;
import org.elasticsearch.script.ScriptModule;
import org.elasticsearch.test.ElasticsearchTestCase;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.threadpool.ThreadPoolModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.Matchers.is;

/**
 * Unit tests for the SimpleQueryStringBuilder
 */
public class SimpleQueryStringBuilderTests extends ElasticsearchTestCase {

    private static final String SQS_BODY_1_5 = "{\"simple_query_string\":{\"query\":\"foo\",\"fields\":[\"body^1.5\"]}}";
    private QueryParseContext context;
    private Injector injector;
    private SimpleQueryStringBuilder testQuery;
    private XContentParser parser;

    @Before
    public void setup() throws IOException {
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("path.conf", this.getResourcePath("config"))
                .put("name", getClass().getName())
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .build();

        Index index = new Index("test");
        injector = new ModulesBuilder().add(
                new EnvironmentModule(new Environment(settings)),
                new SettingsModule(settings),
                new ThreadPoolModule(settings),
                new IndicesQueriesModule(),
                new ScriptModule(settings),
                new IndexSettingsModule(index, settings),
                new IndexCacheModule(settings),
                new AnalysisModule(settings),
                new SimilarityModule(settings),
                new IndexNameModule(index),
                new IndexQueryParserModule(settings),
                new FunctionScoreModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ClusterService.class).toProvider(Providers.of((ClusterService) null));
                        bind(CircuitBreakerService.class).to(NoneCircuitBreakerService.class);
                    }
                }
        ).createInjector();

        IndexQueryParserService queryParserService = injector.getInstance(IndexQueryParserService.class);
        context = new QueryParseContext(index, queryParserService);
        testQuery = createTestQuery();
        String contentString = createXContent(testQuery).string();
        parser = XContentFactory.xContent(contentString).createParser(contentString);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        terminate(injector.getInstance(ThreadPool.class));
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder content = createXContent(new SimpleQueryStringBuilder("foo").field("body", 1.5f));
        assertEquals(content.string(), SQS_BODY_1_5);
    }

    @Test
    public void testSerialization() throws IOException {
        context.reset(parser);

        BytesStreamOutput output = new BytesStreamOutput();
        testQuery.writeTo(output);

        BytesStreamInput bytesStreamInput = new BytesStreamInput(output.bytes());
        SimpleQueryStringBuilder deserializedQuery = new SimpleQueryStringBuilder();
        deserializedQuery.readFrom(bytesStreamInput);

        assertEquals(testQuery, deserializedQuery);
        assertThat(createXContent(testQuery).string(), is(createXContent(deserializedQuery).string()));
    }

    private SimpleQueryStringBuilder createTestQuery() {
        SimpleQueryStringBuilder query = new SimpleQueryStringBuilder("foo");
        if (randomBoolean()) {
            if (randomBoolean()) {
                query.field("body");
            } else {
                query.field("body", randomIntBetween(1, 10));
            }
        }
        if (randomBoolean()) {
            query.analyzer("simple");
        }
        if (randomBoolean()) {
            query.analyzeWildcard(randomBoolean());
        }
        if (randomBoolean()) {
            query.defaultOperator(randomFrom(SimpleQueryStringBuilder.Operator.AND,
                    SimpleQueryStringBuilder.Operator.OR));
        }
        if (randomBoolean()) {
            query.flags(SimpleQueryStringFlag.WHITESPACE, SimpleQueryStringFlag.NEAR);
        }
        if (randomBoolean()) {
            query.lenient(randomBoolean());
        }
        if (randomBoolean()) {
            query.locale(randomLocale());
        }
        if (randomBoolean()) {
            query.lowercaseExpandedTerms(randomBoolean());
        }
        if (randomBoolean()) {
            query.minimumShouldMatch(randomFrom(new String[]{"1", "10%"}));
        }
        if (randomBoolean()) {
            query.queryName("myquery");
        }
        return query;
    }

    private XContentBuilder createXContent(BaseQueryBuilder query) throws IOException {
        XContentBuilder content = XContentFactory.jsonBuilder();
        query.toXContent(content, null);
        content.close();
        return content;
    }
}
