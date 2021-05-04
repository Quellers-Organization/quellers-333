/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.Diff;
import org.elasticsearch.cluster.DiffableUtils;
import org.elasticsearch.cluster.NamedDiff;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Custom {@link Metadata} implementation for storing a map of {@link DataStream}s and their names.
 */
public class DataStreamMetadata implements Metadata.Custom {

    public static final String TYPE = "data_stream";
    private static final ParseField DATA_STREAM = new ParseField("data_stream");
    @SuppressWarnings("unchecked")
    private static final ConstructingObjectParser<DataStreamMetadata, Void> PARSER = new ConstructingObjectParser<>(TYPE, false,
        a -> new DataStreamMetadata((Map<String, DataStream>) a[0]));

    static {
        PARSER.declareObject(ConstructingObjectParser.constructorArg(), (p, c) -> {
            Map<String, DataStream> dataStreams = new HashMap<>();
            while (p.nextToken() != XContentParser.Token.END_OBJECT) {
                String name = p.currentName();
                dataStreams.put(name, DataStream.fromXContent(p));
            }
            return dataStreams;
        }, DATA_STREAM);
    }

    private final Map<String, DataStream> dataStreams;

    public DataStreamMetadata(Map<String, DataStream> dataStreams) {
        this.dataStreams = Map.copyOf(dataStreams);
    }

    public DataStreamMetadata(StreamInput in) throws IOException {
        this(in.readMap(StreamInput::readString, DataStream::new));
    }

    public Map<String, DataStream> dataStreams() {
        return this.dataStreams;
    }

    @Override
    public Diff<Metadata.Custom> diff(Metadata.Custom before) {
        return new DataStreamMetadata.DataStreamMetadataDiff((DataStreamMetadata) before, this);
    }

    public static NamedDiff<Metadata.Custom> readDiffFrom(StreamInput in) throws IOException {
        return new DataStreamMetadata.DataStreamMetadataDiff(in);
    }

    @Override
    public EnumSet<Metadata.XContentContext> context() {
        return Metadata.ALL_CONTEXTS;
    }

    @Override
    public String getWriteableName() {
        return TYPE;
    }

    @Override
    public Version getMinimalSupportedVersion() {
        return Version.V_7_7_0;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(this.dataStreams, StreamOutput::writeString, (stream, val) -> val.writeTo(stream));
    }

    public static DataStreamMetadata fromXContent(XContentParser parser) throws IOException {
        return PARSER.parse(parser, null);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(DATA_STREAM.getPreferredName());
        for (Map.Entry<String, DataStream> dataStream : dataStreams.entrySet()) {
            builder.field(dataStream.getKey(), dataStream.getValue());
        }
        builder.endObject();
        return builder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.dataStreams);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        DataStreamMetadata other = (DataStreamMetadata) obj;
        return Objects.equals(this.dataStreams, other.dataStreams);
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }

    static class DataStreamMetadataDiff implements NamedDiff<Metadata.Custom> {

        final Diff<Map<String, DataStream>> dataStreamDiff;

        DataStreamMetadataDiff(DataStreamMetadata before, DataStreamMetadata after) {
            this.dataStreamDiff = DiffableUtils.diff(before.dataStreams, after.dataStreams,
                DiffableUtils.getStringKeySerializer());
        }

        DataStreamMetadataDiff(StreamInput in) throws IOException {
            this.dataStreamDiff = DiffableUtils.readJdkMapDiff(in, DiffableUtils.getStringKeySerializer(),
                DataStream::new, DataStream::readDiffFrom);
        }

        @Override
        public Metadata.Custom apply(Metadata.Custom part) {
            return new DataStreamMetadata(dataStreamDiff.apply(((DataStreamMetadata) part).dataStreams));
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            dataStreamDiff.writeTo(out);
        }

        @Override
        public String getWriteableName() {
            return TYPE;
        }
    }
}
