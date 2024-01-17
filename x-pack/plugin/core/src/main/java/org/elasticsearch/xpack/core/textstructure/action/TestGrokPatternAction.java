/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.textstructure.action;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.grok.GrokCaptureExtracter;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.action.ValidateActions.addValidationError;

public class TestGrokPatternAction extends ActionType<TestGrokPatternAction.Response> {

    public static final TestGrokPatternAction INSTANCE = new TestGrokPatternAction();
    public static final String NAME = "cluster:monitor/text_structure/testgrokpattern";

    private TestGrokPatternAction() {
        super(NAME, Response::new);
    }

    public static class Request extends ActionRequest {

        public static final ParseField GROK_PATTERN = new ParseField("grok_pattern");
        public static final ParseField TEXT = new ParseField("text");

        private static final ObjectParser<Request.Builder, Void> PARSER = createParser();

        private static ObjectParser<Request.Builder, Void> createParser() {
            ObjectParser<Request.Builder, Void> parser = new ObjectParser<>("textstructure/testgrokpattern", false, Request.Builder::new);
            parser.declareString(Request.Builder::grokPattern, GROK_PATTERN);
            parser.declareStringArray(Request.Builder::texts, TEXT);
            return parser;
        }

        public static class Builder {
            private String grokPattern;
            private List<String> text;

            public void grokPattern(String grokPattern) {
                this.grokPattern = grokPattern;
            }

            public void texts(List<String> text) {
                this.text = text;
            }

            public Request build() {
                return new Request(grokPattern, text);
            }
        }

        private final String grokPattern;
        private final List<String> text;

        private Request(String grokPattern, List<String> text) {
            this.grokPattern = grokPattern;
            this.text = text;
        }

        public Request(StreamInput in) throws IOException {
            super(in);
            grokPattern = in.readString();
            text = in.readStringCollectionAsList();
        }

        public static Request parseRequest(XContentParser parser) throws IOException {
            return PARSER.parse(parser, null).build();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(grokPattern);
            out.writeStringCollection(text);
        }

        public String getGrokPattern() {
            return grokPattern;
        }

        public List<String> getText() {
            return text;
        }

        @Override
        public ActionRequestValidationException validate() {
            ActionRequestValidationException validationException = null;
            if (grokPattern == null) {
                validationException = addValidationError("[" + GROK_PATTERN.getPreferredName() + "] missing", validationException);
            }
            if (text == null) {
                validationException = addValidationError("[" + TEXT.getPreferredName() + "] missing", validationException);
            }
            return validationException;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Request request = (Request) o;
            return Objects.equals(grokPattern, request.grokPattern) && Objects.equals(text, request.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(grokPattern, text);
        }

        @Override
        public String toString() {
            return "Request{" + "grokPattern='" + grokPattern + '\'' + ", text=" + text + '}';
        }
    }

    public static class Response extends ActionResponse implements ToXContentObject, Writeable {

        private final List<Map<String, Object>> ranges;

        public Response(List<Map<String, Object>> ranges) {
            this.ranges = ranges;
        }

        public Response(StreamInput in) throws IOException {
            super(in);
            ranges = in.readCollectionAsList(StreamInput::readGenericMap);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.startArray("matches");
            for (Map<String, Object> ranges : ranges) {
                builder.startObject();
                builder.field("matched", ranges != null);
                if (ranges != null) {
                    builder.startObject("fields");
                    for (Map.Entry<String, Object> rangeOrList : ranges.entrySet()) {
                        if (rangeOrList.getValue() instanceof GrokCaptureExtracter.Range) {
                            GrokCaptureExtracter.Range range = (GrokCaptureExtracter.Range) rangeOrList.getValue();
                            builder.startObject(rangeOrList.getKey());
                            builder.field("match", range.match());
                            builder.field("offset", range.offset());
                            builder.field("length", range.length());
                            builder.endObject();
                        } else if (rangeOrList.getValue() instanceof List) {
                            builder.startArray(rangeOrList.getKey());
                            for (Object rangeObject : (List<?>) rangeOrList.getValue()) {
                                GrokCaptureExtracter.Range range = (GrokCaptureExtracter.Range) rangeObject;
                                builder.startObject();
                                builder.field("match", range.match());
                                builder.field("offset", range.offset());
                                builder.field("length", range.length());
                                builder.endObject();
                            }
                            builder.endArray();
                        }
                    }
                    builder.endObject();
                }
                builder.endObject();
            }
            builder.endArray();
            builder.endObject();
            return builder;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeGenericList(ranges, StreamOutput::writeGenericMap);
        }
    }
}
