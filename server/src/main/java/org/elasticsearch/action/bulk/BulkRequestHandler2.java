/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.action.bulk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.threadpool.Scheduler;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Implements the low-level details of bulk request handling
 */
public final class BulkRequestHandler2 {
    private final Logger logger;
    private final BiConsumer<BulkRequest, ActionListener<BulkResponse>> consumer;
    private final BulkProcessor2.Listener listener;
    private final Retry2 retry;

    BulkRequestHandler2(
        BiConsumer<BulkRequest, ActionListener<BulkResponse>> consumer,
        BackoffPolicy backoffPolicy,
        BulkProcessor2.Listener listener,
        Scheduler scheduler,
        int concurrentRequests,
        int maxBulkRequestQueueSize,
        int maxBulkRequestRetryQueueSize,
        TimeValue queuePollingInterval
    ) {
        assert concurrentRequests >= 0;
        assert maxBulkRequestQueueSize >= 0;
        assert maxBulkRequestRetryQueueSize >= 0;
        this.logger = LogManager.getLogger(getClass());
        this.consumer = consumer;
        this.listener = listener;
        this.retry = new Retry2(
            backoffPolicy,
            scheduler,
            maxBulkRequestQueueSize,
            maxBulkRequestRetryQueueSize,
            concurrentRequests,
            queuePollingInterval
        );
        retry.init();
    }

    public void execute(BulkRequest bulkRequest, long executionId) {
        try {
            listener.beforeBulk(executionId, bulkRequest);
            retry.withBackoff(consumer, bulkRequest, new ActionListener<>() {
                @Override
                public void onResponse(BulkResponse response) {
                    listener.afterBulk(executionId, bulkRequest, response);
                }

                @Override
                public void onFailure(Exception e) {
                    listener.afterBulk(executionId, bulkRequest, e);
                }
            });
        } catch (Exception e) {
            logger.warn(() -> "Failed to execute bulk request " + executionId + ".", e);
            listener.afterBulk(executionId, bulkRequest, e);
        }
    }

    void awaitClose(long timeout, TimeUnit unit) {
        retry.awaitClose(timeout, unit);
    }
}
