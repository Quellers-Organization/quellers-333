/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.fleet.action;

import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.xpack.fleet.Fleet;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GetGlobalCheckpointsActionTests extends ESIntegTestCase {

    public static final TimeValue TEN_SECONDS = TimeValue.timeValueSeconds(10);
    public static final long[] EMPTY_ARRAY = new long[0];

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Stream.of(Fleet.class).collect(Collectors.toList());
    }

    public void testGetGlobalCheckpoints() throws Exception {
        int shards = randomInt(4) + 1;
        String indexName = "test_index";
        client().admin().indices().prepareCreate(indexName).setSettings(Settings.builder()
            .put(IndexSettings.INDEX_TRANSLOG_DURABILITY_SETTING.getKey(), Translog.Durability.REQUEST)
            .put("index.number_of_shards", shards)
            .put("index.number_of_replicas", 0)).get();

        final GetGlobalCheckpointsAction.Request request =
            new GetGlobalCheckpointsAction.Request(indexName, false, EMPTY_ARRAY, TEN_SECONDS);
        final GetGlobalCheckpointsAction.Response response = client().execute(GetGlobalCheckpointsAction.INSTANCE, request).get();
        long[] expected = new long[shards];
        for (int i = 0; i < shards; ++i) {
            expected[i] = -1;
        }
        assertArrayEquals(expected, response.globalCheckpoints());

        final int totalDocuments = shards * 3;
        for (int i = 0; i < totalDocuments; ++i) {
            client().prepareIndex(indexName).setId(Integer.toString(i)).setSource("{}", XContentType.JSON).get();
        }



        assertBusy(() -> {
            final GetGlobalCheckpointsAction.Request request2 =
                new GetGlobalCheckpointsAction.Request(indexName, false, EMPTY_ARRAY, TEN_SECONDS);
            final GetGlobalCheckpointsAction.Response response2 = client().execute(GetGlobalCheckpointsAction.INSTANCE, request2).get();

            assertEquals(totalDocuments, Arrays.stream(response2.globalCheckpoints()).map(s -> s + 1).sum());

            final IndicesStatsResponse statsResponse = client().admin().indices().prepareStats(indexName).get();
            long[] fromStats = Arrays.stream(statsResponse.getShards())
                .filter(i -> i.getShardRouting().primary())
                .sorted(Comparator.comparingInt(value -> value.getShardRouting().id()))
                .mapToLong(s -> s.getSeqNoStats().getGlobalCheckpoint()).toArray();
            assertArrayEquals(fromStats, response2.globalCheckpoints());
        });
    }
}
