/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.client.security;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.EqualsHashCodeTestUtils;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;

public class ClientErnollmentResponseTests extends ESTestCase {

    public void testFromXContent() throws IOException {
        final String password = randomAlphaOfLength(14);
        final String httpCa = randomAlphaOfLength(50);
        final List<String> nodesAddresses =  randomList(10, () -> buildNewFakeTransportAddress().toString());

        final XContentType xContentType = randomFrom(XContentType.values());
        final XContentBuilder builder = XContentFactory.contentBuilder(xContentType);
        builder.startObject()
            .field("password", password)
            .field("http_ca", httpCa)
            .field("nodes_addresses", nodesAddresses)
            .endObject();
        BytesReference xContent = BytesReference.bytes(builder);

        final KibanaEnrollmentResponse response = KibanaEnrollmentResponse.fromXContent(createParser(xContentType.xContent(), xContent));
        assertThat(response.getPassword(), equalTo(password));
        assertThat(response.getHttpCa(), equalTo(httpCa));
        assertThat(response.getNodesAddresses(), equalTo(nodesAddresses));
    }

    public void testEqualsHashCode() {
        final SecureString password = new SecureString(randomAlphaOfLength(14).toCharArray());
        final String httpCa = randomAlphaOfLength(50);
        final List<String> nodesAddresses =  randomList(10, () -> buildNewFakeTransportAddress().toString());
        KibanaEnrollmentResponse kibanaEnrollmentResponse = new KibanaEnrollmentResponse(password, httpCa, nodesAddresses);

        EqualsHashCodeTestUtils.checkEqualsAndHashCode(
            kibanaEnrollmentResponse,
            (original) -> new KibanaEnrollmentResponse(original.getPassword(), original.getHttpCa(), original.getNodesAddresses()));

        EqualsHashCodeTestUtils.checkEqualsAndHashCode(kibanaEnrollmentResponse,
            (original) -> new KibanaEnrollmentResponse(original.getPassword(), original.getHttpCa(), original.getNodesAddresses()),
            ClientErnollmentResponseTests::mutateTestItem);
    }

    private static KibanaEnrollmentResponse mutateTestItem(KibanaEnrollmentResponse original) {
        switch (randomIntBetween(0, 3)) {
            case 0:
                return new KibanaEnrollmentResponse(new SecureString(randomAlphaOfLength(14).toCharArray()),
                    original.getHttpCa(), original.getNodesAddresses());
            case 1:
                return new KibanaEnrollmentResponse(original.getPassword(), randomAlphaOfLength(51), original.getNodesAddresses());
            case 2:
                return new KibanaEnrollmentResponse(original.getPassword(), original.getHttpCa(),
                    original.getNodesAddresses().subList(0, original.getNodesAddresses().size()-1));
            case 3:
                return new KibanaEnrollmentResponse(new SecureString(randomAlphaOfLength(14).toCharArray()), original.getHttpCa(),
                    original.getNodesAddresses().subList(0, original.getNodesAddresses().size()-1));
            default:
                return new KibanaEnrollmentResponse(original.getPassword(), randomAlphaOfLength(51),
                    original.getNodesAddresses().subList(0, original.getNodesAddresses().size()-1));
        }
    }
}
