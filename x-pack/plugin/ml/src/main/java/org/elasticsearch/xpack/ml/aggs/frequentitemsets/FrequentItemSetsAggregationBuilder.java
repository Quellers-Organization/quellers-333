/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.aggs.frequentitemsets;

import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregatorFactories.Builder;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.support.AggregationContext;
import org.elasticsearch.search.aggregations.support.MultiValuesSourceFieldConfig;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ContextParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class FrequentItemSetsAggregationBuilder extends AbstractAggregationBuilder<FrequentItemSetsAggregationBuilder> {

    public static final String NAME = "frequent_items";

    public static final double DEFAULT_MINIMUM_SUPPORT = 0.01;
    public static final int DEFAULT_MINIMUM_SET_SIZE = 1;
    public static final int DEFAULT_SIZE = 10;

    public static final ParseField MINIMUM_SUPPORT = new ParseField("minimum_support");
    public static final ParseField MINIMUM_SET_SIZE = new ParseField("minimum_set_size");
    public static final ParseField FIELDS = new ParseField("fields");

    public static final ConstructingObjectParser<FrequentItemSetsAggregationBuilder, String> PARSER = new ConstructingObjectParser<>(
        NAME,
        false,
        (args, context) -> {
            @SuppressWarnings("unchecked")
            List<MultiValuesSourceFieldConfig> fields = (List<MultiValuesSourceFieldConfig>) args[0];
            double minimumSupport = args[1] == null ? DEFAULT_MINIMUM_SUPPORT : (double) args[1];
            int minimumSetSize = args[2] == null ? DEFAULT_MINIMUM_SET_SIZE : (int) args[2];
            int size = args[3] == null ? DEFAULT_SIZE : (int) args[3];

            return new FrequentItemSetsAggregationBuilder(context, fields, minimumSupport, minimumSetSize, size);
        }
    );

    static {
        ContextParser<Void, MultiValuesSourceFieldConfig.Builder> metricParser = MultiValuesSourceFieldConfig.parserBuilder(
            false, // scriptable
            true,  // timezone aware
            false, // filtered
            true   // format
        );
        PARSER.declareObjectArray(ConstructingObjectParser.constructorArg(), (p, n) -> metricParser.parse(p, null).build(), FIELDS);
        PARSER.declareDouble(ConstructingObjectParser.optionalConstructorArg(), MINIMUM_SUPPORT);
        PARSER.declareInt(ConstructingObjectParser.optionalConstructorArg(), MINIMUM_SET_SIZE);
        PARSER.declareInt(ConstructingObjectParser.optionalConstructorArg(), Aggregation.CommonFields.SIZE);
    }

    private final List<MultiValuesSourceFieldConfig> fields;
    private final double minimumSupport;
    private final int minimumSetSize;
    private final int size;

    public FrequentItemSetsAggregationBuilder(
        String name,
        List<MultiValuesSourceFieldConfig> fields,
        double minimumSupport,
        int minimumSetSize,
        int size
    ) {
        super(name);
        this.fields = fields;
        if (minimumSupport <= 0.0 || minimumSupport > 1.0) {
            throw new IllegalArgumentException(
                "[minimum_support] must be greater than 0 and less or equal to 1. Found [" + minimumSupport + "] in [" + name + "]"
            );
        }
        this.minimumSupport = minimumSupport;
        if (minimumSetSize <= 0) {
            throw new IllegalArgumentException(
                "[minimum_set_size] must be greater than 0. Found [" + minimumSetSize + "] in [" + name + "]"
            );
        }
        this.minimumSetSize = minimumSetSize;
        if (size <= 0) {
            throw new IllegalArgumentException("[size] must be greater than 0. Found [" + size + "] in [" + name + "]");
        }
        this.size = size;
    }

    public FrequentItemSetsAggregationBuilder(StreamInput in) throws IOException {
        super(in);
        this.fields = in.readList(MultiValuesSourceFieldConfig::new);
        this.minimumSupport = in.readDouble();
        this.minimumSetSize = in.readVInt();
        this.size = in.readVInt();
    }

    @Override
    public boolean supportsSampling() {
        return true;
    }

    @Override
    protected AggregationBuilder shallowCopy(Builder factoriesBuilder, Map<String, Object> metadata) {
        return new FrequentItemSetsAggregationBuilder(name, fields, minimumSupport, minimumSetSize, size);
    }

    @Override
    public String getType() {
        return NAME;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeList(fields);
        out.writeDouble(minimumSupport);
        out.writeVInt(minimumSetSize);
        out.writeVInt(size);
    }

    @Override
    protected AggregatorFactory doBuild(AggregationContext context, AggregatorFactory parent, Builder subfactoriesBuilder)
        throws IOException {

        return new FrequentItemSetsAggregatorFactory(
            name,
            context,
            parent,
            subfactoriesBuilder,
            metadata,
            fields,
            minimumSupport,
            minimumSetSize,
            size
        );
    }

    @Override
    protected XContentBuilder internalXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.startArray(FIELDS.getPreferredName());
        for (MultiValuesSourceFieldConfig field : fields) {
            field.toXContent(builder, params);
        }
        builder.endArray();
        builder.field(MINIMUM_SUPPORT.getPreferredName(), minimumSupport);
        builder.field(MINIMUM_SET_SIZE.getPreferredName(), minimumSetSize);
        builder.field(Aggregation.CommonFields.SIZE.getPreferredName(), size);
        builder.endObject();
        return builder;
    }

    @Override
    public BucketCardinality bucketCardinality() {
        return BucketCardinality.MANY;
    }

    // this is a leaf only aggregation
    @Override
    public FrequentItemSetsAggregationBuilder subAggregations(Builder subFactories) {
        throw new IllegalArgumentException("Aggregator [" + name + "] of type [" + getType() + "] cannot accept sub-aggregations");
    }

    @Override
    public FrequentItemSetsAggregationBuilder subAggregation(AggregationBuilder aggregation) {
        throw new IllegalArgumentException("Aggregator [" + name + "] of type [" + getType() + "] cannot accept sub-aggregations");
    }

    @Override
    public Version getMinimalSupportedVersion() {
        return Version.V_8_4_0;
    }

}
