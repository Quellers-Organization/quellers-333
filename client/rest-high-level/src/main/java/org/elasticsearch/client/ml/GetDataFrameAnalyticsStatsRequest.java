/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.client.ml;

import org.elasticsearch.client.Validatable;
import org.elasticsearch.client.ValidationException;
import org.elasticsearch.client.ml.job.util.PageParams;
import org.elasticsearch.common.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Request to get data frame analytics stats
 */
public class GetDataFrameAnalyticsStatsRequest implements Validatable {

    private final String id;
    private PageParams pageParams;

    public GetDataFrameAnalyticsStatsRequest(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public PageParams getPageParams() {
        return pageParams;
    }

    public GetDataFrameAnalyticsStatsRequest setPageParams(@Nullable PageParams pageParams) {
        this.pageParams = pageParams;
        return this;
    }

    @Override
    public Optional<ValidationException> validate() {
        if (id == null) {
            return Optional.of(ValidationException.withError("data frame analytics id must not be null"));
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GetDataFrameAnalyticsStatsRequest other = (GetDataFrameAnalyticsStatsRequest) o;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
