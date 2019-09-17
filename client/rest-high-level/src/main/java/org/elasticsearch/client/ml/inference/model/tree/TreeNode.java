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
package org.elasticsearch.client.ml.inference.model.tree;

import org.elasticsearch.client.ml.job.config.Operator;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

public class TreeNode implements ToXContentObject {

    public static final String NAME = "tree_node";

    public static final ParseField DECISION_TYPE = new ParseField("decision_type");
    public static final ParseField THRESHOLD = new ParseField("threshold");
    public static final ParseField LEFT_CHILD = new ParseField("left_child");
    public static final ParseField RIGHT_CHILD = new ParseField("right_child");
    public static final ParseField DEFAULT_LEFT = new ParseField("default_left");
    public static final ParseField SPLIT_FEATURE = new ParseField("split_feature");
    public static final ParseField SPLIT_INDEX = new ParseField("split_index");
    public static final ParseField SPLIT_GAIN = new ParseField("split_gain");
    public static final ParseField INTERNAL_VALUE = new ParseField("internal_value");


    private static final ObjectParser<Builder, Void> PARSER = new ObjectParser<>(
            NAME,
            true,
            Builder::new);
    static {
        PARSER.declareDouble(Builder::setThreshold, THRESHOLD);
        PARSER.declareField(Builder::setOperator,
            p -> Operator.fromString(p.text()),
            DECISION_TYPE,
            ObjectParser.ValueType.STRING);
        PARSER.declareInt(Builder::setLeftChild, LEFT_CHILD);
        PARSER.declareInt(Builder::setRightChild, RIGHT_CHILD);
        PARSER.declareBoolean(Builder::setDefaultLeft, DEFAULT_LEFT);
        PARSER.declareInt(Builder::setSplitFeature, SPLIT_FEATURE);
        PARSER.declareInt(Builder::setSplitIndex, SPLIT_INDEX);
        PARSER.declareDouble(Builder::setSplitGain, SPLIT_GAIN);
        PARSER.declareDouble(Builder::setInternalValue, INTERNAL_VALUE);
    }

    public static Builder fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    private final Operator operator;
    private final Double threshold;
    private final Integer splitFeature;
    private final Integer splitIndex;
    private final Double splitGain;
    private final Double internalValue;
    private final Boolean defaultLeft;
    private final Integer leftChild;
    private final Integer rightChild;


    TreeNode(Operator operator,
             Double threshold,
             Integer splitFeature,
             Integer splitIndex,
             Double splitGain,
             Double internalValue,
             Boolean defaultLeft,
             Integer leftChild,
             Integer rightChild) {
        this.operator = operator;
        this.threshold  = threshold;
        this.splitFeature = splitFeature;
        this.splitIndex = splitIndex;
        this.splitGain  = splitGain;
        this.internalValue = internalValue;
        this.defaultLeft = defaultLeft;
        this.leftChild  = leftChild;
        this.rightChild = rightChild;
    }

    public Operator getOperator() {
        return operator;
    }

    public Double getThreshold() {
        return threshold;
    }

    public Integer getSplitFeature() {
        return splitFeature;
    }

    public Integer getSplitIndex() {
        return splitIndex;
    }

    public Double getSplitGain() {
        return splitGain;
    }

    public Double getInternalValue() {
        return internalValue;
    }

    public Boolean isDefaultLeft() {
        return defaultLeft;
    }

    public Integer getLeftChild() {
        return leftChild;
    }

    public Integer getRightChild() {
        return rightChild;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        addOptionalField(builder, DECISION_TYPE, operator);
        addOptionalField(builder, THRESHOLD, threshold);
        addOptionalField(builder, SPLIT_FEATURE, splitFeature);
        addOptionalField(builder, SPLIT_GAIN, splitGain);
        addOptionalField(builder, SPLIT_INDEX, splitIndex);
        addOptionalField(builder, INTERNAL_VALUE, internalValue);
        addOptionalField(builder, DEFAULT_LEFT, defaultLeft );
        addOptionalField(builder, LEFT_CHILD, leftChild);
        addOptionalField(builder, RIGHT_CHILD, rightChild);
        builder.endObject();
        return builder;
    }

    private void addOptionalField(XContentBuilder builder, ParseField field, Object value) throws IOException {
        if (value != null) {
            builder.field(field.getPreferredName(), value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TreeNode that = (TreeNode) o;
        return Objects.equals(operator, that.operator)
            && Objects.equals(threshold, that.threshold)
            && Objects.equals(splitFeature, that.splitFeature)
            && Objects.equals(splitIndex, that.splitIndex)
            && Objects.equals(splitGain, that.splitGain)
            && Objects.equals(internalValue, that.internalValue)
            && Objects.equals(defaultLeft, that.defaultLeft)
            && Objects.equals(leftChild, that.leftChild)
            && Objects.equals(rightChild, that.rightChild);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator,
            threshold,
            splitFeature,
            splitGain,
            splitIndex,
            internalValue,
            defaultLeft,
            leftChild,
            rightChild);
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Operator operator;
        private Double threshold;
        private Integer splitFeature;
        private Integer splitIndex;
        private Double splitGain;
        private Double internalValue;
        private Boolean defaultLeft;
        private Integer leftChild;
        private Integer rightChild;

        public Builder setOperator(Operator operator) {
            this.operator = operator;
            return this;
        }

        public Builder setThreshold(Double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder setSplitFeature(Integer splitFeature) {
            this.splitFeature = splitFeature;
            return this;
        }

        public Builder setSplitIndex(Integer splitIndex) {
            this.splitIndex = splitIndex;
            return this;
        }

        public Builder setSplitGain(Double splitGain) {
            this.splitGain = splitGain;
            return this;
        }

        public Builder setInternalValue(Double internalValue) {
            this.internalValue = internalValue;
            return this;
        }

        public Builder setDefaultLeft(Boolean defaultLeft) {
            this.defaultLeft = defaultLeft;
            return this;
        }

        public Builder setLeftChild(Integer leftChild) {
            this.leftChild = leftChild;
            return this;
        }

        public Integer getLeftChild() {
            return leftChild;
        }

        public Builder setRightChild(Integer rightChild) {
            this.rightChild = rightChild;
            return this;
        }

        public Integer getRightChild() {
            return rightChild;
        }

        public TreeNode build() {
            return new TreeNode(operator,
                threshold, 
                splitFeature, 
                splitIndex, 
                splitGain, 
                internalValue, 
                defaultLeft, 
                leftChild, 
                rightChild);
        }
    }
}
