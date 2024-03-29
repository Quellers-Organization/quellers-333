/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.kibana;

import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.SystemIndexThreadPoolTests;
import org.elasticsearch.plugins.Plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.Matchers.equalTo;

public class KibanaThreadPoolTests extends SystemIndexThreadPoolTests {

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Set.of(KibanaPlugin.class);
    }

    public void testKibanaThreadPool() {
        String userIndex = "user_index";
        client().admin().indices().prepareCreate(userIndex).get();
        CountDownLatch writeLatch = blockThreads();
        try {
            assertThreadPoolsBlocked(userIndex);

            // interact with Kibana index

            // index documents
            String idToDelete = client().prepareIndex(".kibana").setSource(Map.of("foo", "delete me!")).get().getId();
            String idToUpdate = client().prepareIndex(".kibana").setSource(Map.of("foo", "update me!")).get().getId();

            // bulk index, delete, and update
            BulkResponse response = client().prepareBulk(".kibana")
                .add(client().prepareIndex(".kibana").setSource(Map.of("foo", "search me!")))
                .add(client().prepareDelete(".kibana", idToDelete))
                .add(client().prepareUpdate().setId(idToUpdate).setDoc(Map.of("foo", "I'm updated!")))
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                .get();
            assertFalse(response.hasFailures());

            // match-all search
            var results = client().prepareSearch(".kibana").setQuery(QueryBuilders.matchAllQuery()).get();
            assertThat(results.getHits().getHits().length, equalTo(2));
        } finally {
            writeLatch.countDown();
        }
    }
}
