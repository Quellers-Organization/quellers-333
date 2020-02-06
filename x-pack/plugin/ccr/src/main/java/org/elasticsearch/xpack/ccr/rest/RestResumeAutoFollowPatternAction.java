/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ccr.rest;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.xpack.core.ccr.action.ActivateAutoFollowPatternAction.Request;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.xpack.core.ccr.action.ActivateAutoFollowPatternAction.INSTANCE;

public class RestResumeAutoFollowPatternAction extends BaseRestHandler {

    @Override
    public List<Route> handledRoutes() {
        return singletonList(new Route("/_ccr/auto_follow/{name}/resume", POST));
    }

    @Override
    public String getName() {
        return "ccr_resume_auto_follow_pattern_action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(final RestRequest restRequest, final NodeClient client) {
        Request request = new Request(restRequest.param("name"), true);
        return channel -> client.execute(INSTANCE, request, new RestToXContentListener<>(channel));
    }
}
