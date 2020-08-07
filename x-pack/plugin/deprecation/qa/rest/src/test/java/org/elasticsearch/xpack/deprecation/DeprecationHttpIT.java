/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.deprecation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.logging.HeaderWarning;
import org.elasticsearch.common.logging.LoggerMessageFormat;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.hamcrest.Matcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.elasticsearch.test.hamcrest.RegexMatcher.matches;
import static org.elasticsearch.xpack.deprecation.TestDeprecationHeaderRestAction.TEST_DEPRECATED_SETTING_TRUE1;
import static org.elasticsearch.xpack.deprecation.TestDeprecationHeaderRestAction.TEST_DEPRECATED_SETTING_TRUE2;
import static org.elasticsearch.xpack.deprecation.TestDeprecationHeaderRestAction.TEST_NOT_DEPRECATED_SETTING;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

/**
 * Tests {@code DeprecationLogger} uses the {@code ThreadContext} to add response headers.
 */
public class DeprecationHttpIT extends ESRestTestCase {

    @Override
    protected RestClient buildClient(Settings settings, HttpHost[] hosts) throws IOException {
        RestClientBuilder builder = RestClient.builder(hosts);
        configureClient(builder, settings);
        builder.setStrictDeprecationMode(false);
        return builder.build();
    }

    /**
     * Attempts to do a scatter/gather request that expects unique responses per sub-request.
     */
    @AwaitsFix(bugUrl = "https://github.com/elastic/elasticsearch/issues/19222")
    public void testUniqueDeprecationResponsesMergedTogether() throws IOException {
        final String[] indices = new String[randomIntBetween(2, 5)];

        // add at least one document for each index
        for (int i = 0; i < indices.length; ++i) {
            indices[i] = "test" + i;

            // create indices with a single shard to reduce noise; the query only deprecates uniquely by index anyway
            createIndex(indices[i], Settings.builder().put("number_of_shards", 1).build());

            int randomDocCount = randomIntBetween(1, 2);

            for (int j = 0; j < randomDocCount; j++) {
                final Request request = new Request("PUT", indices[i] + "/" + j);
                request.setJsonEntity("{ \"field\": " + j + " }");
                assertOK(client().performRequest(request));
            }
        }

        final String commaSeparatedIndices = String.join(",", indices);

        client().performRequest(new Request("POST", commaSeparatedIndices + "/_refresh"));

        // trigger all index deprecations
        Request request = new Request("GET", "/" + commaSeparatedIndices + "/_search");
        request.setJsonEntity("{ \"query\": { \"bool\": { \"filter\": [ { \"deprecated\": {} } ] } } }");
        Response response = client().performRequest(request);
        assertOK(response);

        final List<String> deprecatedWarnings = getWarningHeaders(response.getHeaders());
        final List<Matcher<String>> headerMatchers = new ArrayList<>();

        for (String index : indices) {
            headerMatchers.add(containsString(LoggerMessageFormat.format("[{}] index", (Object) index)));
        }

        assertThat(deprecatedWarnings, hasSize(headerMatchers.size()));
        for (Matcher<String> headerMatcher : headerMatchers) {
            assertThat(deprecatedWarnings, hasItem(headerMatcher));
        }
    }

    public void testDeprecationWarningsAppearInHeaders() throws Exception {
        doTestDeprecationWarningsAppearInHeaders();
    }

    public void testDeprecationHeadersDoNotGetStuck() throws Exception {
        doTestDeprecationWarningsAppearInHeaders();
        doTestDeprecationWarningsAppearInHeaders();
        if (rarely()) {
            doTestDeprecationWarningsAppearInHeaders();
        }
    }

    /**
     * Run a request that receives a predictably randomized number of deprecation warnings.
     * <p>
     * Re-running this back-to-back helps to ensure that warnings are not being maintained across requests.
     */
    private void doTestDeprecationWarningsAppearInHeaders() throws IOException {
        final boolean useDeprecatedField = randomBoolean();
        final boolean useNonDeprecatedSetting = randomBoolean();

        // deprecated settings should also trigger a deprecation warning
        final List<Setting<Boolean>> settings = new ArrayList<>(3);
        settings.add(TEST_DEPRECATED_SETTING_TRUE1);

        if (randomBoolean()) {
            settings.add(TEST_DEPRECATED_SETTING_TRUE2);
        }

        if (useNonDeprecatedSetting) {
            settings.add(TEST_NOT_DEPRECATED_SETTING);
        }

        Collections.shuffle(settings, random());

        // trigger all deprecations
        Request request = new Request("GET", "/_test_cluster/deprecated_settings");
        request.setEntity(buildSettingsRequest(settings, useDeprecatedField));
        Response response = client().performRequest(request);
        assertOK(response);

        final List<String> deprecatedWarnings = getWarningHeaders(response.getHeaders());
        final List<Matcher<String>> headerMatchers = new ArrayList<>(4);

        headerMatchers.add(equalTo(TestDeprecationHeaderRestAction.DEPRECATED_ENDPOINT));
        if (useDeprecatedField) {
            headerMatchers.add(equalTo(TestDeprecationHeaderRestAction.DEPRECATED_USAGE));
        }
        for (Setting<?> setting : settings) {
            if (setting.isDeprecated()) {
                headerMatchers.add(
                    equalTo(
                        "["
                            + setting.getKey()
                            + "] setting was deprecated in Elasticsearch and will be removed in a future release! "
                            + "See the breaking changes documentation for the next major version."
                    )
                );
            }
        }

        assertThat(deprecatedWarnings, hasSize(headerMatchers.size()));
        for (final String deprecatedWarning : deprecatedWarnings) {
            assertThat(deprecatedWarning, matches(HeaderWarning.WARNING_HEADER_PATTERN.pattern()));
        }
        final List<String> actualWarningValues = deprecatedWarnings.stream()
            .map(s -> HeaderWarning.extractWarningValueFromWarningHeader(s, true))
            .collect(Collectors.toList());
        for (Matcher<String> headerMatcher : headerMatchers) {
            assertThat(actualWarningValues, hasItem(headerMatcher));
        }
    }

    /**
     * Check that deprecation messages can be recorded to an index
     */
    public void testDeprecationMessagesCanBeIndexed() throws Exception {
        try {
            configureWriteDeprecationLogsToIndex(true);

            doTestDeprecationWarningsAppearInHeaders();

            assertBusy(() -> {
                final Response response = client().performRequest(new Request("GET", "logs-deprecation-elasticsearch/_search"));
                assertOK(response);

                ObjectMapper mapper = new ObjectMapper();
                final JsonNode jsonNode = mapper.readTree(response.getEntity().getContent());

                assertThat(jsonNode.at("hits/total/value").intValue(), greaterThan(0));

                final JsonNode firstHit = jsonNode.at("hits/hits/0");

                final Map<String, Object> document = new HashMap<>();
                firstHit.fields().forEachRemaining(entry -> document.put(entry.getKey(), entry.getValue()));

                assertThat(document, hasEntry("message", "[/_test_cluster/deprecated_settings] exists for deprecated tests"));
            }, 120, TimeUnit.SECONDS);
        } finally {
            configureWriteDeprecationLogsToIndex(null);
            client().performRequest(new Request("DELETE", "_data_stream/logs-deprecation-elasticsearch"));
        }
    }

    private void configureWriteDeprecationLogsToIndex(Boolean value) throws IOException {
        final Request request = new Request("PUT", "_cluster/settings");
        request.setJsonEntity("{ \"transient\": { \"cluster.deprecation_indexing.enabled\": " + value + " } }");
        client().performRequest(request);
    }

    private List<String> getWarningHeaders(Header[] headers) {
        List<String> warnings = new ArrayList<>();

        for (Header header : headers) {
            if (header.getName().equals("Warning")) {
                warnings.add(header.getValue());
            }
        }

        return warnings;
    }

    private HttpEntity buildSettingsRequest(List<Setting<Boolean>> settings, boolean useDeprecatedField) throws IOException {
        XContentBuilder builder = JsonXContent.contentBuilder();

        builder.startObject().startArray(useDeprecatedField ? "deprecated_settings" : "settings");

        for (Setting<Boolean> setting : settings) {
            builder.value(setting.getKey());
        }

        builder.endArray().endObject();

        return new StringEntity(Strings.toString(builder), ContentType.APPLICATION_JSON);
    }
}
