/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.core.indexlifecycle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.stats.IndexStats;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsRequest;
import org.elasticsearch.action.admin.indices.stats.ShardStats;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class WaitForNoFollowersStep extends AsyncWaitStep {

    private static final Logger logger = LogManager.getLogger(WaitForNoFollowersStep.class);

    static final String NAME = "wait-for-shard-history-leases";
    static final String CCR_LEASE_KEY = "ccr";

    WaitForNoFollowersStep(StepKey key, StepKey nextStepKey, Client client) {
        super(key, nextStepKey, client);
    }

    @Override
    public void evaluateCondition(IndexMetaData indexMetaData, Listener listener) {
        IndicesStatsRequest request = new IndicesStatsRequest();
        request.clear();
        String indexName = indexMetaData.getIndex().getName();
        request.indices(indexName);
        getClient().admin().indices().stats(request, ActionListener.wrap((response) -> {
            IndexStats indexStats = response.getIndex(indexName);
            if (indexStats == null) {
                // Index was probably deleted
                logger.debug("got null shard stats for index {}, proceeding on the assumption it has been deleted",
                    indexMetaData.getIndex());
                listener.onResponse(true, null);
                return;
            }

            boolean isCurrentlyLeaderIndex = Arrays.stream(indexStats.getShards())
                .map(ShardStats::getRetentionLeaseStats)
                .flatMap(retentionLeaseStats -> retentionLeaseStats.leases().stream())
                .anyMatch(lease -> CCR_LEASE_KEY.equals(lease.source()));

            if (isCurrentlyLeaderIndex) {
                listener.onResponse(false, new Info());
            } else {
                listener.onResponse(true, null);
            }
        }, listener::onFailure));
    }

    static final class Info implements ToXContentObject {

        static final ParseField MESSAGE_FIELD = new ParseField("message");

        private final String message;

        Info() {
            this.message = "this index is a leader index; waiting for all following indices to cease following before proceeding";
        }

        String getMessage() {
            return message;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field(MESSAGE_FIELD.getPreferredName(), message);
            builder.endObject();
            return builder;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Info info = (Info) o;
            return Objects.equals(getMessage(), info.getMessage());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getMessage());
        }
    }
}
