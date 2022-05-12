/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.aggregations;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.common.Rounding;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.index.mapper.DateFieldMapper;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.global.Global;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.CompensatedSum;
import org.elasticsearch.search.aggregations.metrics.InternalNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.Stats;
import org.elasticsearch.search.aggregations.metrics.Sum;
import org.elasticsearch.search.aggregations.pipeline.SimpleValue;
import org.elasticsearch.search.aggregations.timeseries.TimeSeries;
import org.elasticsearch.search.aggregations.timeseries.aggregation.Function;
import org.elasticsearch.search.aggregations.timeseries.aggregation.InternalTimeSeriesAggregation;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.elasticsearch.search.aggregations.AggregationBuilders.dateHistogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.global;
import static org.elasticsearch.search.aggregations.AggregationBuilders.stats;
import static org.elasticsearch.search.aggregations.AggregationBuilders.sum;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.timeSeries;
import static org.elasticsearch.search.aggregations.AggregationBuilders.timeSeriesAggregation;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

@ESIntegTestCase.SuiteScopeTestCase
public class TimeSeriesAggregationsIT extends ESIntegTestCase {

    private static final Map<Map<String, String>, Map<Long, Map<String, Double>>> data = new HashMap<>();
    private static int numberOfDimensions;
    private static int numberOfMetrics;
    private static String[][] dimensions;
    private static Long[] boundaries;

    @Override
    public void setupSuiteScopeCluster() throws Exception {
        int numberOfIndices = randomIntBetween(1, 3);
        numberOfDimensions = randomIntBetween(1, 5);
        numberOfMetrics = randomIntBetween(1, 10);
        String[] routingKeys = randomSubsetOf(
            randomIntBetween(1, numberOfDimensions),
            IntStream.rangeClosed(0, numberOfDimensions - 1).boxed().toArray(Integer[]::new)
        ).stream().map(k -> "dim_" + k).toArray(String[]::new);
        dimensions = new String[numberOfDimensions][];
        int dimCardinality = 1;
        for (int i = 0; i < dimensions.length; i++) {
            dimensions[i] = randomUnique(() -> randomAlphaOfLength(10), randomIntBetween(1, 20 / numberOfDimensions)).toArray(
                new String[0]
            );
            dimCardinality *= dimensions[i].length;
        }

        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        builder.startObject("properties");
        for (int i = 0; i < dimensions.length; i++) {
            builder.startObject("dim_" + i);
            builder.field("type", "keyword");
            builder.field("time_series_dimension", true);
            builder.endObject();
        }
        for (int i = 0; i < numberOfMetrics; i++) {
            builder.startObject("metric_" + i);
            builder.field("type", "double");
            builder.endObject();
        }
        builder.endObject(); // properties
        builder.endObject();
        String start = "2021-01-01T00:00:00Z";
        String end = "2022-01-01T00:00:00Z";
        long startMillis = DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER.parseMillis(start);
        long endMillis = DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER.parseMillis(end);
        Set<Long> possibleBoundaries = randomUnique(() -> randomLongBetween(startMillis + 1, endMillis - 1), numberOfIndices - 1);
        possibleBoundaries.add(startMillis);
        possibleBoundaries.add(endMillis);
        boundaries = possibleBoundaries.stream().sorted().toArray(Long[]::new);
        for (int i = 0; i < numberOfIndices; i++) {
            assertAcked(
                prepareCreate("index" + i).setSettings(
                    Settings.builder()
                        .put("mode", "time_series")
                        .put("routing_path", String.join(",", routingKeys))
                        .put("index.number_of_shards", randomIntBetween(1, 10))
                        .put("time_series.start_time", boundaries[i])
                        .put("time_series.end_time", boundaries[i + 1])
                        .build()
                ).setMapping(builder).addAlias(new Alias("index")).get()
            );
        }

        int numberOfDocs = randomIntBetween(dimCardinality, dimCardinality * 5);
        logger.info(
            "Dimensions: "
                + numberOfDimensions
                + " metrics: "
                + numberOfMetrics
                + " documents "
                + numberOfDocs
                + " cardinality "
                + dimCardinality
        );

        List<IndexRequestBuilder> docs = new ArrayList<>(numberOfDocs);
        for (int i = 0; i < numberOfDocs; i++) {
            XContentBuilder docSource = XContentFactory.jsonBuilder();
            docSource.startObject();
            Map<String, String> key = new HashMap<>();
            for (int d = 0; d < numberOfDimensions; d++) {
                String dim = randomFrom(dimensions[d]);
                docSource.field("dim_" + d, dim);
                key.put("dim_" + d, dim);
            }
            Map<String, Double> metrics = new HashMap<>();
            for (int m = 0; m < numberOfMetrics; m++) {
                Double val = randomDoubleBetween(0.0, 10000.0, true);
                docSource.field("metric_" + m, val);
                metrics.put("metric_" + m, val);
            }
            Map<Long, Map<String, Double>> tsValues = data.get(key);
            long timestamp;
            if (tsValues == null) {
                timestamp = randomLongBetween(startMillis, endMillis - 1);
                tsValues = new HashMap<>();
                data.put(key, tsValues);
            } else {
                timestamp = randomValueOtherThanMany(tsValues::containsKey, () -> randomLongBetween(startMillis, endMillis - 1));
            }
            tsValues.put(timestamp, metrics);
            docSource.field("@timestamp", timestamp);
            docSource.endObject();
            docs.add(client().prepareIndex("index" + findIndex(timestamp)).setOpType(DocWriteRequest.OpType.CREATE).setSource(docSource));
        }
        indexRandom(true, false, docs);
    }

    public void testStandAloneTimeSeriesAgg() {
        SearchResponse response = client().prepareSearch("index").setSize(0).addAggregation(timeSeries("by_ts")).get();
        assertSearchResponse(response);
        Aggregations aggregations = response.getAggregations();
        assertNotNull(aggregations);
        TimeSeries timeSeries = aggregations.get("by_ts");
        assertThat(
            timeSeries.getBuckets().stream().map(MultiBucketsAggregation.Bucket::getKey).collect(Collectors.toSet()),
            equalTo(data.keySet())
        );
        for (TimeSeries.Bucket bucket : timeSeries.getBuckets()) {
            @SuppressWarnings("unchecked")
            Map<String, String> key = (Map<String, String>) bucket.getKey();
            assertThat((long) data.get(key).size(), equalTo(bucket.getDocCount()));
        }
    }

    public void testTimeSeriesGroupedByADimension() {
        String groupBy = "dim_" + randomIntBetween(0, numberOfDimensions - 1);
        SearchResponse response = client().prepareSearch("index")
            .setSize(0)
            .addAggregation(
                terms("by_dim").field(groupBy)
                    .size(data.size())
                    .collectMode(randomFrom(Aggregator.SubAggCollectionMode.values()))
                    .subAggregation(timeSeries("by_ts"))
            )
            .get();
        assertSearchResponse(response);
        Aggregations aggregations = response.getAggregations();
        assertNotNull(aggregations);
        Terms terms = aggregations.get("by_dim");
        Set<Map<String, String>> keys = new HashSet<>();
        for (Terms.Bucket term : terms.getBuckets()) {
            TimeSeries timeSeries = term.getAggregations().get("by_ts");
            for (TimeSeries.Bucket bucket : timeSeries.getBuckets()) {
                @SuppressWarnings("unchecked")
                Map<String, String> key = (Map<String, String>) bucket.getKey();
                assertThat((long) data.get(key).size(), equalTo(bucket.getDocCount()));
                assertTrue("key is not unique", keys.add(key));
                assertThat("time series doesn't contain dimensions we grouped by", key.get(groupBy), equalTo(term.getKeyAsString()));
            }
        }
        assertThat(keys, equalTo(data.keySet()));
    }

    public void testTimeSeriesGroupedByDateHistogram() {
        DateHistogramInterval fixedInterval = DateHistogramInterval.days(randomIntBetween(10, 100));
        SearchResponse response = client().prepareSearch("index")
            .setSize(0)
            .addAggregation(
                dateHistogram("by_time").field("@timestamp")
                    .fixedInterval(fixedInterval)
                    .subAggregation(timeSeries("by_ts").subAggregation(stats("timestamp").field("@timestamp")))
            )
            .get();
        assertSearchResponse(response);
        Aggregations aggregations = response.getAggregations();
        assertNotNull(aggregations);
        Histogram histogram = aggregations.get("by_time");
        Map<Map<String, String>, Long> keys = new HashMap<>();
        for (Histogram.Bucket interval : histogram.getBuckets()) {
            long intervalStart = ((ZonedDateTime) interval.getKey()).toEpochSecond() * 1000;
            long intervalEnd = intervalStart + fixedInterval.estimateMillis();
            TimeSeries timeSeries = interval.getAggregations().get("by_ts");
            for (TimeSeries.Bucket bucket : timeSeries.getBuckets()) {
                @SuppressWarnings("unchecked")
                Map<String, String> key = (Map<String, String>) bucket.getKey();
                keys.compute(key, (k, v) -> (v == null ? 0 : v) + bucket.getDocCount());
                assertThat(bucket.getDocCount(), lessThanOrEqualTo((long) data.get(key).size()));
                Stats stats = bucket.getAggregations().get("timestamp");
                long minTimestamp = DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER.parseMillis(stats.getMinAsString());
                long maxTimestamp = DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER.parseMillis(stats.getMaxAsString());
                assertThat(minTimestamp, greaterThanOrEqualTo(intervalStart));
                assertThat(maxTimestamp, lessThan(intervalEnd));
            }
        }
        assertThat(keys.keySet(), equalTo(data.keySet()));
        for (Map.Entry<Map<String, String>, Long> entry : keys.entrySet()) {
            assertThat(entry.getValue(), equalTo((long) data.get(entry.getKey()).size()));
        }
    }

    public void testStandAloneTimeSeriesAggWithDimFilter() {
        boolean include = randomBoolean();
        int dim = randomIntBetween(0, numberOfDimensions - 1);
        String val = randomFrom(dimensions[dim]);
        QueryBuilder queryBuilder = QueryBuilders.termQuery("dim_" + dim, val);
        if (include == false) {
            queryBuilder = QueryBuilders.boolQuery().mustNot(queryBuilder);
        }
        SearchResponse response = client().prepareSearch("index")
            .setQuery(queryBuilder)
            .setSize(0)
            .addAggregation(timeSeries("by_ts"))
            .get();
        assertSearchResponse(response);
        Aggregations aggregations = response.getAggregations();
        assertNotNull(aggregations);
        TimeSeries timeSeries = aggregations.get("by_ts");
        Map<Map<String, String>, Map<Long, Map<String, Double>>> filteredData = dataFilteredByDimension("dim_" + dim, val, include);
        assertThat(
            timeSeries.getBuckets().stream().map(MultiBucketsAggregation.Bucket::getKey).collect(Collectors.toSet()),
            equalTo(filteredData.keySet())
        );
        for (TimeSeries.Bucket bucket : timeSeries.getBuckets()) {
            @SuppressWarnings("unchecked")
            Map<String, String> key = (Map<String, String>) bucket.getKey();
            assertThat(bucket.getDocCount(), equalTo((long) filteredData.get(key).size()));
        }
    }

    public void testStandAloneTimeSeriesAggWithGlobalAggregation() {
        boolean include = randomBoolean();
        int dim = randomIntBetween(0, numberOfDimensions - 1);
        int metric = randomIntBetween(0, numberOfMetrics - 1);
        String val = randomFrom(dimensions[dim]);
        QueryBuilder queryBuilder = QueryBuilders.termQuery("dim_" + dim, val);
        if (include == false) {
            queryBuilder = QueryBuilders.boolQuery().mustNot(queryBuilder);
        }
        SearchResponse response = client().prepareSearch("index")
            .setQuery(queryBuilder)
            .setSize(0)
            .addAggregation(timeSeries("by_ts").subAggregation(sum("filter_sum").field("metric_" + metric)))
            .addAggregation(global("everything").subAggregation(sum("all_sum").field("metric_" + metric)))
            .addAggregation(PipelineAggregatorBuilders.sumBucket("total_filter_sum", "by_ts>filter_sum"))
            .get();
        assertSearchResponse(response);
        Aggregations aggregations = response.getAggregations();
        assertNotNull(aggregations);
        TimeSeries timeSeries = aggregations.get("by_ts");
        Map<Map<String, String>, Map<Long, Map<String, Double>>> filteredData = dataFilteredByDimension("dim_" + dim, val, include);
        assertThat(
            timeSeries.getBuckets().stream().map(MultiBucketsAggregation.Bucket::getKey).collect(Collectors.toSet()),
            equalTo(filteredData.keySet())
        );
        for (TimeSeries.Bucket bucket : timeSeries.getBuckets()) {
            @SuppressWarnings("unchecked")
            Map<String, String> key = (Map<String, String>) bucket.getKey();
            assertThat(bucket.getDocCount(), equalTo((long) filteredData.get(key).size()));
        }
        SimpleValue obj = aggregations.get("total_filter_sum");
        assertThat(obj.value(), closeTo(sumByMetric(filteredData, "metric_" + metric), obj.value() * 0.0001));

        Global global = aggregations.get("everything");
        Sum allSum = global.getAggregations().get("all_sum");
        assertThat(allSum.value(), closeTo(sumByMetric(data, "metric_" + metric), allSum.value() * 0.0001));

        ElasticsearchException e = expectThrows(
            ElasticsearchException.class,
            () -> client().prepareSearch("index")
                .setQuery(QueryBuilders.termQuery("dim_" + dim, val))
                .setSize(0)
                .addAggregation(global("everything").subAggregation(timeSeries("by_ts")))
                .get()
        );
        assertThat(e.getRootCause().getMessage(), containsString("Time series aggregations cannot be used inside global aggregation."));
    }

    public void testStandAloneTimeSeriesAggWithMetricFilter() {
        boolean above = randomBoolean();
        int metric = randomIntBetween(0, numberOfMetrics - 1);
        double val = randomDoubleBetween(0, 100000, true);
        RangeQueryBuilder queryBuilder = QueryBuilders.rangeQuery("metric_" + metric);
        if (above) {
            queryBuilder.gt(val);
        } else {
            queryBuilder.lte(val);
        }
        SearchResponse response = client().prepareSearch("index")
            .setQuery(queryBuilder)
            .setSize(0)
            .addAggregation(timeSeries("by_ts"))
            .get();
        assertSearchResponse(response);
        Aggregations aggregations = response.getAggregations();
        assertNotNull(aggregations);
        TimeSeries timeSeries = aggregations.get("by_ts");
        Map<Map<String, String>, Map<Long, Map<String, Double>>> filteredData = dataFilteredByMetric(data, "metric_" + metric, val, above);
        assertThat(
            timeSeries.getBuckets().stream().map(MultiBucketsAggregation.Bucket::getKey).collect(Collectors.toSet()),
            equalTo(filteredData.keySet())
        );
        for (TimeSeries.Bucket bucket : timeSeries.getBuckets()) {
            @SuppressWarnings("unchecked")
            Map<String, String> key = (Map<String, String>) bucket.getKey();
            assertThat(bucket.getDocCount(), equalTo((long) filteredData.get(key).size()));
        }
    }

    public void testRetrievingHits() {
        Map.Entry<String, Double> filterMetric = randomMetricAndValue(data);
        double lowerVal = filterMetric.getValue() - randomDoubleBetween(0, 100000, true);
        double upperVal = filterMetric.getValue() + randomDoubleBetween(0, 100000, true);
        Map<Map<String, String>, Map<Long, Map<String, Double>>> filteredData = dataFilteredByMetric(
            dataFilteredByMetric(data, filterMetric.getKey(), upperVal, false),
            filterMetric.getKey(),
            lowerVal,
            true
        );
        QueryBuilder queryBuilder = QueryBuilders.rangeQuery(filterMetric.getKey()).gt(lowerVal).lte(upperVal);
        int expectedSize = count(filteredData);
        ElasticsearchException e = expectThrows(
            ElasticsearchException.class,
            () -> client().prepareSearch("index")
                .setQuery(queryBuilder)
                .setSize(expectedSize * 2)
                .addAggregation(timeSeries("by_ts").subAggregation(topHits("hits").size(100)))
                .addAggregation(topHits("top_hits").size(100)) // top level top hits
                .get()
        );
        assertThat(e.getDetailedMessage(), containsString("Top hits aggregations cannot be used together with time series aggregations"));
        // TODO: Fix the top hits aggregation
    }

    /**
     * Filters the test data by only including or excluding certain results
     * @param dimension name of the dimension to be filtered
     * @param value name of the dimension to be filtered
     * @param include true if all records with this dimension should be included, false otherwise
     * @return filtered map
     */
    private static Map<Map<String, String>, Map<Long, Map<String, Double>>> dataFilteredByDimension(
        String dimension,
        String value,
        boolean include
    ) {
        Map<Map<String, String>, Map<Long, Map<String, Double>>> newMap = new HashMap<>();
        for (Map.Entry<Map<String, String>, Map<Long, Map<String, Double>>> entry : data.entrySet()) {
            if (value.equals(entry.getKey().get(dimension)) == include) {
                newMap.put(entry.getKey(), entry.getValue());
            }
        }
        return newMap;
    }

    /**
     * Filters the test data by only including or excluding certain results
     * @param data data to be filtered
     * @param metric name of the metric the records should be filtered by
     * @param value value of the metric
     * @param above true if all records above the value should be included, false otherwise
     * @return filtered map
     */
    private static Map<Map<String, String>, Map<Long, Map<String, Double>>> dataFilteredByMetric(
        Map<Map<String, String>, Map<Long, Map<String, Double>>> data,
        String metric,
        double value,
        boolean above
    ) {
        Map<Map<String, String>, Map<Long, Map<String, Double>>> newMap = new HashMap<>();
        for (Map.Entry<Map<String, String>, Map<Long, Map<String, Double>>> entry : data.entrySet()) {
            Map<Long, Map<String, Double>> values = new HashMap<>();
            for (Map.Entry<Long, Map<String, Double>> doc : entry.getValue().entrySet()) {
                Double docVal = doc.getValue().get(metric);
                if (docVal != null && (docVal > value == above)) {
                    values.put(doc.getKey(), doc.getValue());
                }
            }
            if (values.isEmpty() == false) {
                newMap.put(entry.getKey(), values);
            }
        }
        return newMap;
    }

    private static Double sumByMetric(Map<Map<String, String>, Map<Long, Map<String, Double>>> data, String metric) {
        final CompensatedSum kahanSummation = new CompensatedSum(0, 0);
        for (Map.Entry<Map<String, String>, Map<Long, Map<String, Double>>> entry : data.entrySet()) {
            for (Map.Entry<Long, Map<String, Double>> doc : entry.getValue().entrySet()) {
                Double docVal = doc.getValue().get(metric);
                if (docVal != null) {
                    kahanSummation.add(docVal);
                }
            }
        }
        return kahanSummation.value();
    }

    private static int count(Map<Map<String, String>, Map<Long, Map<String, Double>>> data) {
        int size = 0;
        for (Map.Entry<Map<String, String>, Map<Long, Map<String, Double>>> entry : data.entrySet()) {
            size += entry.getValue().entrySet().size();
        }
        return size;
    }

    private static int findIndex(long timestamp) {
        for (int i = 0; i < boundaries.length - 1; i++) {
            if (timestamp < boundaries[i + 1]) {
                return i;
            }
        }
        throw new IllegalArgumentException("Cannot find index for timestamp " + timestamp);
    }

    private static Map.Entry<String, Double> randomMetricAndValue(Map<Map<String, String>, Map<Long, Map<String, Double>>> data) {
        return randomFrom(
            randomFrom(randomFrom(data.entrySet().stream().toList()).getValue().entrySet().stream().toList()).getValue()
                .entrySet()
                .stream()
                .toList()
        );
    }

    public void testGetHitsFailure() throws Exception {
        assertAcked(
            prepareCreate("test").setSettings(
                Settings.builder()
                    .put("mode", "time_series")
                    .put("routing_path", "key")
                    .put("time_series.start_time", "2021-01-01T00:00:00Z")
                    .put("time_series.end_time", "2022-01-01T00:00:00Z")
                    .put("number_of_shards", 1)
                    .build()
            ).setMapping("key", "type=keyword,time_series_dimension=true", "val", "type=double").get()
        );

        client().prepareBulk()
            .add(client().prepareIndex("test").setId("2").setSource("key", "bar", "val", 2, "@timestamp", "2021-01-01T00:00:10Z"))
            .add(client().prepareIndex("test").setId("1").setSource("key", "bar", "val", 10, "@timestamp", "2021-01-01T00:00:00Z"))
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            .get();
        client().prepareBulk()
            .add(client().prepareIndex("test").setId("4").setSource("key", "bar", "val", 50, "@timestamp", "2021-01-01T00:00:30Z"))
            .add(client().prepareIndex("test").setId("3").setSource("key", "bar", "val", 40, "@timestamp", "2021-01-01T00:00:20Z"))
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            .get();
        client().prepareBulk()
            .add(client().prepareIndex("test").setId("7").setSource("key", "foo", "val", 20, "@timestamp", "2021-01-01T00:00:00Z"))
            .add(client().prepareIndex("test").setId("8").setSource("key", "foo", "val", 30, "@timestamp", "2021-01-01T00:10:00Z"))
            .add(client().prepareIndex("test").setId("5").setSource("key", "baz", "val", 20, "@timestamp", "2021-01-01T00:00:00Z"))
            .add(client().prepareIndex("test").setId("6").setSource("key", "baz", "val", 30, "@timestamp", "2021-01-01T00:10:00Z"))
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            .get();

        QueryBuilder queryBuilder = QueryBuilders.rangeQuery("@timestamp").lte("2021-01-01T00:10:00Z");
        SearchResponse response = client().prepareSearch("test")
            .setQuery(queryBuilder)
            .setSize(10)
            .addSort("key", SortOrder.ASC)
            .addSort("@timestamp", SortOrder.ASC)
            .get();
        assertSearchResponse(response);

        response = client().prepareSearch("test").setQuery(queryBuilder).setSize(10).addAggregation(timeSeries("by_ts")).get();
        assertSearchResponse(response);

    }

    public void testBasicTimeSeriesAggregations() {
        DateHistogramInterval fixedInterval = DateHistogramInterval.days(randomIntBetween(10, 100));
        Rounding.Prepared rounding = Rounding.builder(new TimeValue(fixedInterval.estimateMillis())).build().prepareForUnknown();
        SearchResponse response = client().prepareSearch("index")
            .setSize(0)
            .addAggregation(
                timeSeriesAggregation("by_ts").field("metric_0")
                    .interval(fixedInterval)
                    .size(data.size())
            )
            .get();
        Aggregations aggregations = response.getAggregations();
        assertNotNull(aggregations);
        InternalTimeSeriesAggregation timeSeries = aggregations.get("by_ts");
        assertThat(
            timeSeries.getBuckets().stream().map(MultiBucketsAggregation.Bucket::getKey).collect(Collectors.toSet()),
            equalTo(data.keySet())
        );
        for (InternalTimeSeriesAggregation.InternalBucket bucket : timeSeries.getBuckets()) {
            Map<String, Object> key = bucket.getKey();
            Map<Long, Map<String, Double>> dataValues = data.get(key);
            assertThat((long) dataValues.size(), equalTo(bucket.getDocCount()));
            Map<Long, Tuple<Double, Long>> dataBucketValues = new HashMap<>();
            dataValues.forEach((timestamp, metrics) -> {
                long roundValue = rounding.nextRoundingValue(timestamp);
                if (dataBucketValues.containsKey(roundValue)) {
                    if (timestamp > dataBucketValues.get(roundValue).v2()) {
                        dataBucketValues.put(roundValue, new Tuple<>(metrics.get("metric_0"), timestamp));
                    }
                } else {
                    dataBucketValues.put(roundValue, new Tuple<>(metrics.get("metric_0"), timestamp));
                }
            });

            dataBucketValues.forEach((timestamp, value) -> {
                assertTrue(bucket.getTimeBucketValues().containsKey(timestamp));
                InternalAggregation aggregation = bucket.getTimeBucketValues().get(timestamp);
                assertTrue(aggregation instanceof InternalNumericMetricsAggregation.SingleValue);
                assertThat(((InternalNumericMetricsAggregation.SingleValue) aggregation).value(), closeTo(value.v1(), 0.0001d));
            });
        }
    }

    public void testTimeSeriesAggregationsDownsample() {
        DateHistogramInterval fixedInterval = DateHistogramInterval.days(randomIntBetween(10, 100));
        Rounding.Prepared rounding = Rounding.builder(new TimeValue(fixedInterval.estimateMillis())).build().prepareForUnknown();
        SearchResponse response = client().prepareSearch("index")
            .setSize(0)
            .addAggregation(
                timeSeriesAggregation("by_ts").field("metric_0")
                    .interval(fixedInterval)
                    .downsample(fixedInterval, Function.sum)
                    .size(data.size())
            )
            .get();
        Aggregations aggregations = response.getAggregations();
        assertNotNull(aggregations);
        InternalTimeSeriesAggregation timeSeries = aggregations.get("by_ts");
        assertThat(
            timeSeries.getBuckets().stream().map(MultiBucketsAggregation.Bucket::getKey).collect(Collectors.toSet()),
            equalTo(data.keySet())
        );
        for (InternalTimeSeriesAggregation.InternalBucket bucket : timeSeries.getBuckets()) {
            Map<String, Object> key = bucket.getKey();
            Map<Long, Map<String, Double>> dataValues = data.get(key);
            assertThat((long) dataValues.size(), equalTo(bucket.getDocCount()));
            Map<Long, Double> dataBucketValues = new HashMap<>();
            dataValues.forEach((timestamp, metrics) -> {
                long roundValue = rounding.nextRoundingValue(timestamp);
                if (dataBucketValues.containsKey(roundValue)) {
                    dataBucketValues.put(roundValue, dataBucketValues.get(roundValue) + metrics.get("metric_0"));
                } else {
                    dataBucketValues.put(roundValue, metrics.get("metric_0"));
                }
            });

            dataBucketValues.forEach((timestamp, value) -> {
                assertTrue(bucket.getTimeBucketValues().containsKey(timestamp));
                InternalAggregation aggregation = bucket.getTimeBucketValues().get(timestamp);
                assertTrue(aggregation instanceof InternalNumericMetricsAggregation.SingleValue);
                assertThat(((InternalNumericMetricsAggregation.SingleValue) aggregation).value(), closeTo(value, 0.0001d));
            });
        }
    }

    public void testTimeSeriesAggregationsAggregator() {
        DateHistogramInterval fixedInterval = DateHistogramInterval.days(randomIntBetween(10, 100));
        Rounding.Prepared rounding = Rounding.builder(new TimeValue(fixedInterval.estimateMillis())).build().prepareForUnknown();
        SearchResponse response = client().prepareSearch("index")
            .setSize(0)
            .addAggregation(
                timeSeriesAggregation("by_ts").field("metric_0")
                    .interval(fixedInterval)
                    .aggregator("sum")
                    .size(data.size())
            )
            .get();
        Aggregations aggregations = response.getAggregations();
        assertNotNull(aggregations);
        InternalTimeSeriesAggregation timeSeries = aggregations.get("by_ts");
        assertThat(timeSeries.getBuckets().size(), equalTo(1));
        assertThat(
            timeSeries.getBuckets().stream().map(MultiBucketsAggregation.Bucket::getKey).collect(Collectors.toSet()),
            equalTo(Set.of(Map.of()))
        );

        Map<Long, Double> aggResults = new HashMap<>();
        data.forEach((key, value) -> {
            Map<Long, Tuple<Double, Long>> downsampleResult = new HashMap<>();
            value.forEach((timestamp, metrics) -> {
                long roundValue = rounding.nextRoundingValue(timestamp);
                if (downsampleResult.containsKey(roundValue)) {
                    if (timestamp > downsampleResult.get(roundValue).v2()) {
                        downsampleResult.put(roundValue, new Tuple<>(metrics.get("metric_0"), timestamp));
                    }
                } else {
                    downsampleResult.put(roundValue, new Tuple<>(metrics.get("metric_0"), timestamp));
                }
            });

            for (Entry<Long, Tuple<Double, Long>> entry : downsampleResult.entrySet()) {
                Long timestamp = entry.getKey();
                Double downsampleValue = entry.getValue().v1();
                if (aggResults.containsKey(timestamp)) {
                    aggResults.put(timestamp, aggResults.get(timestamp) + downsampleValue);
                } else {
                    aggResults.put(timestamp, downsampleValue);
                }
            }
        });

        for (InternalTimeSeriesAggregation.InternalBucket bucket : timeSeries.getBuckets()) {
            aggResults.forEach((timestamp, metric) -> {
                assertTrue(bucket.getTimeBucketValues().containsKey(timestamp));
                InternalAggregation aggregation = bucket.getTimeBucketValues().get(timestamp);
                assertTrue(aggregation instanceof InternalNumericMetricsAggregation.SingleValue);
                assertThat(((InternalNumericMetricsAggregation.SingleValue) aggregation).value(), closeTo(metric, 0.0001d));
            });
        }
    }

    public void testTimeSeriesAggregationsGroupBy() {
        DateHistogramInterval fixedInterval = DateHistogramInterval.days(randomIntBetween(10, 100));
        Rounding.Prepared rounding = Rounding.builder(new TimeValue(fixedInterval.estimateMillis())).build().prepareForUnknown();
        SearchResponse response = client().prepareSearch("index")
            .setSize(0)
            .addAggregation(
                timeSeriesAggregation("by_ts").field("metric_0")
                    .group(List.of("dim_0"))
                    .interval(fixedInterval)
                    .downsample(fixedInterval, Function.max)
                    .aggregator("sum")
                    .size(data.size())
            )
            .get();
        Aggregations aggregations = response.getAggregations();
        assertNotNull(aggregations);
        InternalTimeSeriesAggregation timeSeries = aggregations.get("by_ts");
        assertThat(
            timeSeries.getBuckets().stream().map(MultiBucketsAggregation.Bucket::getKey).collect(Collectors.toSet()),
            equalTo(
                data.keySet()
                    .stream()
                    .map(key -> key.get("dim_0"))
                    .collect(Collectors.toSet())
                    .stream()
                    .map(key -> Map.of("dim_0", key))
                    .collect(Collectors.toSet())
            )
        );

        Map<Map<String, String>, Map<Long, Double>> aggResults = new HashMap<>();
        data.forEach((key, value) -> {
            String dim = key.get("dim_0");
            Map<String, String> bucketKey = Map.of("dim_0", dim);
            Map<Long, Double> bucketResult = aggResults.get(bucketKey);
            if (bucketResult == null) {
                bucketResult = new HashMap<>();
                aggResults.put(bucketKey, bucketResult);
            }

            Map<Long, Double> downsampleResult = new HashMap<>();
            value.forEach((timestamp, metrics) -> {
                long roundValue = rounding.nextRoundingValue(timestamp);
                if (downsampleResult.containsKey(roundValue)) {
                    downsampleResult.put(roundValue, Math.max(downsampleResult.get(roundValue), metrics.get("metric_0")));
                } else {
                    downsampleResult.put(roundValue, metrics.get("metric_0"));
                }
            });

            for (Entry<Long, Double> entry : downsampleResult.entrySet()) {
                Long timestamp = entry.getKey();
                Double downsampleValue = entry.getValue();
                if (bucketResult.containsKey(timestamp)) {
                    bucketResult.put(timestamp, bucketResult.get(timestamp) + downsampleValue);
                } else {
                    bucketResult.put(timestamp, downsampleValue);
                }
            }
        });

        for (InternalTimeSeriesAggregation.InternalBucket bucket : timeSeries.getBuckets()) {
            Map<String, Object> key = bucket.getKey();
            Map<Long, Double> dataValues = aggResults.get(key);
            dataValues.forEach((timestamp, metric) -> {
                assertTrue(bucket.getTimeBucketValues().containsKey(timestamp));
                InternalAggregation aggregation = bucket.getTimeBucketValues().get(timestamp);
                assertTrue(aggregation instanceof InternalNumericMetricsAggregation.SingleValue);
                assertThat(((InternalNumericMetricsAggregation.SingleValue) aggregation).value(), closeTo(metric, 0.0001d));
            });
        }
    }
}
