/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ml.action;

import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.test.AbstractStreamableXContentTestCase;
import org.elasticsearch.xpack.core.ml.action.PutFilterAction.Request;
import org.elasticsearch.xpack.core.ml.job.config.MlFilterTests;

public class PutFilterActionRequestTests extends AbstractStreamableXContentTestCase<Request> {

    private final String filterId = randomAlphaOfLengthBetween(1, 20);

    @Override
    protected Request createTestInstance() {
        return new PutFilterAction.Request(MlFilterTests.createRandom(filterId));
    }

    @Override
    protected boolean supportsUnknownFields() {
        return false;
    }

    @Override
    protected Request createBlankInstance() {
        return new PutFilterAction.Request();
    }

    @Override
    protected Request doParseInstance(XContentParser parser) {
        return PutFilterAction.Request.parseRequest(filterId, parser);
    }
}
