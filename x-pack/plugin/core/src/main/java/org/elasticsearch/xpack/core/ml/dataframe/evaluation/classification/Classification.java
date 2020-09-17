/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ml.dataframe.evaluation.classification;

import org.elasticsearch.Version;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.core.ml.dataframe.evaluation.Evaluation;
import org.elasticsearch.xpack.core.ml.dataframe.evaluation.EvaluationFields;
import org.elasticsearch.xpack.core.ml.dataframe.evaluation.EvaluationMetric;
import org.elasticsearch.xpack.core.ml.utils.ExceptionsHelper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.xpack.core.ml.dataframe.evaluation.EvaluationFields.ACTUAL_FIELD;
import static org.elasticsearch.xpack.core.ml.dataframe.evaluation.EvaluationFields.PREDICTED_CLASS_FIELD;
import static org.elasticsearch.xpack.core.ml.dataframe.evaluation.EvaluationFields.PREDICTED_FIELD;
import static org.elasticsearch.xpack.core.ml.dataframe.evaluation.EvaluationFields.PREDICTED_PROBABILITY_FIELD;
import static org.elasticsearch.xpack.core.ml.dataframe.evaluation.MlEvaluationNamedXContentProvider.registeredMetricName;

/**
 * Evaluation of classification results.
 */
public class Classification implements Evaluation {

    public static final ParseField NAME = new ParseField("classification");

    private static final ParseField METRICS = new ParseField("metrics");

    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<Classification, Void> PARSER =
        new ConstructingObjectParser<>(
            NAME.getPreferredName(),
            a -> new Classification((String) a[0], (String) a[1], (String) a[2], (String) a[3], (List<EvaluationMetric>) a[4]));

    static {
        PARSER.declareString(ConstructingObjectParser.constructorArg(), ACTUAL_FIELD);
        PARSER.declareString(ConstructingObjectParser.optionalConstructorArg(), PREDICTED_FIELD);
        PARSER.declareString(ConstructingObjectParser.optionalConstructorArg(), PREDICTED_CLASS_FIELD);
        PARSER.declareString(ConstructingObjectParser.optionalConstructorArg(), PREDICTED_PROBABILITY_FIELD);
        PARSER.declareNamedObjects(ConstructingObjectParser.optionalConstructorArg(),
            (p, c, n) -> p.namedObject(EvaluationMetric.class, registeredMetricName(NAME.getPreferredName(), n), c), METRICS);
    }

    public static Classification fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    /**
     * The collection of fields in the index being evaluated.
     *   fields.getActualField() is assumed to be a ground truth label.
     *   fields.getPredictedField() is assumed to be a predicted label.
     *   fields.getPredictedClassField() and fields.getPredictedProbabilityField() are assumed to be properties under the same nested field.
     */
    private final EvaluationFields fields;

    /**
     * The list of metrics to calculate
     */
    private final List<EvaluationMetric> metrics;

    public Classification(String actualField,
                          @Nullable String predictedField,
                          @Nullable String predictedClassField,
                          @Nullable String predictedProbabilityField,
                          @Nullable List<EvaluationMetric> metrics) {
        String topClassesField = null;
        if (predictedClassField != null && predictedProbabilityField != null) {
            int predictedClassFieldLastDot = predictedClassField.lastIndexOf(".");
            int predictedProbabilityFieldLastDot = predictedProbabilityField.lastIndexOf(".");
            if (predictedClassFieldLastDot == -1) {
                throw ExceptionsHelper.badRequestException(
                    "The value of [{}] must contain a dot ('.') but it didn't ([{}])",
                    PREDICTED_CLASS_FIELD.getPreferredName(), predictedClassField);
            }
            if (predictedProbabilityFieldLastDot == -1) {
                throw ExceptionsHelper.badRequestException(
                    "The value of [{}] must contain a dot ('.') but it didn't ([{}])",
                    PREDICTED_PROBABILITY_FIELD.getPreferredName(), predictedProbabilityField);
            }
            String predictedClassFieldPrefix = predictedClassField.substring(0, predictedClassFieldLastDot);
            String predictedProbabilityFieldPrefix = predictedProbabilityField.substring(0, predictedProbabilityFieldLastDot);
            if (predictedClassFieldPrefix.equals(predictedProbabilityFieldPrefix) == false) {
                throw ExceptionsHelper.badRequestException(
                    "The values of [{}] and [{}] must start with the same prefix but they didn't ([{}] vs [{}])",
                    PREDICTED_CLASS_FIELD.getPreferredName(),
                    PREDICTED_PROBABILITY_FIELD.getPreferredName(),
                    predictedClassFieldPrefix,
                    predictedProbabilityFieldPrefix);
            }
            topClassesField = predictedClassFieldPrefix;
        }
        this.fields =
            new EvaluationFields(
                ExceptionsHelper.requireNonNull(actualField, ACTUAL_FIELD),
                predictedField,
                topClassesField,
                predictedClassField,
                predictedProbabilityField);
        this.metrics = initMetrics(metrics, Classification::defaultMetrics);
    }

    private static List<EvaluationMetric> defaultMetrics() {
        return Arrays.asList(new MulticlassConfusionMatrix());
    }

    public Classification(StreamInput in) throws IOException {
        if (in.getVersion().onOrAfter(Version.V_8_0_0)) {
            this.fields =
                new EvaluationFields(
                    in.readString(), in.readOptionalString(), in.readOptionalString(), in.readOptionalString(), in.readOptionalString());
        } else {
            this.fields = new EvaluationFields(in.readString(), in.readString(), null, null, null);
        }
        this.metrics = in.readNamedWriteableList(EvaluationMetric.class);
    }

    @Override
    public String getName() {
        return NAME.getPreferredName();
    }

    @Override
    public EvaluationFields getFields() {
        return fields;
    }

    @Override
    public List<EvaluationMetric> getMetrics() {
        return metrics;
    }

    @Override
    public String getWriteableName() {
        return NAME.getPreferredName();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(fields.getActualField());
        if (out.getVersion().onOrAfter(Version.V_8_0_0)) {
            out.writeOptionalString(fields.getPredictedField());
            out.writeOptionalString(fields.getTopClassesField());
            out.writeOptionalString(fields.getPredictedClassField());
            out.writeOptionalString(fields.getPredictedProbabilityField());
        } else {
            out.writeString(fields.getPredictedField());
        }
        out.writeNamedWriteableList(metrics);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ACTUAL_FIELD.getPreferredName(), fields.getActualField());
        if (fields.getPredictedField() != null) {
            builder.field(PREDICTED_FIELD.getPreferredName(), fields.getPredictedField());
        }
        if (fields.getPredictedClassField() != null) {
            builder.field(PREDICTED_CLASS_FIELD.getPreferredName(), fields.getPredictedClassField());
        }
        if (fields.getPredictedProbabilityField() != null) {
            builder.field(PREDICTED_PROBABILITY_FIELD.getPreferredName(), fields.getPredictedProbabilityField());
        }
        builder.startObject(METRICS.getPreferredName());
        for (EvaluationMetric metric : metrics) {
            builder.field(metric.getName(), metric);
        }
        builder.endObject();

        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Classification that = (Classification) o;
        return Objects.equals(that.fields, this.fields)
            && Objects.equals(that.metrics, this.metrics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields, metrics);
    }
}
