/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.transform.rest.action;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.action.support.master.AcknowledgedRequest;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.common.time.DateMathParser;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.mapper.DateFieldMapper;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xpack.core.transform.TransformField;
import org.elasticsearch.xpack.core.transform.TransformMessages;
import org.elasticsearch.xpack.core.transform.action.StartTransformAction;

import java.time.Instant;
import java.util.List;
import java.util.function.LongSupplier;

import static org.elasticsearch.rest.RestRequest.Method.POST;

public class RestStartTransformAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return List.of(new Route(POST, TransformField.REST_BASE_PATH_TRANSFORMS_BY_ID + "_start"));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) {
        String id = restRequest.param(TransformField.ID.getPreferredName());
        String startAfterString = restRequest.param(TransformField.START_AFTER.getPreferredName());
        Instant startAfter = startAfterString != null
            ? parseDateOrThrow(startAfterString, TransformField.START_AFTER, System::currentTimeMillis)
            : null;
        TimeValue timeout = restRequest.paramAsTime(TransformField.TIMEOUT.getPreferredName(), AcknowledgedRequest.DEFAULT_ACK_TIMEOUT);

        StartTransformAction.Request request = new StartTransformAction.Request(id, startAfter, timeout);
        return channel -> client.execute(StartTransformAction.INSTANCE, request, new RestToXContentListener<>(channel));
    }

    private static Instant parseDateOrThrow(String date, ParseField paramName, LongSupplier now) {
        DateMathParser dateMathParser = DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER.toDateMathParser();
        try {
            return dateMathParser.parse(date, now);
        } catch (Exception e) {
            String msg = TransformMessages.getMessage(TransformMessages.FAILED_TO_PARSE_DATE, paramName.getPreferredName(), date);
            throw new ElasticsearchParseException(msg, e);
        }
    }

    @Override
    public String getName() {
        return "transform_start_transform_action";
    }
}
