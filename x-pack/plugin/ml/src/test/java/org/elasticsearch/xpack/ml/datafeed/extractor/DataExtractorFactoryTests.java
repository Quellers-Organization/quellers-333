/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.datafeed.extractor;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.fieldcaps.FieldCapabilities;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesAction;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.core.ml.datafeed.ChunkingConfig;
import org.elasticsearch.xpack.core.ml.datafeed.DatafeedConfig;
import org.elasticsearch.xpack.core.ml.job.config.DataDescription;
import org.elasticsearch.xpack.core.ml.job.config.Job;
import org.elasticsearch.xpack.core.rollup.action.GetRollupIndexCapsAction;
import org.elasticsearch.xpack.core.rollup.action.RollableIndexCaps;
import org.elasticsearch.xpack.core.rollup.action.RollupJobCaps;
import org.elasticsearch.xpack.core.rollup.job.DateHistogramGroupConfig;
import org.elasticsearch.xpack.core.rollup.job.GroupConfig;
import org.elasticsearch.xpack.core.rollup.job.MetricConfig;
import org.elasticsearch.xpack.core.rollup.job.RollupJobConfig;
import org.elasticsearch.xpack.core.rollup.job.TermsGroupConfig;
import org.elasticsearch.xpack.ml.datafeed.DatafeedManagerTests;
import org.elasticsearch.xpack.ml.datafeed.extractor.aggregation.AggregationDataExtractorFactory;
import org.elasticsearch.xpack.ml.datafeed.extractor.chunked.ChunkedDataExtractorFactory;
import org.elasticsearch.xpack.ml.datafeed.extractor.aggregation.RollupDataExtractorFactory;
import org.elasticsearch.xpack.ml.datafeed.extractor.scroll.ScrollDataExtractorFactory;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DataExtractorFactoryTests extends ESTestCase {

    private FieldCapabilitiesResponse fieldsCapabilities;
    private GetRollupIndexCapsAction.Response getRollupIndexResponse;

    private Client client;

    @Before
    public void setUpTests() {
        client = mock(Client.class);
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(new ThreadContext(Settings.EMPTY));
        fieldsCapabilities = mock(FieldCapabilitiesResponse.class);
        givenAggregatableField("time", "date");
        givenAggregatableField("field", "keyword");

        getRollupIndexResponse = mock(GetRollupIndexCapsAction.Response.class);
        when(getRollupIndexResponse.getJobs()).thenReturn(new HashMap<>());

        doAnswer(invocationMock -> {
            @SuppressWarnings("raw_types")
            ActionListener listener = (ActionListener) invocationMock.getArguments()[2];
            listener.onResponse(fieldsCapabilities);
            return null;
        }).when(client).execute(same(FieldCapabilitiesAction.INSTANCE), any(), any());

        doAnswer(invocationMock -> {
            @SuppressWarnings("raw_types")
            ActionListener listener = (ActionListener) invocationMock.getArguments()[2];
            listener.onResponse(getRollupIndexResponse);
            return null;
        }).when(client).execute(same(GetRollupIndexCapsAction.INSTANCE), any(), any());
    }

    public void testCreateDataExtractorFactoryGivenDefaultScroll() {
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo").build();

        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
                dataExtractorFactory -> assertThat(dataExtractorFactory, instanceOf(ChunkedDataExtractorFactory.class)),
                e -> fail()
        );

        DataExtractorFactory.create(client, datafeedConfig, jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenScrollWithAutoChunk() {
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        datafeedConfig.setChunkingConfig(ChunkingConfig.newAuto());

        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
                dataExtractorFactory -> assertThat(dataExtractorFactory, instanceOf(ChunkedDataExtractorFactory.class)),
                e -> fail()
        );

        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenScrollWithOffChunk() {
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        datafeedConfig.setChunkingConfig(ChunkingConfig.newOff());

        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
                dataExtractorFactory -> assertThat(dataExtractorFactory, instanceOf(ScrollDataExtractorFactory.class)),
                e -> fail()
        );

        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenDefaultAggregation() {
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        MaxAggregationBuilder maxTime = AggregationBuilders.max("time").field("time");
        datafeedConfig.setAggregations(AggregatorFactories.builder().addAggregator(
                AggregationBuilders.histogram("time").interval(300000).subAggregation(maxTime).field("time")));

        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
                dataExtractorFactory -> assertThat(dataExtractorFactory, instanceOf(ChunkedDataExtractorFactory.class)),
                e -> fail()
        );

        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenAggregationWithOffChunk() {
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        datafeedConfig.setChunkingConfig(ChunkingConfig.newOff());
        MaxAggregationBuilder maxTime = AggregationBuilders.max("time").field("time");
        datafeedConfig.setAggregations(AggregatorFactories.builder().addAggregator(
                AggregationBuilders.histogram("time").interval(300000).subAggregation(maxTime).field("time")));

        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
                dataExtractorFactory -> assertThat(dataExtractorFactory, instanceOf(AggregationDataExtractorFactory.class)),
                e -> fail()
        );

        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenDefaultAggregationWithAutoChunk() {
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        MaxAggregationBuilder maxTime = AggregationBuilders.max("time").field("time");
        datafeedConfig.setAggregations(AggregatorFactories.builder().addAggregator(
                AggregationBuilders.histogram("time").interval(300000).subAggregation(maxTime).field("time")));
        datafeedConfig.setChunkingConfig(ChunkingConfig.newAuto());

        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
                dataExtractorFactory -> assertThat(dataExtractorFactory, instanceOf(ChunkedDataExtractorFactory.class)),
                e -> fail()
        );

        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenRollupAndValidAggregation() {
        givenAggregatableRollup("myField", "max", 5, "termField");
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        datafeedConfig.setChunkingConfig(ChunkingConfig.newOff());
        MaxAggregationBuilder maxTime = AggregationBuilders.max("time").field("time");
        MaxAggregationBuilder myField = AggregationBuilders.max("myField").field("myField");
        TermsAggregationBuilder myTerm = AggregationBuilders.terms("termAgg").field("termField").subAggregation(myField);
        datafeedConfig.setAggregations(AggregatorFactories.builder().addAggregator(
            AggregationBuilders.dateHistogram("time").interval(600_000).subAggregation(maxTime).subAggregation(myTerm).field("time")));
        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
            dataExtractorFactory -> assertThat(dataExtractorFactory, instanceOf(RollupDataExtractorFactory.class)),
            e -> fail()
        );
        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenRollupAndValidAggregationAndAutoChunk() {
        givenAggregatableRollup("myField", "max", 5, "termField");
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        datafeedConfig.setChunkingConfig(ChunkingConfig.newAuto());
        MaxAggregationBuilder maxTime = AggregationBuilders.max("time").field("time");
        MaxAggregationBuilder myField = AggregationBuilders.max("myField").field("myField");
        TermsAggregationBuilder myTerm = AggregationBuilders.terms("termAgg").field("termField").subAggregation(myField);
        datafeedConfig.setAggregations(AggregatorFactories.builder().addAggregator(
            AggregationBuilders.dateHistogram("time").interval(600_000).subAggregation(maxTime).subAggregation(myTerm).field("time")));
        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
            dataExtractorFactory -> assertThat(dataExtractorFactory, instanceOf(ChunkedDataExtractorFactory.class)),
            e -> fail()
        );
        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenRollupButNoAggregations() {
        givenAggregatableRollup("myField", "max", 5);
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        datafeedConfig.setChunkingConfig(ChunkingConfig.newOff());

        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
            dataExtractorFactory -> fail(),
            e -> {
                assertThat(e.getMessage(), equalTo("Aggregations are required when using Rollup indices"));
                assertThat(e, instanceOf(IllegalArgumentException.class));
            }
        );

        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenRollupWithBadInterval() {
        givenAggregatableRollup("myField", "max", 7, "termField");
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        datafeedConfig.setChunkingConfig(ChunkingConfig.newOff());
        MaxAggregationBuilder maxTime = AggregationBuilders.max("time").field("time");
        MaxAggregationBuilder myField = AggregationBuilders.max("myField").field("myField");
        TermsAggregationBuilder myTerm = AggregationBuilders.terms("termAgg").field("termField").subAggregation(myField);
        datafeedConfig.setAggregations(AggregatorFactories.builder().addAggregator(
            AggregationBuilders.dateHistogram("time").interval(600_000).subAggregation(maxTime).subAggregation(myTerm).field("time")));
        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
            dataExtractorFactory -> fail(),
            e -> {
                assertThat(e.getMessage(),
                    containsString("Rollup capabilities do not have a [date_histogram] aggregation with an interval " +
                        "that is a multiple of the datafeed's interval."));
                assertThat(e, instanceOf(IllegalArgumentException.class));
            }
        );
        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenRollupMissingTerms() {
        givenAggregatableRollup("myField", "max", 5);
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        datafeedConfig.setChunkingConfig(ChunkingConfig.newOff());
        MaxAggregationBuilder maxTime = AggregationBuilders.max("time").field("time");
        MaxAggregationBuilder myField = AggregationBuilders.max("myField").field("myField");
        TermsAggregationBuilder myTerm = AggregationBuilders.terms("termAgg").field("termField").subAggregation(myField);
        datafeedConfig.setAggregations(AggregatorFactories.builder().addAggregator(
            AggregationBuilders.dateHistogram("time").interval(600_000).subAggregation(maxTime).subAggregation(myTerm).field("time")));
        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
            dataExtractorFactory -> fail(),
            e -> {
                assertThat(e.getMessage(),
                    containsString("Rollup capabilities do not support all the datafeed aggregations at the desired interval."));
                assertThat(e, instanceOf(IllegalArgumentException.class));
            }
        );
        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    public void testCreateDataExtractorFactoryGivenRollupMissingMetric() {
        givenAggregatableRollup("myField", "max", 5, "termField");
        DataDescription.Builder dataDescription = new DataDescription.Builder();
        dataDescription.setTimeField("time");
        Job.Builder jobBuilder = DatafeedManagerTests.createDatafeedJob();
        jobBuilder.setDataDescription(dataDescription);
        DatafeedConfig.Builder datafeedConfig = DatafeedManagerTests.createDatafeedConfig("datafeed1", "foo");
        datafeedConfig.setChunkingConfig(ChunkingConfig.newOff());
        MaxAggregationBuilder maxTime = AggregationBuilders.max("time").field("time");
        MaxAggregationBuilder myField = AggregationBuilders.max("myField").field("otherField");
        TermsAggregationBuilder myTerm = AggregationBuilders.terms("termAgg").field("termField").subAggregation(myField);
        datafeedConfig.setAggregations(AggregatorFactories.builder().addAggregator(
            AggregationBuilders.dateHistogram("time").interval(600_000).subAggregation(maxTime).subAggregation(myTerm).field("time")));
        ActionListener<DataExtractorFactory> listener = ActionListener.wrap(
            dataExtractorFactory -> fail(),
            e -> {
                assertThat(e.getMessage(),
                    containsString("Rollup capabilities do not support all the datafeed aggregations at the desired interval."));
                assertThat(e, instanceOf(IllegalArgumentException.class));
            }
        );
        DataExtractorFactory.create(client, datafeedConfig.build(), jobBuilder.build(new Date()), listener);
    }

    private void givenAggregatableRollup(String field, String type, int minuteInterval, String... groupByTerms) {
        List<MetricConfig> metricConfigs = Arrays.asList(new MetricConfig(field, Collections.singletonList(type)),
            new MetricConfig("time", Arrays.asList("min", "max")));
        TermsGroupConfig termsGroupConfig = null;
        if (groupByTerms.length > 0) {
            termsGroupConfig = new TermsGroupConfig(groupByTerms);
        }
        RollupJobConfig rollupJobConfig = new RollupJobConfig("rollupJob1",
            "myIndexes*",
            "myIndex_rollup",
            "*/30 * * * * ?",
            300,
            new GroupConfig(
                new DateHistogramGroupConfig("time", DateHistogramInterval.minutes(minuteInterval)), null, termsGroupConfig),
            metricConfigs,
            null);
        RollupJobCaps rollupJobCaps = new RollupJobCaps(rollupJobConfig);
        RollableIndexCaps rollableIndexCaps = new RollableIndexCaps("myIndex_rollup", Collections.singletonList(rollupJobCaps));
        Map<String, RollableIndexCaps> jobs = new HashMap<>(1);
        jobs.put("rollupJob1", rollableIndexCaps);
        when(getRollupIndexResponse.getJobs()).thenReturn(jobs);
    }

    private void givenAggregatableField(String field, String type) {
        FieldCapabilities fieldCaps = mock(FieldCapabilities.class);
        when(fieldCaps.isSearchable()).thenReturn(true);
        when(fieldCaps.isAggregatable()).thenReturn(true);
        Map<String, FieldCapabilities> fieldCapsMap = new HashMap<>();
        fieldCapsMap.put(type, fieldCaps);
        when(fieldsCapabilities.getField(field)).thenReturn(fieldCapsMap);
    }
}
