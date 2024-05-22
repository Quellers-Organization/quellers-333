/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.ingest.geoip.stats;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public record DownloadedDatabaseInfo(String name, DownloadAttempt successfulAttempt, DownloadAttempt failedAttempt)
    implements
        Writeable,
        ToXContent {
    public DownloadedDatabaseInfo(StreamInput in) throws IOException {
        this(in.readString(), in.readBoolean() ? new DownloadAttempt(in) : null, in.readBoolean() ? new DownloadAttempt(in) : null);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        if (successfulAttempt == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            successfulAttempt.writeTo(out);
        }
        if (failedAttempt == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            failedAttempt.writeTo(out);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("name", name);
        if (successfulAttempt != null) {
            builder.field("last_success", successfulAttempt);
        }
        if (failedAttempt != null) {
            builder.field("last_failure", failedAttempt);
        }
        builder.endObject();
        return builder;
    }

    public record DownloadAttempt(
        String md5,
        Long downloadAttemptTimeInMillis,
        Long downloadDurationInMillis,
        String provider,
        Long buildDateInMillis,
        String errorMessage
    ) implements Writeable, ToXContent {

        DownloadAttempt(StreamInput in) throws IOException {
            this(
                in.readOptionalString(),
                in.readOptionalLong(),
                in.readOptionalVLong(),
                in.readOptionalString(),
                in.readOptionalLong(),
                in.readOptionalString()
            );
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeOptionalString(md5);
            out.writeOptionalLong(downloadAttemptTimeInMillis);
            out.writeOptionalVLong(downloadDurationInMillis);
            out.writeOptionalString(provider);
            out.writeOptionalLong(buildDateInMillis);
            out.writeOptionalString(errorMessage);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            if (md5 != null) {
                builder.field("md5", md5);
            }
            if (downloadAttemptTimeInMillis != null) {
                builder.timeField("download_date_in_millis", "download_date", downloadAttemptTimeInMillis);
            }
            if (downloadDurationInMillis != null) {
                builder.field("download_time", TimeValue.timeValueMillis(downloadDurationInMillis).getStringRep());
            }
            if (provider != null) {
                builder.field("provider", provider);
            }
            if (buildDateInMillis != null) {
                builder.timeField("build_date_in_millis", "build_date", buildDateInMillis);
            }
            if (errorMessage != null) {
                builder.field("error_message", errorMessage);
            }
            builder.endObject();
            return builder;
        }
    }
}
