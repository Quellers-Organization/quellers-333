/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.analytics.action;

import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.application.EnterpriseSearch;

import java.util.List;

import static org.elasticsearch.rest.RestRequest.Method.POST;

public class RestPostAnalyticsEventAction extends BaseRestHandler {

    @Override
    public String getName() {
        return "analytics_post_event_action";
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(POST, "/" + EnterpriseSearch.BEHAVIORAL_ANALYTICS_API_ENDPOINT + "/{collection_name}/event/{event_type}"));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) {
        PostAnalyticsEventAction.Request request = buidRequest(restRequest);
        return channel -> client.execute(PostAnalyticsEventAction.INSTANCE, request, new RestStatusToXContentListener<>(channel));
    }

    private PostAnalyticsEventAction.Request buidRequest(RestRequest restRequest) {
        Tuple<XContentType, BytesReference> sourceTuple = restRequest.contentOrSourceParam();

        PostAnalyticsEventAction.RequestBuilder builder = PostAnalyticsEventAction.Request.builder(
            restRequest.param("collection_name"),
            restRequest.param("event_type"),
            sourceTuple.v1(),
            sourceTuple.v2()
        );

        builder.debug(restRequest.paramAsBoolean("debug", false));

        // TODO:
        // 1. Extract ip address (do not forget the X-Forwarded-For header) and pass it to the request.
        // 2. Extract / sanitize headers (user agent)

        return builder.request();
    }
}
