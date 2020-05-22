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

package org.elasticsearch.search.aggregations.bucket.histogram;

import org.elasticsearch.common.breaker.CircuitBreaker;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.MockBigArrays;
import org.elasticsearch.common.util.MockPageCacheRecycler;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.aggregations.MultiBucketConsumerService;
import org.elasticsearch.search.aggregations.ParsedMultiBucketAggregation;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.test.InternalMultiBucketAggregationTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InternalVariableWidthHistogramTests extends
    InternalMultiBucketAggregationTestCase<InternalVariableWidthHistogram>{

    private DocValueFormat format;
    private InternalVariableWidthHistogram.EmptyBucketInfo emptyBucktInfo;
        private int numBuckets;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        format = randomNumericDocValueFormat();
        emptyBucktInfo = new InternalVariableWidthHistogram.EmptyBucketInfo(InternalAggregations.EMPTY);
        this.numBuckets = 3;
    }

    @Override
    protected InternalVariableWidthHistogram createTestInstance(String name,
                                                                Map<String, Object> metaData,
                                                                InternalAggregations aggregations) {
        List<InternalVariableWidthHistogram.Bucket> buckets = new ArrayList<>();
        return new InternalVariableWidthHistogram(name, buckets, emptyBucktInfo, numBuckets, format, metaData);
    }

    @Override
    protected Class<? extends ParsedMultiBucketAggregation> implementationClass() {
        return ParsedVariableWidthHistogram.class;
    }
    @Override
    protected InternalVariableWidthHistogram mutateInstance(InternalVariableWidthHistogram instance) {
        String name = instance.getName();
        List<InternalVariableWidthHistogram.Bucket> buckets = instance.getBuckets();
        int targetBuckets = instance.getTargetBuckets();
        InternalVariableWidthHistogram.EmptyBucketInfo emptyBucketInfo = instance.getEmptyBucketInfo();
        Map<String, Object> metadata = instance.getMetadata();
        switch (between(0, 2)) {
            case 0:
                name += randomAlphaOfLength(5);
                break;
            case 1:
                buckets = new ArrayList<>(buckets);
                double boundMin = randomDouble();
                double boundMax = Math.abs(boundMin) * 2;
                buckets.add(new InternalVariableWidthHistogram.Bucket(
                    randomDouble(),
                    new InternalVariableWidthHistogram.Bucket.BucketBounds(boundMin, boundMax),
                    randomIntBetween(1, 100),
                    format,
                    InternalAggregations.EMPTY
                ));
                break;
            case 2:
                emptyBucketInfo = null;
                if (metadata == null) {
                    metadata = new HashMap<>(1);
                } else {
                    metadata = new HashMap<>(instance.getMetadata());
                }
                metadata.put(randomAlphaOfLength(15), randomInt());
                break;
            default:
                throw new AssertionError("Illegal randomisation branch");
        }
        return new InternalVariableWidthHistogram(name, buckets, emptyBucketInfo, targetBuckets, format, metadata);
    }

    public void testSingleShardReduceLong() {
        InternalVariableWidthHistogram dummy_histogram = createTestInstance();
        List<InternalVariableWidthHistogram.Bucket> buckets = new ArrayList<>();
        for (long value : new long[]{1, 2, 5, 10, 12, 200}) {
            InternalVariableWidthHistogram.Bucket.BucketBounds bounds =  new InternalVariableWidthHistogram.Bucket.BucketBounds(value, value + 1);
            InternalVariableWidthHistogram.Bucket bucket = new InternalVariableWidthHistogram.Bucket(
                value, bounds, 3, format, InternalAggregations.EMPTY
            );
            buckets.add(bucket);
        }
        InternalVariableWidthHistogram histogram = dummy_histogram.create(buckets);

        MockBigArrays bigArrays = new MockBigArrays(new MockPageCacheRecycler(Settings.EMPTY), new NoneCircuitBreakerService());
        ScriptService mockScriptService = mockScriptService();

        MultiBucketConsumerService.MultiBucketConsumer bucketConsumer = new MultiBucketConsumerService.MultiBucketConsumer(DEFAULT_MAX_BUCKETS,
            new NoneCircuitBreakerService().getBreaker(CircuitBreaker.REQUEST));
        InternalAggregation.ReduceContext context = InternalAggregation.ReduceContext.forFinalReduction(
            bigArrays, mockScriptService, bucketConsumer, PipelineAggregator.PipelineTree.EMPTY);

        ArrayList<InternalAggregation> aggs = new ArrayList<>();
        aggs.add(histogram);
        List<InternalVariableWidthHistogram.Bucket> reduced_buckets = histogram.reduceBuckets(aggs, context);

        // Final clusters should be [ (1,2,5), (10,12), 200) ]
        // Final centroids should be [ 1.5, 10, 200 ]
        // Final keys should be [ 1, 5, 200 ]
        double double_error = 1d / 10000d;
        assertEquals((8d/3d), reduced_buckets.get(0).centroid(), double_error);
        assertEquals(1d, reduced_buckets.get(0).getKeyForXContent(), double_error);
        assertEquals(9, reduced_buckets.get(0).getDocCount());
        assertEquals(11d, reduced_buckets.get(1).centroid(), double_error);
        assertEquals(10d, reduced_buckets.get(1).getKeyForXContent(), double_error);
        assertEquals(6, reduced_buckets.get(1).getDocCount());
        assertEquals(200d, reduced_buckets.get(2).centroid(), double_error);
        assertEquals(200d, reduced_buckets.get(2).getKeyForXContent(), double_error);
        assertEquals(3, reduced_buckets.get(2).getDocCount());
    }

    public void testSingleShardReduceDouble() {
        InternalVariableWidthHistogram dummy_histogram = createTestInstance();
        List<InternalVariableWidthHistogram.Bucket> buckets = new ArrayList<>();
        for (double value : new double[]{-1.3, -1.3, 12.0, 13.0, 20.0, 21.5, 23.0, 24.5}) {
            InternalVariableWidthHistogram.Bucket.BucketBounds bounds =  new InternalVariableWidthHistogram.Bucket.BucketBounds(value - 0.7, value + 1);
            InternalVariableWidthHistogram.Bucket bucket = new InternalVariableWidthHistogram.Bucket(
                value, bounds, 1, format, InternalAggregations.EMPTY
            );
            buckets.add(bucket);
        }
        InternalVariableWidthHistogram histogram = dummy_histogram.create(buckets);

        MockBigArrays bigArrays = new MockBigArrays(new MockPageCacheRecycler(Settings.EMPTY), new NoneCircuitBreakerService());
        ScriptService mockScriptService = mockScriptService();

        MultiBucketConsumerService.MultiBucketConsumer bucketConsumer = new MultiBucketConsumerService.MultiBucketConsumer(DEFAULT_MAX_BUCKETS,
            new NoneCircuitBreakerService().getBreaker(CircuitBreaker.REQUEST));
        InternalAggregation.ReduceContext context = InternalAggregation.ReduceContext.forFinalReduction(
            bigArrays, mockScriptService, bucketConsumer, PipelineAggregator.PipelineTree.EMPTY);

        ArrayList<InternalAggregation> aggs = new ArrayList<>();
        aggs.add(histogram);
        List<InternalVariableWidthHistogram.Bucket> reduced_buckets = histogram.reduceBuckets(aggs, context);

        // Final clusters should be [ (-1.3,-1.3), (12.0,13.0), (20.0, 21.5, 23.0, 24.5) ]
        // Final centroids should be [ -1.3, 12.5, 22.25 ]
        // Final keys should be [ -1.3, 11.7, 19.7 ]
        double double_error = 1d / 10000d;
        assertEquals(-1.3, reduced_buckets.get(0).centroid(), double_error);
        assertEquals(-2.0, reduced_buckets.get(0).getKeyForXContent(), double_error);
        assertEquals(2, reduced_buckets.get(0).getDocCount());
        assertEquals(12.5, reduced_buckets.get(1).centroid(), double_error);
        assertEquals(11.3, reduced_buckets.get(1).getKeyForXContent(), double_error);
        assertEquals(2, reduced_buckets.get(1).getDocCount());
        assertEquals(22.25, reduced_buckets.get(2).centroid(), double_error);
        assertEquals(19.3, reduced_buckets.get(2).getKeyForXContent(), double_error);
        assertEquals(4, reduced_buckets.get(2).getDocCount());
    }

    public void testMultipleShardsReduce() {
        InternalVariableWidthHistogram dummy_histogram = createTestInstance();

        List<InternalVariableWidthHistogram.Bucket> buckets1 = new ArrayList<>();
        for (long value : new long[]{1, 5, 6, 10}) {
            InternalVariableWidthHistogram.Bucket.BucketBounds bounds =  new InternalVariableWidthHistogram.Bucket.BucketBounds(value, value + 1);
            InternalVariableWidthHistogram.Bucket bucket = new InternalVariableWidthHistogram.Bucket(
                value, bounds, 1, format, InternalAggregations.EMPTY
            );
            buckets1.add(bucket);
        }

        List<InternalVariableWidthHistogram.Bucket> buckets2 = new ArrayList<>();
        for (long value : new long[]{2, 3, 6, 7}) {
            InternalVariableWidthHistogram.Bucket.BucketBounds bounds =  new InternalVariableWidthHistogram.Bucket.BucketBounds(value, value + 1);
            InternalVariableWidthHistogram.Bucket bucket = new InternalVariableWidthHistogram.Bucket(
                value, bounds, 1, format, InternalAggregations.EMPTY
            );
            buckets2.add(bucket);
        }

        List<InternalVariableWidthHistogram.Bucket> buckets3 = new ArrayList<>();
        for (long value : new long[]{0, 2, 12}) {
            InternalVariableWidthHistogram.Bucket.BucketBounds bounds =  new InternalVariableWidthHistogram.Bucket.BucketBounds(
                value, value + 1
            );
            InternalVariableWidthHistogram.Bucket bucket = new InternalVariableWidthHistogram.Bucket(
                value, bounds, 1, format, InternalAggregations.EMPTY
            );
            buckets3.add(bucket);
        }

        InternalVariableWidthHistogram histogram1 = dummy_histogram.create(buckets1);
        InternalVariableWidthHistogram histogram2 = dummy_histogram.create(buckets2);
        InternalVariableWidthHistogram histogram3 = dummy_histogram.create(buckets3);

        MockBigArrays bigArrays = new MockBigArrays(new MockPageCacheRecycler(Settings.EMPTY), new NoneCircuitBreakerService());
        ScriptService mockScriptService = mockScriptService();

        MultiBucketConsumerService.MultiBucketConsumer bucketConsumer = new MultiBucketConsumerService.MultiBucketConsumer(DEFAULT_MAX_BUCKETS,
            new NoneCircuitBreakerService().getBreaker(CircuitBreaker.REQUEST));
        InternalAggregation.ReduceContext context = InternalAggregation.ReduceContext.forFinalReduction(
            bigArrays, mockScriptService, bucketConsumer, PipelineAggregator.PipelineTree.EMPTY);

        ArrayList<InternalAggregation> aggs = new ArrayList<>();
        aggs.add(histogram1);
        aggs.add(histogram2);
        aggs.add(histogram3);
        List<InternalVariableWidthHistogram.Bucket> reduced_buckets = histogram1.reduceBuckets(aggs, context);

        // Final clusters should be [ (0, 1, 2, 2, 3), (5, 6, 6, 7), (10, 12) ]
        // Final centroids should be [ 2, 6, 11 ]
        // Final keys should be [ 1, 5, 10 ]
        double double_error = 1d / 10000d;
        assertEquals(1.6d, reduced_buckets.get(0).centroid(), double_error);
        assertEquals(0d, reduced_buckets.get(0).getKeyForXContent(), double_error);
        assertEquals(5, reduced_buckets.get(0).getDocCount());
        assertEquals(6d, reduced_buckets.get(1).centroid(), double_error);
        assertEquals(5d, reduced_buckets.get(1).getKeyForXContent(), double_error);
        assertEquals(4, reduced_buckets.get(1).getDocCount());
        assertEquals(11d, reduced_buckets.get(2).centroid(), double_error);
        assertEquals(10d, reduced_buckets.get(2).getKeyForXContent(), double_error);
        assertEquals(2, reduced_buckets.get(2).getDocCount());
    }

    public void testOverlappingReduceResult() {
        InternalVariableWidthHistogram dummy_histogram = createTestInstance();
        List<InternalVariableWidthHistogram.Bucket> buckets = new ArrayList<>();
        for (long value : new long[]{1, 2, 4, 10}) {
            InternalVariableWidthHistogram.Bucket.BucketBounds bounds =  new InternalVariableWidthHistogram.Bucket.BucketBounds(
                value, value + 3
            );
            InternalVariableWidthHistogram.Bucket bucket = new InternalVariableWidthHistogram.Bucket(
                value, bounds, 4, format, InternalAggregations.EMPTY
            );
            buckets.add(bucket);
        }
        InternalVariableWidthHistogram histogram = dummy_histogram.create(buckets);

        MockBigArrays bigArrays = new MockBigArrays(new MockPageCacheRecycler(Settings.EMPTY), new NoneCircuitBreakerService());
        ScriptService mockScriptService = mockScriptService();

        MultiBucketConsumerService.MultiBucketConsumer bucketConsumer = new MultiBucketConsumerService.MultiBucketConsumer(DEFAULT_MAX_BUCKETS,
            new NoneCircuitBreakerService().getBreaker(CircuitBreaker.REQUEST));
        InternalAggregation.ReduceContext context = InternalAggregation.ReduceContext.forFinalReduction(
            bigArrays, mockScriptService, bucketConsumer, PipelineAggregator.PipelineTree.EMPTY);

        ArrayList<InternalAggregation> aggs = new ArrayList<>();
        aggs.add(histogram);
        List<InternalVariableWidthHistogram.Bucket> reduced_buckets = histogram.reduceBuckets(aggs, context);

        // Expected clusters: [ (1, 2), (4), 10) ]
        // Expected centroids: [ 1.5, 4, 10 ]
        // Expected cluster (min, max): [ (1, 5), (4, 7), (10, 13) ]
        // Expected keys: [ 1, 4.5, 10 ]
        // Expected doc counts: [8, 4, 4]
        double double_error = 1d / 10000d;
        assertEquals(1.5, reduced_buckets.get(0).centroid(), double_error);
        assertEquals(1d, reduced_buckets.get(0).getKeyForXContent(), double_error);
        assertEquals(8, reduced_buckets.get(0).getDocCount());
        assertEquals(4d, reduced_buckets.get(1).centroid(), double_error);
        assertEquals(4.5, reduced_buckets.get(1).getKeyForXContent(), double_error);
        assertEquals(4, reduced_buckets.get(1).getDocCount());
        assertEquals(10d, reduced_buckets.get(2).centroid(), double_error);
        assertEquals(10d, reduced_buckets.get(2).getKeyForXContent(), double_error);
        assertEquals(4, reduced_buckets.get(2).getDocCount());
    }

    /**
     * When buckets have the same min after the reduce phase, they should be merged.
     */
    public void testSameMinMerge() {
        InternalVariableWidthHistogram dummy_histogram = createTestInstance();
        List<InternalVariableWidthHistogram.Bucket> buckets = new ArrayList<>();
        for (long value : new long[]{1, 100, 700}) {
            InternalVariableWidthHistogram.Bucket.BucketBounds bounds;
            if(value == 1 || value == 100) {
                bounds = new InternalVariableWidthHistogram.Bucket.BucketBounds(
                    1, value
                );
            } else{
                bounds = new InternalVariableWidthHistogram.Bucket.BucketBounds(
                    value, value + 1
                );
            }
            InternalVariableWidthHistogram.Bucket bucket = new InternalVariableWidthHistogram.Bucket(
                value, bounds, 1, format, InternalAggregations.EMPTY
            );
            buckets.add(bucket);
        }
        InternalVariableWidthHistogram histogram = dummy_histogram.create(buckets);

        MockBigArrays bigArrays = new MockBigArrays(new MockPageCacheRecycler(Settings.EMPTY), new NoneCircuitBreakerService());
        ScriptService mockScriptService = mockScriptService();

        MultiBucketConsumerService.MultiBucketConsumer bucketConsumer = new MultiBucketConsumerService.MultiBucketConsumer(DEFAULT_MAX_BUCKETS,
            new NoneCircuitBreakerService().getBreaker(CircuitBreaker.REQUEST));
        InternalAggregation.ReduceContext context = InternalAggregation.ReduceContext.forFinalReduction(
            bigArrays, mockScriptService, bucketConsumer, PipelineAggregator.PipelineTree.EMPTY);

        ArrayList<InternalAggregation> aggs = new ArrayList<>();
        aggs.add(histogram);
        List<InternalVariableWidthHistogram.Bucket> reduced_buckets = histogram.reduceBuckets(aggs, context);

        // Expected clusters: [ (1), (100), (700) ]
        // Expected clusters after same min merge: [ (1, 100), (700) ]
        // Expected centroids: [ 101/2, 700 ]
        // Expected keys: [ 1, 700 ]
        // Expected doc counts: [2, 1]
        double double_error = 1d / 10000d;
        assertEquals(2, reduced_buckets.size());
        assertEquals((101d/2d), reduced_buckets.get(0).centroid(), double_error);
        assertEquals(1d, reduced_buckets.get(0).getKeyForXContent(), double_error);
        assertEquals(2, reduced_buckets.get(0).getDocCount());
        assertEquals(700d, reduced_buckets.get(1).centroid(), double_error);
        assertEquals(700d, reduced_buckets.get(1).getKeyForXContent(), double_error);
        assertEquals(1, reduced_buckets.get(1).getDocCount());
    }

    @Override
    protected void assertReduced(InternalVariableWidthHistogram reduced, List<InternalVariableWidthHistogram> inputs) {
        testSingleShardReduceLong();
        testSingleShardReduceDouble();
        testMultipleShardsReduce();
        testOverlappingReduceResult();
        testSameMinMerge();
    }
}
