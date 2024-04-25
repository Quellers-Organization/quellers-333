/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.indices;

import org.elasticsearch.index.mapper.SourceFieldMetrics;

/**
 * Groups together all metrics used in mappers.
 * Main purpose of this class is to avoid verbosity of passing individual metric instances around.
 */
public class MapperMetrics {
    public static MapperMetrics NOOP = new MapperMetrics(SourceFieldMetrics.NOOP);

    private final SourceFieldMetrics sourceFieldMetrics;

    public MapperMetrics(SourceFieldMetrics sourceFieldMetrics) {
        this.sourceFieldMetrics = sourceFieldMetrics;
    }

    public SourceFieldMetrics getSyntheticSourceMetrics() {
        return sourceFieldMetrics;
    }
}
