/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.transform.transforms;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.xpack.core.transform.transforms.TransformIndexerStats;

import java.util.Map;
import java.util.stream.Stream;

public interface Function {

    /**
     * Get the initial page size for this pivot.
     *
     * The page size is the main parameter for adjusting memory consumption. Memory consumption mainly depends on
     * the page size, the type of aggregations and the data. As the page size is the number of buckets we return
     * per page the page size is a multiplier for the costs of aggregating bucket.
     *
     * In future we might inspect the configuration and base the initial size on the aggregations used.
     *
     * @return the page size
     */
    int getInitialPageSize();

    AggregationBuilder aggregation(Map<String, Object> position, int pageSize);

    ChangeCollector buildChangeCollector(String synchronizationField);

    boolean supportsIncrementalBucketUpdate();

    Stream<IndexRequest> processBuckets(
        SearchResponse searchResponse,
        String destinationIndex,
        String destinationPipeline,
        Map<String, String> fieldMappings,
        TransformIndexerStats stats
    );

    Map<String, Object> getAfterKey(SearchResponse searchResponse);

}
