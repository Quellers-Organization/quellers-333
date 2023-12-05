/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.connector.syncjob.action;

import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.xpack.application.EnterpriseSearch;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.xpack.application.connector.syncjob.ConnectorSyncJobConstants.CONNECTOR_SYNC_JOB_ID_PARAM;

public class RestUpdateConnectorSyncJobErrorAction extends BaseRestHandler {

    @Override
    public String getName() {
        return "connector_sync_job_update_error_action";
    }

    @Override
    public List<Route> routes() {
        return List.of(
            new Route(
                RestRequest.Method.PUT,
                "/" + EnterpriseSearch.CONNECTOR_SYNC_JOB_API_ENDPOINT + "/{" + CONNECTOR_SYNC_JOB_ID_PARAM + "}/_error"
            )
        );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        UpdateConnectorSyncJobErrorAction.Request request = UpdateConnectorSyncJobErrorAction.Request.fromXContentBytes(
            restRequest.param(CONNECTOR_SYNC_JOB_ID_PARAM),
            restRequest.content(),
            restRequest.getXContentType()
        );

        return restChannel -> client.execute(
            UpdateConnectorSyncJobErrorAction.INSTANCE,
            request,
            new RestToXContentListener<>(restChannel)
        );
    }
}
