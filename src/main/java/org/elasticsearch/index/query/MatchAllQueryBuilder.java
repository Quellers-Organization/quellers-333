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

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Objects;

/**
 * A query that matches on all documents.
 */
public class MatchAllQueryBuilder extends BaseQueryBuilder implements Streamable, BoostableQueryBuilder<MatchAllQueryBuilder> {

    private float boost = 1.0f;

    /**
     * Sets the boost for this query.  Documents matching this query will (in addition to the normal
     * weightings) have their score multiplied by the boost provided.
     */
    @Override
    public MatchAllQueryBuilder boost(float boost) {
        this.boost = boost;
        return this;
    }

    /**
     * Gets the boost for this query.
     */
    public float boost() {
        return this.boost;
    }

    @Override
    public void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(MatchAllQueryParser.NAME);
        if (boost != 1.0f) {
            builder.field("boost", boost);
        }
        builder.endObject();
    }

    final protected String parserName() {
        return MatchAllQueryParser.NAME;
    }

    @Override
    public Query toQuery(QueryParseContext parseContext) {
        if (this.boost == 1.0f) {
            return Queries.newMatchAllQuery();
        }

        MatchAllDocsQuery query = new MatchAllDocsQuery();
        query.setBoost(boost);
        return query;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        MatchAllQueryBuilder other = (MatchAllQueryBuilder) obj;
        return Objects.equals(boost, other.boost);
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits(this.boost);
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        this.boost = in.readFloat();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeFloat(this.boost);
    }
}
