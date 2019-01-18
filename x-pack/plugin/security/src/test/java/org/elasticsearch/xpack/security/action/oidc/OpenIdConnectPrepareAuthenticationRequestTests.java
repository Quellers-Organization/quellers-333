/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.action.oidc;

import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.core.security.action.oidc.OpenIdConnectPrepareAuthenticationRequest;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class OpenIdConnectPrepareAuthenticationRequestTests extends ESTestCase {

    public void testSerialization() throws IOException {
        final OpenIdConnectPrepareAuthenticationRequest request = new OpenIdConnectPrepareAuthenticationRequest();
        request.setRealmName("oidc-realm1");
        final BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);

        final OpenIdConnectPrepareAuthenticationRequest unserialized =
            new OpenIdConnectPrepareAuthenticationRequest(out.bytes().streamInput());
        assertThat(unserialized.getRealmName(), equalTo("oidc-realm1"));
    }

    public void testValidation() {
        final OpenIdConnectPrepareAuthenticationRequest request = new OpenIdConnectPrepareAuthenticationRequest();
        final ActionRequestValidationException validation = request.validate();
        assertNotNull(validation);
        assertThat(validation.validationErrors().size(), equalTo(1));
        assertThat(validation.validationErrors().get(0), containsString("realm name must be provided"));
    }
}
