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

package org.elasticsearch.index.query;

import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

/**
 * A filer for a field based on several terms matching on any of them.
 */
public class TermsLookupQueryBuilder extends QueryBuilder {

    private final String name;
    private String lookupIndex;
    private String lookupType;
    private String lookupId;
    private String lookupRouting;
    private String lookupPath;
    private Boolean lookupCache;

    private String queryName;

    public TermsLookupQueryBuilder(String name) {
        this.name = name;
    }

    /**
     * Sets the filter name for the filter that can be used when searching for matched_filters per hit.
     */
    public TermsLookupQueryBuilder queryName(String queryName) {
        this.queryName = queryName;
        return this;
    }

    /**
     * Sets the index name to lookup the terms from.
     */
    public TermsLookupQueryBuilder lookupIndex(String lookupIndex) {
        this.lookupIndex = lookupIndex;
        return this;
    }

    /**
     * Sets the index type to lookup the terms from.
     */
    public TermsLookupQueryBuilder lookupType(String lookupType) {
        this.lookupType = lookupType;
        return this;
    }

    /**
     * Sets the doc id to lookup the terms from.
     */
    public TermsLookupQueryBuilder lookupId(String lookupId) {
        this.lookupId = lookupId;
        return this;
    }

    /**
     * Sets the path within the document to lookup the terms from.
     */
    public TermsLookupQueryBuilder lookupPath(String lookupPath) {
        this.lookupPath = lookupPath;
        return this;
    }

    public TermsLookupQueryBuilder lookupRouting(String lookupRouting) {
        this.lookupRouting = lookupRouting;
        return this;
    }

    public TermsLookupQueryBuilder lookupCache(boolean lookupCache) {
        this.lookupCache = lookupCache;
        return this;
    }

    @Override
    public void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(TermsQueryParser.NAME);

        builder.startObject(name);
        if (lookupIndex != null) {
            builder.field("index", lookupIndex);
        }
        builder.field("type", lookupType);
        builder.field("id", lookupId);
        if (lookupRouting != null) {
            builder.field("routing", lookupRouting);
        }
        if (lookupCache != null) {
            builder.field("cache", lookupCache);
        }
        builder.field("path", lookupPath);
        builder.endObject();

        if (queryName != null) {
            builder.field("_name", queryName);
        }

        builder.endObject();
    }
}