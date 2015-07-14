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

package org.elasticsearch.search.suggest.completionv2.context;

import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.elasticsearch.search.suggest.completionv2.context.GeoContextMapping.*;

/**
 * WIP
 */
public class GeoQueryContext implements ToXContent {
    public final CharSequence geoHash;
    public final int boost;
    public final int[] neighbours;

    public GeoQueryContext(GeoPoint geoPoint) {
        this(geoPoint.geohash());
    }

    public GeoQueryContext(CharSequence geoHash) {
        this(geoHash, 1);
    }

    public GeoQueryContext(GeoPoint geoPoint, int boost, int... neighbours) {
        this(geoPoint.geohash(), boost, neighbours);
    }

    public GeoQueryContext(CharSequence geoHash, int boost, int... neighbours) {
        this.geoHash = geoHash;
        this.boost = boost;
        this.neighbours = neighbours;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(CONTEXT_VALUE, geoHash);
        builder.field(CONTEXT_BOOST, boost);
        builder.field(CONTEXT_NEIGHBOURS, neighbours);
        builder.endObject();
        return builder;
    }
}
