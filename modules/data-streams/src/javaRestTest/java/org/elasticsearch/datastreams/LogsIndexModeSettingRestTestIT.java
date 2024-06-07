/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.datastreams;

import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

import org.elasticsearch.Build;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.index.IndexMode;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.cluster.local.distribution.DistributionType;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.elasticsearch.test.rest.yaml.ESClientYamlSuiteTestCase;
import org.junit.Before;
import org.junit.ClassRule;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.Matchers.equalTo;

public class LogsIndexModeSettingRestTestIT extends ESRestTestCase {

    private static final String USERNAME = Objects.requireNonNull(System.getProperty("tests.rest.cluster.username", "test_admin"));
    private static final String PASSWORD = Objects.requireNonNull(
        System.getProperty("tests.rest.cluster.password", "x-pack-test-password")
    );

    @ClassRule
    public static ElasticsearchCluster cluster = ElasticsearchCluster.local()
        .distribution(DistributionType.INTEG_TEST)
        .setting("xpack.security.enabled", "false")
        .module("x-pack-stack")
        .module("data-streams")
        .user(USERNAME, PASSWORD)
        .build();

    @ParametersFactory
    public static Iterable<Object[]> parameters() throws Exception {
        return ESClientYamlSuiteTestCase.createParameters();
    }

    @Override
    protected Settings restClientSettings() {
        String token = basicAuthHeaderValue(USERNAME, new SecureString(PASSWORD.toCharArray()));
        return Settings.builder().put(super.restClientSettings()).put(ThreadContext.PREFIX + ".Authorization", token).build();
    }

    @Override
    protected String getTestRestCluster() {
        return cluster.getHttpAddresses();
    }

    @Before
    public void setup() throws Exception {
        client = client();
        waitForLogs(client);
    }

    private RestClient client;

    private static final String MAPPINGS = """
        {
          "index_patterns": [ "logs-*-*" ],
          "data_stream": {},
          "priority": 500,
          "template": {
            "mappings": {
              "properties": {
                "@timestamp" : {
                  "type": "date"
                },
                "hostname": {
                  "type": "keyword"
                },
                "method": {
                  "type": "keyword"
                },
                "message": {
                  "type": "text"
                }
              }
            }
          }
        }""";

    private static void waitForLogs(RestClient client) throws Exception {
        assertBusy(() -> {
            try {
                Request request = new Request("GET", "_index_template/logs");
                assertOK(client.performRequest(request));
            } catch (ResponseException e) {
                fail(e.getMessage());
            }
        });
    }

    public void testLogsSettingsIndexMode() throws IOException {
        assertOK(getTemplate(client, "logs"));
        assertOK(putTemplate(client, "custom-mappings", MAPPINGS));
        assertOK(getTemplate(client, "custom-mappings"));
        assertOK(createDataStream(client, "logs-apache-dev"));
        final String indexMode = (String) getSetting(client, getWriteBackingIndex(client, "logs-apache-dev", 0), "index.mode");
        if (Build.current().isProductionRelease()) {
            assertThat(indexMode, equalTo(IndexMode.STANDARD.getName()));
        } else {
            assertThat(indexMode, equalTo(IndexMode.LOGS.getName()));
        }
    }

    private static Response putTemplate(final RestClient client, final String templateName, final String mappings) throws IOException {
        final Request request = new Request("PUT", "/_index_template/" + templateName);
        request.setJsonEntity(mappings);
        return client.performRequest(request);
    }

    private static Response getTemplate(final RestClient client, final String templateName) throws IOException {
        return client.performRequest(new Request("GET", "/_index_template/" + templateName));
    }

    private static Response createDataStream(final RestClient client, final String dataStreamName) throws IOException {
        return client.performRequest(new Request("PUT", "_data_stream/" + dataStreamName));
    }

    @SuppressWarnings("unchecked")
    private static String getWriteBackingIndex(final RestClient client, final String dataStreamName, int backingIndex) throws IOException {
        final Request request = new Request("GET", "_data_stream/" + dataStreamName);
        final List<Object> dataStreams = (List<Object>) entityAsMap(client.performRequest(request)).get("data_streams");
        final Map<String, Object> dataStream = (Map<String, Object>) dataStreams.get(0);
        final List<Map<String, String>> backingIndices = (List<Map<String, String>>) dataStream.get("indices");
        return backingIndices.get(backingIndex).get("index_name");
    }

    @SuppressWarnings("unchecked")
    private static Object getSetting(final RestClient client, final String indexName, final String setting) throws IOException {
        final Request request = new Request("GET", "/" + indexName + "/_settings?flat_settings");
        final Map<String, Object> settings = ((Map<String, Map<String, Object>>) entityAsMap(client.performRequest(request)).get(indexName))
            .get("settings");
        return settings.get(setting);
    }
}
