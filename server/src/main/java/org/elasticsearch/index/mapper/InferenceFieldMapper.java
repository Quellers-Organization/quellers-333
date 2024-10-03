/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.index.mapper;

import org.elasticsearch.cluster.metadata.InferenceFieldMetadata;
import org.elasticsearch.inference.InferenceService;

import java.util.Map;
import java.util.Set;

/**
 * Field mapper that requires to transform its input before indexation through the {@link InferenceService}.
 */
public interface InferenceFieldMapper {

    /**
     * Retrieve the inference metadata associated with this mapper.
     *
     * @param sourcePaths The source path that populates the input for the field (before inference)
     */
    InferenceFieldMetadata getMetadata(Set<String> sourcePaths);

    /**
     * Get the field's original value (i.e. the value the user specified) from the provided source.
     *
     * @param sourceAsMap The source as a map
     * @return The field's original value, or {@code null} if none was provided
     */
    Object getOriginalValue(Map<String, Object> sourceAsMap);

    /**
     * Get the field's inference field name
     *
     * @return The full path to the field's inference field name
     */
    String getInferenceFieldName();
}
