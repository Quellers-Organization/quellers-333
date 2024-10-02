/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.operator;

import org.elasticsearch.compute.aggregation.AggregatorFunctionSupplier;
import org.elasticsearch.compute.aggregation.AggregatorMode;
import org.elasticsearch.compute.aggregation.GroupingAggregator;
import org.elasticsearch.compute.aggregation.GroupingKey;
import org.elasticsearch.compute.aggregation.blockhash.BlockHash;
import org.elasticsearch.compute.aggregation.blockhash.TimeSeriesBlockHash;
import org.elasticsearch.compute.data.ElementType;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides operator factories for time-series aggregations.
 * A time-series aggregation executes in three stages, deviating from the typical two-stage aggregation.
 * For example: {@code sum(rate(write_requests)), avg(cpu) BY cluster, time-bucket}
 *
 * 1. Initial Stage:
 * In this stage, a standard hash aggregation is executed, grouped by tsid and time-bucket.
 * The {@code values} aggregations are added to collect values of the grouping keys excluding the time-bucket,
 * which are then used for final result grouping.
 * {@code rate[INITIAL](write_requests), avg[INITIAL](cpu), values[SINGLE](cluster) BY tsid, time-bucket}
 *
 * 2. Intermediate Stage:
 * Equivalent to the final mode of a standard hash aggregation.
 * This stage merges and reduces the result of the rate aggregations,
 * but merges (without reducing) the results of non-rate aggregations.
 * {@code rate[FINAL](write_requests), avg[INTERMEDIATE](cpu), values[SINGLE](cluster) BY tsid, time-bucket}
 *
 * 3. Final Stage:
 * This extra stage performs outer aggregations over the rate results
 * and combines the intermediate results of non-rate aggregations using the specified user-defined grouping keys.
 * {@code sum[SINGLE](rate_result), avg[FINAL](cpu) BY cluster, bucket}
 */
public final class TimeSeriesAggregationOperatorFactories {

    public record Initial(
        int tsHashChannel,
        int timeBucketChannel,
        List<GroupingKey.Supplier> groupings,
        List<AggregatorFunctionSupplier> rates,
        List<AggregatorFunctionSupplier> nonRates,
        int maxPageSize
    ) implements Operator.OperatorFactory {
        @Override
        public Operator get(DriverContext driverContext) {
            List<GroupingAggregator.Factory> aggregators = new ArrayList<>(groupings.size() + rates.size() + nonRates.size());
            for (AggregatorFunctionSupplier f : rates) {
                aggregators.add(f.groupingAggregatorFactory(AggregatorMode.INITIAL));
            }
            for (AggregatorFunctionSupplier f : nonRates) {
                aggregators.add(f.groupingAggregatorFactory(AggregatorMode.INITIAL));
            }
            List<GroupingKey.Factory> groupings = this.groupings.stream().map(g -> g.get(AggregatorMode.INITIAL)).toList();
            aggregators.addAll(valuesAggregatorForGroupings(groupings, timeBucketChannel));
            return new HashAggregationOperator(
                aggregators,
                groupings,
                () -> new TimeSeriesBlockHash(tsHashChannel, timeBucketChannel, driverContext),
                driverContext
            );
        }

        @Override
        public String describe() {
            return "TimeSeriesInitialAggregationOperatorFactory";
        }
    }

    public record Intermediate(
        int tsHashChannel,
        int timeBucketChannel,
        List<GroupingKey.Supplier> groupings,
        List<AggregatorFunctionSupplier> rates,
        List<AggregatorFunctionSupplier> nonRates,
        int maxPageSize
    ) implements Operator.OperatorFactory {
        @Override
        public Operator get(DriverContext driverContext) {
            List<GroupingAggregator.Factory> aggregators = new ArrayList<>(groupings.size() + rates.size() + nonRates.size());
            for (AggregatorFunctionSupplier f : rates) {
                aggregators.add(f.groupingAggregatorFactory(AggregatorMode.FINAL));
            }
            for (AggregatorFunctionSupplier f : nonRates) {
                aggregators.add(f.groupingAggregatorFactory(AggregatorMode.INTERMEDIATE));
            }
            List<GroupingKey.Factory> groupings = this.groupings.stream().map(g -> g.get(AggregatorMode.INTERMEDIATE)).toList();
            aggregators.addAll(valuesAggregatorForGroupings(groupings, timeBucketChannel));
            List<BlockHash.GroupSpec> hashGroups = List.of(
                new BlockHash.GroupSpec(tsHashChannel, ElementType.BYTES_REF),
                new BlockHash.GroupSpec(timeBucketChannel, ElementType.LONG)
            );
            return new HashAggregationOperator(
                aggregators,
                groupings,
                () -> BlockHash.build(hashGroups, driverContext.blockFactory(), maxPageSize, false),
                driverContext
            );
        }

        @Override
        public String describe() {
            return "TimeSeriesIntermediateAggregationOperatorFactory";
        }
    }

    public record Final(
        List<GroupingKey.Supplier> groupings,
        List<AggregatorFunctionSupplier> outerRates,
        List<AggregatorFunctionSupplier> nonRates,
        int maxPageSize
    ) implements Operator.OperatorFactory {
        @Override
        public Operator get(DriverContext driverContext) {
            List<GroupingAggregator.Factory> aggregators = new ArrayList<>(outerRates.size() + nonRates.size());
            for (AggregatorFunctionSupplier f : outerRates) {
                aggregators.add(f.groupingAggregatorFactory(AggregatorMode.SINGLE));
            }
            for (AggregatorFunctionSupplier f : nonRates) {
                aggregators.add(f.groupingAggregatorFactory(AggregatorMode.FINAL));
            }
            List<GroupingKey.Factory> groupings = this.groupings.stream().map(g -> g.get(AggregatorMode.FINAL)).toList();
            return new HashAggregationOperator(
                aggregators,
                groupings,
                () -> BlockHash.build(GroupingKey.toBlockHashGroupSpec(groupings), driverContext.blockFactory(), maxPageSize, false),
                driverContext
            );
        }

        @Override
        public String describe() {
            return "TimeSeriesFinalAggregationOperatorFactory";
        }
    }

    static List<GroupingAggregator.Factory> valuesAggregatorForGroupings(List<GroupingKey.Factory> groupings, int timeBucketChannel) {
        List<GroupingAggregator.Factory> aggregators = new ArrayList<>();
        for (GroupingKey.Factory g : groupings) {
            GroupingAggregator.Factory factory = g.valuesAggregatorForGroupingsInTimeSeries(timeBucketChannel);
            if (factory != null) {
                aggregators.add(factory);
            }
        }
        return aggregators;
    }
}
