/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.openai;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.Strings;
import org.elasticsearch.xpack.inference.external.http.HttpResult;
import org.elasticsearch.xpack.inference.external.http.retry.BaseResponseHandler;
import org.elasticsearch.xpack.inference.external.http.retry.ContentTooLargeException;
import org.elasticsearch.xpack.inference.external.http.retry.ResponseParser;
import org.elasticsearch.xpack.inference.external.http.retry.RetryException;
import org.elasticsearch.xpack.inference.external.response.openai.OpenAiErrorResponseEntity;
import org.elasticsearch.xpack.inference.logging.ThrottlerManager;

import static org.elasticsearch.xpack.inference.external.http.HttpUtils.checkForEmptyBody;

public class OpenAiResponseHandler extends BaseResponseHandler {

    private static final Logger logger = LogManager.getLogger(OpenAiResponseHandler.class);
    /**
     * Rate limit headers taken from https://platform.openai.com/docs/guides/rate-limits/rate-limits-in-headers
     */
    // The maximum number of requests that are permitted before exhausting the rate limit.
    static final String REQUESTS_LIMIT = "x-ratelimit-limit-requests";
    // The maximum number of tokens that are permitted before exhausting the rate limit.
    static final String TOKENS_LIMIT = "x-ratelimit-limit-tokens";
    // The remaining number of requests that are permitted before exhausting the rate limit.
    static final String REMAINING_REQUESTS = "x-ratelimit-remaining-requests";
    // The remaining number of tokens that are permitted before exhausting the rate limit.
    static final String REMAINING_TOKENS = "x-ratelimit-remaining-tokens";

    static final String CONTENT_TOO_LARGE_MESSAGE = "Please reduce your prompt; or completion length.";

    public OpenAiResponseHandler(String requestType, ResponseParser parseFunction) {
        super(requestType, parseFunction, OpenAiErrorResponseEntity::fromResponse);
    }

    @Override
    public void validateResponse(ThrottlerManager throttlerManager, Logger logger, HttpRequestBase request, HttpResult result)
        throws RetryException {
        checkForFailureStatusCode(request, result);
        checkForEmptyBody(throttlerManager, logger, request, result);
    }

    /**
     * Validates the status code throws an RetryException if not in the range [200, 300).
     *
     * The OpenAI API error codes are documented <a href="https://platform.openai.com/docs/guides/error-codes/api-errors">here</a>.
     * @param request The http request
     * @param result  The http response and body
     * @throws RetryException Throws if status code is {@code >= 300 or < 200 }
     */
    void checkForFailureStatusCode(HttpRequestBase request, HttpResult result) throws RetryException {
        int statusCode = result.response().getStatusLine().getStatusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }

        // handle error codes
        if (statusCode >= 500) {
            logger.warn(Strings.format("OpenAI retrying because of code [%s]", statusCode));
            throw new RetryException(true, buildError(SERVER_ERROR, request, result));
        } else if (statusCode == 429) {
            logger.warn("OpenAI retrying because of 429");
            throw new RetryException(true, buildError(buildRateLimitErrorMessage(result), request, result));
        } else if (isContentTooLarge(result)) {
            throw new ContentTooLargeException(buildError(CONTENT_TOO_LARGE, request, result));
        } else if (statusCode == 401) {
            throw new RetryException(false, buildError(AUTHENTICATION, request, result));
        } else if (statusCode >= 300 && statusCode < 400) {
            throw new RetryException(false, buildError(REDIRECTION, request, result));
        } else {
            throw new RetryException(false, buildError(UNSUCCESSFUL, request, result));
        }
    }

    private static boolean isContentTooLarge(HttpResult result) {
        int statusCode = result.response().getStatusLine().getStatusCode();

        if (statusCode == 413) {
            return true;
        }

        if (statusCode == 400) {
            var errorEntity = OpenAiErrorResponseEntity.fromResponse(result);

            return errorEntity != null && errorEntity.getErrorMessage().contains(CONTENT_TOO_LARGE_MESSAGE);
        }

        return false;
    }

    static String buildRateLimitErrorMessage(HttpResult result) {
        var response = result.response();
        var tokenLimit = getFirstHeaderOrUnknown(response, TOKENS_LIMIT);
        var remainingTokens = getFirstHeaderOrUnknown(response, REMAINING_TOKENS);
        var requestLimit = getFirstHeaderOrUnknown(response, REQUESTS_LIMIT);
        var remainingRequests = getFirstHeaderOrUnknown(response, REMAINING_REQUESTS);

        var usageMessage = Strings.format(
            "Token limit [%s], remaining tokens [%s]. Request limit [%s], remaining requests [%s]",
            tokenLimit,
            remainingTokens,
            requestLimit,
            remainingRequests
        );

        return RATE_LIMIT + ". " + usageMessage;
    }

    private static String getFirstHeaderOrUnknown(HttpResponse response, String name) {
        var header = response.getFirstHeader(name);
        if (header != null && header.getElements().length > 0) {
            return header.getElements()[0].getName();
        }
        return "unknown";
    }
}
