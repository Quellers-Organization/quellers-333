/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.datastreams;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.test.rest.ESRestTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class DataStreamsRestIT extends ESRestTestCase {

    public void testHiddenDataStream() throws IOException {
        // Create a template
        Request putComposableIndexTemplateRequest = new Request("POST", "/_index_template/hidden");
        putComposableIndexTemplateRequest.setJsonEntity("{" +
            "  \"index_patterns\": [\".hidden\"],\n" +
            "  \"data_stream\": {},\n" +
            "  \"template\": {\n" +
            "    \"settings\": {\n" +
            "      \"index.hidden\": \"true\"\n" +
            "    }\n" +
            "  }\n" +
            "}"
        );
        assertOK(client().performRequest(putComposableIndexTemplateRequest));

        Request createDocRequest = new Request("POST", "/.hidden/_doc?refresh=true");
        createDocRequest.setJsonEntity("{" +
            "  \"@timestamp\": \"2020-10-22\",\n" +
            "  \"a\": 1\n" +
            "}");

        assertOK(client().performRequest(createDocRequest));

        Request getDataStreamsRequest = new Request("GET", "/_data_stream");
        Response response = client().performRequest(getDataStreamsRequest);
        Map<String, Object> dataStreams = entityAsMap(response);
        assertEquals(Collections.singletonList(".hidden"), XContentMapValues.extractValue("data_streams.name", dataStreams));
        assertEquals(Collections.singletonList("hidden"), XContentMapValues.extractValue("data_streams.template", dataStreams));
        assertEquals(Collections.singletonList(1), XContentMapValues.extractValue("data_streams.generation", dataStreams));
        assertEquals(Collections.singletonList(true), XContentMapValues.extractValue("data_streams.hidden", dataStreams));

        Request searchRequest = new Request("GET", "/.hidd*/_search");
        response = client().performRequest(searchRequest);
        Map<String, Object> results = entityAsMap(response);
        assertEquals(0, XContentMapValues.extractValue("hits.total.value", results));

        searchRequest = new Request("GET", "/.hidd*/_search?expand_wildcards=open,hidden");
        response = client().performRequest(searchRequest);
        results = entityAsMap(response);
        assertEquals(1, XContentMapValues.extractValue("hits.total.value", results));
    }
}
