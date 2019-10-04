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
package org.elasticsearch.client.ml.inference;

import org.elasticsearch.client.ml.inference.preprocessing.PreProcessor;
import org.elasticsearch.client.ml.inference.trainedmodel.TrainedModel;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TrainedModelDefinition implements ToXContentObject {

    public static final String NAME = "trained_model_doc";

    public static final ParseField TRAINED_MODEL = new ParseField("trained_model");
    public static final ParseField PREPROCESSORS = new ParseField("preprocessors");
    public static final ParseField INPUT = new ParseField("input");

    public static final ObjectParser<Builder, Void> PARSER = new ObjectParser<>(NAME,
            true,
            TrainedModelDefinition.Builder::new);
    static {
        PARSER.declareNamedObjects(TrainedModelDefinition.Builder::setTrainedModel,
            (p, c, n) -> p.namedObject(TrainedModel.class, n, null),
            (modelDocBuilder) -> { /* Noop does not matter client side*/ },
            TRAINED_MODEL);
        PARSER.declareNamedObjects(TrainedModelDefinition.Builder::setPreProcessors,
            (p, c, n) -> p.namedObject(PreProcessor.class, n, null),
            (trainedModelDefBuilder) -> {/* Does not matter client side*/ },
            PREPROCESSORS);
        PARSER.declareObject(TrainedModelDefinition.Builder::setInput, (p, c) -> Input.fromXContent(p), INPUT);
    }

    public static TrainedModelDefinition.Builder fromXContent(XContentParser parser) throws IOException {
        return PARSER.parse(parser, null);
    }

    private final TrainedModel trainedModel;
    private final List<PreProcessor> preProcessors;
    private final Input input;

    TrainedModelDefinition(TrainedModel trainedModel, List<PreProcessor> preProcessors, Input input) {
        this.trainedModel = trainedModel;
        this.preProcessors = preProcessors == null ? Collections.emptyList() : Collections.unmodifiableList(preProcessors);
        this.input = input;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        NamedXContentObjectHelper.writeNamedObjects(builder,
            params,
            false,
            TRAINED_MODEL.getPreferredName(),
            Collections.singletonList(trainedModel));
        NamedXContentObjectHelper.writeNamedObjects(builder,
            params,
            true,
            PREPROCESSORS.getPreferredName(),
            preProcessors);
        if (input != null) {
            builder.field(INPUT.getPreferredName(), input);
        }
        builder.endObject();
        return builder;
    }

    public TrainedModel getTrainedModel() {
        return trainedModel;
    }

    public List<PreProcessor> getPreProcessors() {
        return preProcessors;
    }

    public Input getInput() {
        return input;
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainedModelDefinition that = (TrainedModelDefinition) o;
        return Objects.equals(trainedModel, that.trainedModel) &&
            Objects.equals(preProcessors, that.preProcessors) &&
            Objects.equals(input, that.input);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainedModel, preProcessors, input);
    }

    public static class Builder {

        private List<PreProcessor> preProcessors;
        private TrainedModel trainedModel;
        private Input input;

        public Builder setPreProcessors(List<PreProcessor> preProcessors) {
            this.preProcessors = preProcessors;
            return this;
        }

        public Builder setTrainedModel(TrainedModel trainedModel) {
            this.trainedModel = trainedModel;
            return this;
        }

        public Builder setInput(Input input) {
            this.input = input;
            return this;
        }

        private Builder setTrainedModel(List<TrainedModel> trainedModel) {
            assert trainedModel.size() == 1;
            return setTrainedModel(trainedModel.get(0));
        }

        public TrainedModelDefinition build() {
            return new TrainedModelDefinition(this.trainedModel, this.preProcessors, this.input);
        }
    }

    public static class Input implements ToXContentObject {

        public static final String NAME = "trained_mode_definition_input";
        public static final ParseField FEATURE_NAMES = new ParseField("feature_names");

        @SuppressWarnings("unchecked")
        public static final ConstructingObjectParser<Input, Void> PARSER = new ConstructingObjectParser<>(NAME,
                true,
                a -> new Input((List<String>)a[0]));
        static {
            PARSER.declareStringArray(ConstructingObjectParser.constructorArg(), FEATURE_NAMES);
        }

        public static Input fromXContent(XContentParser parser) throws IOException {
            return PARSER.parse(parser, null);
        }

        private final List<String> featureNames;

        public Input(List<String> featureNames) {
            this.featureNames = featureNames;
        }

        public List<String> getFeatureNames() {
            return featureNames;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field(FEATURE_NAMES.getPreferredName(), featureNames);
            builder.endObject();
            return builder;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TrainedModelDefinition.Input that = (TrainedModelDefinition.Input) o;
            return Objects.equals(featureNames, that.featureNames);
        }

        @Override
        public int hashCode() {
            return Objects.hash(featureNames);
        }

    }

}
