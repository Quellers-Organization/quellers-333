/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.aggs.correlation;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CorrelativeValue implements Writeable, ToXContentObject {

    private static final ParseField EXPECTATIONS = new ParseField("expectations");
    private static final ParseField FRACTIONS = new ParseField("fractions");
    private static final ParseField DOC_COUNT = new ParseField("doc_count");

    @SuppressWarnings("unchecked")
    private static final ConstructingObjectParser<org.elasticsearch.xpack.ml.aggs.correlation.CorrelativeValue, Void> PARSER =
        new ConstructingObjectParser<>(
            "correlative_value",
            a -> new org.elasticsearch.xpack.ml.aggs.correlation.CorrelativeValue((List<Double>) a[0], (List<Double>) a[2], (Long) a[1])
        );
    static {
        PARSER.declareDoubleArray(ConstructingObjectParser.constructorArg(), EXPECTATIONS);
        PARSER.declareLong(ConstructingObjectParser.constructorArg(), DOC_COUNT);
        PARSER.declareDoubleArray(ConstructingObjectParser.optionalConstructorArg(), FRACTIONS);
    }

    private final double[] expectations;
    private final double[] fractions;
    private final long docCount;
    private CorrelativeValue(List<Double> values, List<Double> fractions, long docCount) {
        this(
            values.stream().mapToDouble(Double::doubleValue).toArray(),
            fractions == null ? null : fractions.stream().mapToDouble(Double::doubleValue).toArray(),
            docCount
        );
    }

    public CorrelativeValue(double[] values, double[] fractions, long docCount) {
        Objects.requireNonNull(values);
        if (fractions != null) {
            if (values.length != fractions.length) {
                throw new IllegalArgumentException("[expectations] and [fractions] must have the same length");
            }
        }
        if (docCount <= 0) {
            throw new IllegalArgumentException("[doc_count] must be a positive value");
        }
        if (values.length < 2) {
            throw new IllegalArgumentException("[expectations] must have a length of at least 2");
        }
        this.expectations = values;
        this.fractions = fractions;
        this.docCount = docCount;
    }

    public CorrelativeValue(StreamInput in) throws IOException {
        this.expectations = in.readDoubleArray();
        this.fractions = in.readBoolean() ? in.readDoubleArray() : null;
        this.docCount = in.readVLong();
    }

    public static org.elasticsearch.xpack.ml.aggs.correlation.CorrelativeValue fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    public double[] getExpectations() {
        return expectations;
    }

    public double[] getFractions() {
        return fractions;
    }

    public long getDocCount() {
        return docCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        org.elasticsearch.xpack.ml.aggs.correlation.CorrelativeValue that =
            (org.elasticsearch.xpack.ml.aggs.correlation.CorrelativeValue) o;
        return docCount == that.docCount && Arrays.equals(expectations, that.expectations) && Arrays.equals(fractions, that.fractions);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(docCount);
        result = 31 * result + Arrays.hashCode(expectations);
        result = 31 * result + Arrays.hashCode(fractions);
        return result;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(EXPECTATIONS.getPreferredName(), expectations);
        if (fractions != null) {
            builder.field(FRACTIONS.getPreferredName(), fractions);
        }
        builder.field(DOC_COUNT.getPreferredName(), docCount);
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeDoubleArray(expectations);
        out.writeBoolean(fractions != null);
        if (fractions != null) {
            out.writeDoubleArray(fractions);
        }
        out.writeVLong(docCount);
    }

    @Override
    public String toString() {
        return Strings.toString(this, true, true);
    }
}
