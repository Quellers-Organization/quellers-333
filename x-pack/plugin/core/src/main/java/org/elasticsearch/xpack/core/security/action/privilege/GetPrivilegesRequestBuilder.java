/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.security.action.privilege;

import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;

/**
 * Builder for {@link GetPrivilegesRequest}
 */
public class GetPrivilegesRequestBuilder
    extends ActionRequestBuilder<GetPrivilegesRequest, GetPrivilegesResponse, GetPrivilegesRequestBuilder> {

    public GetPrivilegesRequestBuilder(ElasticsearchClient client, GetPrivilegesAction action) {
        super(client, action, new GetPrivilegesRequest());
    }

    public GetPrivilegesRequestBuilder privileges(String... privileges) {
        request.privileges(privileges);
        return this;
    }

    public GetPrivilegesRequestBuilder application(String applicationName) {
        request.application(applicationName);
        return this;
    }
}
