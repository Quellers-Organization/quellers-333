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

package org.elasticsearch.index.aliases;

import org.apache.lucene.search.Filter;
import org.elasticsearch.cluster.metadata.AliasFieldsFiltering;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.compress.CompressedString;

/**
 *
 */
public class IndexAlias {

    private String alias;

    private CompressedString filter;

    private Filter parsedFilter;

    private AliasFieldsFiltering fieldsFiltering;

    public IndexAlias(String alias, @Nullable CompressedString filter, @Nullable Filter parsedFilter, AliasFieldsFiltering fieldsFiltering) {
        this.alias = alias;
        this.filter = filter;
        this.parsedFilter = parsedFilter;
        this.fieldsFiltering = fieldsFiltering;
    }

    public String alias() {
        return alias;
    }

    @Nullable
    public CompressedString filter() {
        return filter;
    }

    @Nullable
    public Filter parsedFilter() {
        return parsedFilter;
    }

    public AliasFieldsFiltering fieldFiltering() {
        return fieldsFiltering;
    }
}
