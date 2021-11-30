/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.transform.action;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class GetCheckpointAction extends ActionType<GetCheckpointAction.Response> {

    public static final GetCheckpointAction INSTANCE = new GetCheckpointAction();
    public static final String NAME = "cluster:internal/transform/checkpoint";

    private GetCheckpointAction() {
        super(NAME, GetCheckpointAction.Response::new);
    }

    public static class Request extends ActionRequest implements IndicesRequest {

        private final String[] indices;
        private final IndicesOptions indicesOptions;

        public Request(StreamInput in) throws IOException {
            super(in);
            indices = in.readStringArray();
            indicesOptions = IndicesOptions.readIndicesOptions(in);
        }

        public Request(String[] indices, IndicesOptions indicesOptions) {
            this.indices = indices;
            this.indicesOptions = indicesOptions;
        }

        @Override
        public ActionRequestValidationException validate() {
            return null;
        }

        @Override
        public String[] indices() {
            return indices;
        }

        @Override
        public IndicesOptions indicesOptions() {
            return indicesOptions;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            Request that = (Request) obj;

            return Arrays.equals(indices, that.indices) && Objects.equals(indicesOptions, that.indicesOptions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(indices), indicesOptions);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeStringArrayNullable(indices);
            indicesOptions.writeIndicesOptions(out);
        }
    }

    public static class Response extends ActionResponse {

        private final Map<String, long[]> checkpoints;

        public Response(Map<String, long[]> checkpoints) {
            this.checkpoints = checkpoints;
        }

        public Response(StreamInput in) throws IOException {
            this.checkpoints = in.readOrderedMap(StreamInput::readString, StreamInput::readLongArray);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeMap(getCheckpoints(), StreamOutput::writeString, StreamOutput::writeLongArray);
        }

        public Map<String, long[]> getCheckpoints() {
            return Collections.unmodifiableMap(checkpoints);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            Response that = (Response) obj;

            return this.checkpoints.size() == that.checkpoints.size()
                && this.checkpoints.entrySet().stream().allMatch(e -> Arrays.equals(e.getValue(), that.checkpoints.get(e.getKey())));
        }

        @Override
        public int hashCode() {
            int hash = 1;

            for (Entry<String, long[]> e : checkpoints.entrySet()) {
                hash = 31 * hash + Objects.hash(e.getKey(), Arrays.hashCode(e.getValue()));
            }

            return hash;
        }
    }
}
