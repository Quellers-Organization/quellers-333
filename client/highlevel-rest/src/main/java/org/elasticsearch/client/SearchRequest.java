/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.client;

import org.elasticsearch.common.Strings;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Representation of a request to the "_search" endpoint.
 * Wraps a @link {@link SearchSourceBuilder} representing the request body. Setting indices, types and url parameters can be chained.
 */
public class SearchRequest {

    private final SearchSourceBuilder searchSourceBuilder;
    private String[] indices = Strings.EMPTY_ARRAY;
    private String[] types = Strings.EMPTY_ARRAY;
    private final Map<String, String> urlParams = new HashMap<>();

    /**
     * Create a new SearchRequest
     * @param searchSource the builder for the request body
     */
    public SearchRequest(SearchSourceBuilder searchSource) {
        this.searchSourceBuilder = Objects.requireNonNull(searchSource);
    }

    /**
     * Set the indices this request runs on
     */
    public SearchRequest setIndices(String... indices) {
        this.indices = Objects.requireNonNull(indices);
        return this;
    }

    /**
     * Set the types this request runs on
     */
    public SearchRequest setTypes(String... types) {
        this.types = Objects.requireNonNull(types);
        return this;
    }

    /**
     * Set the url parameters used by this request
     */
    public SearchRequest addParam(String param, String value) {
        Objects.requireNonNull(param, "param must not be null");
        Objects.requireNonNull(value, "value must not be null");
        urlParams.put(param, value);
        return this;
    }

    /**
     * Get the underlying {@link SearchSourceBuilder}
     */
    public SearchSourceBuilder getSearchSource() {
        return this.searchSourceBuilder;
    }

    /**
     * Get the indices this request runs on
     */
    public String[] getIndices() {
        return this.indices;
    }

    /**
     * Get the types this request runs on
     */
    public String[] getTypes() {
        return this.types;
    }

    /**
     * Get the url parameters used in this request
     */
    public Map<String, String> getParams() {
        return this.urlParams;
    }
}
