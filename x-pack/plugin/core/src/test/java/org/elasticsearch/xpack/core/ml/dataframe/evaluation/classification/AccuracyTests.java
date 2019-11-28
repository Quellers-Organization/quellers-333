/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ml.dataframe.evaluation.classification;

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.test.AbstractSerializingTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.elasticsearch.test.hamcrest.OptionalMatchers.isEmpty;
import static org.elasticsearch.xpack.core.ml.dataframe.evaluation.MockAggregations.mockSingleValue;
import static org.elasticsearch.xpack.core.ml.dataframe.evaluation.MockAggregations.mockTerms;
import static org.elasticsearch.xpack.core.ml.dataframe.evaluation.classification.TupleMatchers.isTuple;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

public class AccuracyTests extends AbstractSerializingTestCase<Accuracy> {

    @Override
    protected Accuracy doParseInstance(XContentParser parser) throws IOException {
        return Accuracy.fromXContent(parser);
    }

    @Override
    protected Accuracy createTestInstance() {
        return createRandom();
    }

    @Override
    protected Writeable.Reader<Accuracy> instanceReader() {
        return Accuracy::new;
    }

    @Override
    protected boolean supportsUnknownFields() {
        return true;
    }

    public static Accuracy createRandom() {
        return new Accuracy();
    }

    public void testProcess() {
        Aggregations aggs = new Aggregations(Arrays.asList(
            mockTerms(Accuracy.BY_ACTUAL_CLASS_AGG_NAME),
            mockSingleValue(Accuracy.OVERALL_ACCURACY_AGG_NAME, 0.8123),
            mockSingleValue("some_other_single_metric_agg", 0.2377)
        ));

        Accuracy accuracy = new Accuracy();
        accuracy.process(aggs);

        assertThat(accuracy.aggs("act", "pred"), isTuple(empty(), empty()));
        assertThat(accuracy.getResult().get(), equalTo(new Accuracy.Result(List.of(), 0.8123)));
    }

    public void testProcess_GivenMissingAgg() {
        {
            Aggregations aggs = new Aggregations(Arrays.asList(
                mockTerms(Accuracy.BY_ACTUAL_CLASS_AGG_NAME),
                mockSingleValue("some_other_single_metric_agg", 0.2377)
            ));
            Accuracy accuracy = new Accuracy();
            accuracy.process(aggs);
            assertThat(accuracy.getResult(), isEmpty());
        }
        {
            Aggregations aggs = new Aggregations(Arrays.asList(
                mockSingleValue(Accuracy.OVERALL_ACCURACY_AGG_NAME, 0.8123),
                mockSingleValue("some_other_single_metric_agg", 0.2377)
            ));
            Accuracy accuracy = new Accuracy();
            accuracy.process(aggs);
            assertThat(accuracy.getResult(), isEmpty());
        }
    }

    public void testProcess_GivenAggOfWrongType() {
        {
            Aggregations aggs = new Aggregations(Arrays.asList(
                mockTerms(Accuracy.BY_ACTUAL_CLASS_AGG_NAME),
                mockTerms(Accuracy.OVERALL_ACCURACY_AGG_NAME)
            ));
            Accuracy accuracy = new Accuracy();
            accuracy.process(aggs);
            assertThat(accuracy.getResult(), isEmpty());
        }
        {
            Aggregations aggs = new Aggregations(Arrays.asList(
                mockSingleValue(Accuracy.BY_ACTUAL_CLASS_AGG_NAME, 1.0),
                mockSingleValue(Accuracy.OVERALL_ACCURACY_AGG_NAME, 0.8123)
            ));
            Accuracy accuracy = new Accuracy();
            accuracy.process(aggs);
            assertThat(accuracy.getResult(), isEmpty());
        }
    }
}
