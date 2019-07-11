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

package org.elasticsearch.index.reindex;

import org.elasticsearch.Version;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.persistent.PersistentTaskParams;

import java.io.IOException;
import java.util.Map;

public class ReindexJob implements PersistentTaskParams {

    // TODO: Name
    public static final String NAME = ReindexTask.NAME;

    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<ReindexJob, Void> PARSER
        = new ConstructingObjectParser<>(NAME, a -> new ReindexJob((ReindexRequest) a[0], (Boolean) a[1], (Map<String, String>) a[2]));

    private static String REINDEX_REQUEST = "reindex_request";
    private static String STORE_RESULT = "store_result";
    private static String HEADERS = "headers";

    static {
        PARSER.declareObject(ConstructingObjectParser.constructorArg(), (p, c) -> ReindexRequest.fromXContentWithParams(p),
            new ParseField(REINDEX_REQUEST));
        PARSER.declareBoolean(ConstructingObjectParser.constructorArg(), new ParseField(STORE_RESULT));
        PARSER.declareObject(ConstructingObjectParser.constructorArg(), (p, c) -> p.mapStrings(), new ParseField(HEADERS));
    }

    private final ReindexRequest reindexRequest;
    private final boolean storeResult;
    private final Map<String, String> headers;

    public ReindexJob(ReindexRequest reindexRequest, boolean storeResult, Map<String, String> headers) {
        this.reindexRequest = reindexRequest;
        this.storeResult = storeResult;
        this.headers = headers;
    }

    public ReindexJob(StreamInput in) throws IOException {
        reindexRequest = new ReindexRequest(in);
        storeResult = in.readBoolean();
        headers = in.readMap(StreamInput::readString, StreamInput::readString);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public Version getMinimalSupportedVersion() {
        // TODO: version
        return Version.V_8_0_0;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        reindexRequest.writeTo(out);
        out.writeBoolean(storeResult);
        out.writeMap(headers, StreamOutput::writeString, StreamOutput::writeString);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(REINDEX_REQUEST);
        reindexRequest.toXContent(builder, params, true);
        builder.field(STORE_RESULT, storeResult);
        builder.field(HEADERS, headers);
        return builder.endObject();
    }

    public ReindexRequest getReindexRequest() {
        return reindexRequest;
    }

    public boolean shouldStoreResult() {
        return storeResult;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public static ReindexJob fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }
}
