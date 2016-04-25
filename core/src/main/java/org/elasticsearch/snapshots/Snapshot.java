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

package org.elasticsearch.snapshots;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.Version;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.xcontent.FromXContentBuilder;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represent information about snapshot
 */
public class Snapshot implements Comparable<Snapshot>, ToXContent, FromXContentBuilder<Snapshot> {

    private final String name;

    private final String uuid;

    private final Version version;

    private final SnapshotState state;

    private final String reason;

    private final List<String> indices;

    private final long startTime;

    private final long endTime;

    private final int totalShard;

    private final int successfulShards;

    private final List<SnapshotShardFailure> shardFailures;

    private final static List<SnapshotShardFailure> NO_FAILURES = Collections.emptyList();

    public final static Snapshot PROTO = new Snapshot();

    private Snapshot(String name, String uuid, List<String> indices, SnapshotState state, String reason, Version version, long startTime,
                     long endTime, int totalShard, int successfulShards, List<SnapshotShardFailure> shardFailures) {
        assert name != null;
        assert uuid != null;
        assert indices != null;
        assert state != null;
        assert shardFailures != null;
        this.name = name;
        this.uuid = uuid;
        this.indices = indices;
        this.state = state;
        this.reason = reason;
        this.version = version;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalShard = totalShard;
        this.successfulShards = successfulShards;
        this.shardFailures = shardFailures;
    }


    public Snapshot(String name, String uuid, List<String> indices, long startTime) {
        this(name, uuid, indices, SnapshotState.IN_PROGRESS, null, Version.CURRENT, startTime, 0L, 0, 0, NO_FAILURES);
    }

    public Snapshot(String name, String uuid, List<String> indices, long startTime, String reason, long endTime,
                        int totalShard, List<SnapshotShardFailure> shardFailures) {
        this(name, uuid, indices, snapshotState(reason, shardFailures), reason, Version.CURRENT,
                startTime, endTime, totalShard, totalShard - shardFailures.size(), shardFailures);
    }

    /**
     * Special constructor for the prototype object
     */
    private Snapshot() {
        this("", "", Collections.emptyList(), 0);
    }

    private static SnapshotState snapshotState(String reason, List<SnapshotShardFailure> shardFailures) {
        if (reason == null) {
            if (shardFailures.isEmpty()) {
                return SnapshotState.SUCCESS;
            } else {
                return SnapshotState.PARTIAL;
            }
        } else {
            return SnapshotState.FAILED;
        }
    }

    /**
     * Returns snapshot name
     *
     * @return snapshot name
     */
    public String name() {
        return name;
    }

    /**
     * Returns snapshot uuid
     *
     * @return snapshot uuid
     */
    public String uuid() {
        return uuid;
    }

    /**
     * Returns current snapshot state
     *
     * @return snapshot state
     */
    public SnapshotState state() {
        return state;
    }

    /**
     * Returns reason for complete snapshot failure
     *
     * @return snapshot failure reason
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns version of Elasticsearch that was used to create this snapshot
     *
     * @return Elasticsearch version
     */
    public Version version() {
        return version;
    }

    /**
     * Returns indices that were included into this snapshot
     *
     * @return list of indices
     */
    public List<String> indices() {
        return indices;
    }

    /**
     * Returns time when snapshot started
     *
     * @return snapshot start time
     */
    public long startTime() {
        return startTime;
    }

    /**
     * Returns time when snapshot ended
     * <p>
     * Can be 0L if snapshot is still running
     *
     * @return snapshot end time
     */
    public long endTime() {
        return endTime;
    }

    /**
     * Returns total number of shards that were snapshotted
     *
     * @return number of shards
     */
    public int totalShard() {
        return totalShard;
    }

    /**
     * Returns total number of shards that were successfully snapshotted
     *
     * @return number of successful shards
     */
    public int successfulShards() {
        return successfulShards;
    }

    /**
     * Returns shard failures
     */
    public List<SnapshotShardFailure> shardFailures() {
        return shardFailures;
    }

    /**
     * Compares two snapshots by their start time
     *
     * @param o other snapshot
     * @return the value {@code 0} if snapshots were created at the same time;
     * a value less than {@code 0} if this snapshot was created before snapshot {@code o}; and
     * a value greater than {@code 0} if this snapshot was created after snapshot {@code o};
     */
    @Override
    public int compareTo(Snapshot o) {
        return Long.compare(startTime, o.startTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Snapshot that = (Snapshot) o;
        return startTime == that.startTime && name.equals(that.name) && uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + uuid.hashCode();
        result = 31 * result + Long.hashCode(startTime);
        return result;
    }

    @Override
    public Snapshot fromXContent(XContentParser parser, ParseFieldMatcher parseFieldMatcher) throws IOException {
        return fromXContent(parser);
    }

    static final class Fields {
        static final String SNAPSHOT = "snapshot";
        static final String NAME = "name";
        static final String UUID = "uuid";
        static final String VERSION_ID = "version_id";
        static final String INDICES = "indices";
        static final String STATE = "state";
        static final String REASON = "reason";
        static final String START_TIME = "start_time";
        static final String END_TIME = "end_time";
        static final String TOTAL_SHARDS = "total_shards";
        static final String SUCCESSFUL_SHARDS = "successful_shards";
        static final String FAILURES = "failures";
    }


    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject(Fields.SNAPSHOT);
        builder.field(Fields.NAME, name);
        builder.field(Fields.UUID, uuid);
        builder.field(Fields.VERSION_ID, version.id);
        builder.startArray(Fields.INDICES);
        for (String index : indices) {
            builder.value(index);
        }
        builder.endArray();
        builder.field(Fields.STATE, state);
        if (reason != null) {
            builder.field(Fields.REASON, reason);
        }
        builder.field(Fields.START_TIME, startTime);
        builder.field(Fields.END_TIME, endTime);
        builder.field(Fields.TOTAL_SHARDS, totalShard);
        builder.field(Fields.SUCCESSFUL_SHARDS, successfulShards);
        builder.startArray(Fields.FAILURES);
        for (SnapshotShardFailure shardFailure : shardFailures) {
            builder.startObject();
            shardFailure.toXContent(builder, params);
            builder.endObject();
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }


    public static Snapshot fromXContent(XContentParser parser) throws IOException {
        String name = null;
        String uuid = null;
        Version version = Version.CURRENT;
        SnapshotState state = SnapshotState.IN_PROGRESS;
        String reason = null;
        List<String> indices = Collections.emptyList();
        long startTime = 0;
        long endTime = 0;
        int totalShard = 0;
        int successfulShards = 0;
        List<SnapshotShardFailure> shardFailures = NO_FAILURES;
        if (parser.currentToken() == null) { // fresh parser? move to the first token
            parser.nextToken();
        }
        if (parser.currentToken() == XContentParser.Token.START_OBJECT) {  // on a start object move to next token
            parser.nextToken();
        }
        XContentParser.Token token;
        if ((token = parser.nextToken()) == XContentParser.Token.START_OBJECT) {
            String currentFieldName = parser.currentName();
            if (Fields.SNAPSHOT.equals(currentFieldName)) {
                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (token == XContentParser.Token.FIELD_NAME) {
                        currentFieldName = parser.currentName();
                        token = parser.nextToken();
                        if (token.isValue()) {
                            if (Fields.NAME.equals(currentFieldName)) {
                                name = parser.text();
                            } else if (Fields.UUID.equals(currentFieldName)) {
                                uuid = parser.text();
                            } else if (Fields.STATE.equals(currentFieldName)) {
                                state = SnapshotState.valueOf(parser.text());
                            } else if (Fields.REASON.equals(currentFieldName)) {
                                reason = parser.text();
                            } else if (Fields.START_TIME.equals(currentFieldName)) {
                                startTime = parser.longValue();
                            } else if (Fields.END_TIME.equals(currentFieldName)) {
                                endTime = parser.longValue();
                            } else if (Fields.TOTAL_SHARDS.equals(currentFieldName)) {
                                totalShard = parser.intValue();
                            } else if (Fields.SUCCESSFUL_SHARDS.equals(currentFieldName)) {
                                successfulShards = parser.intValue();
                            } else if (Fields.VERSION_ID.equals(currentFieldName)) {
                                version = Version.fromId(parser.intValue());
                            }
                        } else if (token == XContentParser.Token.START_ARRAY) {
                            if (Fields.INDICES.equals(currentFieldName)) {
                                ArrayList<String> indicesArray = new ArrayList<>();
                                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                                    indicesArray.add(parser.text());
                                }
                                indices = Collections.unmodifiableList(indicesArray);
                            } else if (Fields.FAILURES.equals(currentFieldName)) {
                                ArrayList<SnapshotShardFailure> shardFailureArrayList = new ArrayList<>();
                                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                                    shardFailureArrayList.add(SnapshotShardFailure.fromXContent(parser));
                                }
                                shardFailures = Collections.unmodifiableList(shardFailureArrayList);
                            } else {
                                // It was probably created by newer version - ignoring
                                parser.skipChildren();
                            }
                        } else if (token == XContentParser.Token.START_OBJECT) {
                            // It was probably created by newer version - ignoring
                            parser.skipChildren();
                        }
                    }
                }
            }
        } else {
            throw new ElasticsearchParseException("unexpected token  [" + token + "]");
        }
        return new Snapshot(name, uuid, indices, state, reason, version, startTime, endTime, totalShard, successfulShards, shardFailures);
    }

}
