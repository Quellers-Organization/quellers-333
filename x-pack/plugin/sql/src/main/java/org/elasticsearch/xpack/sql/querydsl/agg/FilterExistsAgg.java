/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.querydsl.agg;

import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.xpack.ql.expression.gen.script.Scripts;

import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;

/**
 * Aggregation builder for a "filter" aggregation encapsulating an "exists" query.
 */
public class FilterExistsAgg extends LeafAgg {

    public FilterExistsAgg(String id, Object fieldOrScript) {
        super(id, fieldOrScript);
    }

    @Override
    AggregationBuilder toBuilder() {
        if (fieldName() != null) {
            return filter(id(), QueryBuilders.existsQuery(fieldName()));
        } else {
            return filter(id(), QueryBuilders.scriptQuery(Scripts.isNotNullCardinality(scriptTemplate()).toPainless()));
        }
    }
}
