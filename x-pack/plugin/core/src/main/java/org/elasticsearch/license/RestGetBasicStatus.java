/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.license;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestBuilderListener;
import org.elasticsearch.xpack.core.XPackClient;
import org.elasticsearch.xpack.core.rest.XPackRestHandler;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.GET;

public class RestGetBasicStatus extends XPackRestHandler {

    RestGetBasicStatus(RestController controller) {
        controller.registerHandler(GET, URI_BASE + "/license/basic_status", this);
    }

    @Override
    protected RestChannelConsumer doPrepareRequest(RestRequest request, XPackClient client) throws IOException {
        return channel -> client.licensing().prepareGetStartBasic().execute(
                new RestBuilderListener<GetBasicStatusResponse>(channel) {
                    @Override
                    public RestResponse buildResponse(GetBasicStatusResponse response, XContentBuilder builder) throws Exception {
                        builder.startObject();
                        builder.field("eligible_to_start_basic", response.isEligibleToStartBasic());
                        builder.endObject();
                        return new BytesRestResponse(RestStatus.OK, builder);
                    }
                });
    }

    @Override
    public String getName() {
        return "xpack_basic_status_action";
    }
}
