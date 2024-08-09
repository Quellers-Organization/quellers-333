/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.multivalue;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;
import org.elasticsearch.xpack.esql.core.util.StringUtils;
import org.elasticsearch.xpack.esql.expression.function.AbstractScalarFunctionTestCase;
import org.elasticsearch.xpack.esql.expression.function.MultivalueTestCaseSupplier;
import org.elasticsearch.xpack.esql.expression.function.TestCaseSupplier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.elasticsearch.xpack.esql.core.type.DataType.DOUBLE;
import static org.elasticsearch.xpack.esql.core.type.DataType.INTEGER;
import static org.elasticsearch.xpack.esql.core.type.DataType.LONG;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;

public class MvPercentileTests extends AbstractScalarFunctionTestCase {
    public MvPercentileTests(@Name("TestCase") Supplier<TestCaseSupplier.TestCase> testCaseSupplier) {
        this.testCase = testCaseSupplier.get();
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() {
        List<TestCaseSupplier> cases = new ArrayList<>();

        var fieldSuppliers = Stream.of(
            MultivalueTestCaseSupplier.intCases(Integer.MIN_VALUE, Integer.MAX_VALUE, true),
            MultivalueTestCaseSupplier.longCases(Long.MIN_VALUE, Long.MAX_VALUE, true),
            MultivalueTestCaseSupplier.doubleCases(-Double.MAX_VALUE, Double.MAX_VALUE, true)
        ).flatMap(List::stream).toList();

        var percentileSuppliers = Stream.of(
            TestCaseSupplier.intCases(0, 100, true),
            TestCaseSupplier.longCases(0, 100, true),
            TestCaseSupplier.doubleCases(0, 100, true)
        ).flatMap(List::stream).toList();

        for (var fieldSupplier : fieldSuppliers) {
            for (var percentileSupplier : percentileSuppliers) {
                cases.add(makeSupplier(fieldSupplier, percentileSupplier));
            }
        }

        for (var percentileType : List.of(INTEGER, LONG, DataType.DOUBLE)) {
            cases.addAll(
                List.of(
                    // Doubles
                    new TestCaseSupplier(
                        "median double",
                        List.of(DOUBLE, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10., 5., 10.), DOUBLE, "field"),
                                percentileWithType(50, percentileType)
                            ),
                            evaluatorString(DOUBLE, percentileType),
                            DOUBLE,
                            equalTo(5.)
                        )
                    ),
                    new TestCaseSupplier(
                        "single value double",
                        List.of(DOUBLE, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(55.), DOUBLE, "field"),
                                percentileWithType(randomIntBetween(0, 100), percentileType)
                            ),
                            evaluatorString(DOUBLE, percentileType),
                            DOUBLE,
                            equalTo(55.)
                        )
                    ),
                    new TestCaseSupplier(
                        "p0 double",
                        List.of(DOUBLE, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10., 5., 10.), DOUBLE, "field"),
                                percentileWithType(0, percentileType)
                            ),
                            evaluatorString(DOUBLE, percentileType),
                            DOUBLE,
                            equalTo(-10.)
                        )
                    ),
                    new TestCaseSupplier(
                        "p100 double",
                        List.of(DOUBLE, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10., 5., 10.), DOUBLE, "field"),
                                percentileWithType(100, percentileType)
                            ),
                            evaluatorString(DOUBLE, percentileType),
                            DOUBLE,
                            equalTo(10.)
                        )
                    ),
                    new TestCaseSupplier(
                        "averaged double",
                        List.of(DOUBLE, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10., 5., 10.), DOUBLE, "field"),
                                percentileWithType(75, percentileType)
                            ),
                            evaluatorString(DOUBLE, percentileType),
                            DOUBLE,
                            equalTo(7.5)
                        )
                    ),
                    new TestCaseSupplier(
                        "big double difference",
                        List.of(DOUBLE, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-Double.MAX_VALUE, Double.MAX_VALUE), DOUBLE, "field"),
                                percentileWithType(50, percentileType)
                            ),
                            evaluatorString(DOUBLE, percentileType),
                            DOUBLE,
                            closeTo(0, 0.0000001)
                        )
                    ),

                    // Int
                    new TestCaseSupplier(
                        "median int",
                        List.of(INTEGER, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10, 5, 10), INTEGER, "field"),
                                percentileWithType(50, percentileType)
                            ),
                            evaluatorString(INTEGER, percentileType),
                            INTEGER,
                            equalTo(5)
                        )
                    ),
                    new TestCaseSupplier(
                        "single value int",
                        List.of(INTEGER, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(55), INTEGER, "field"),
                                percentileWithType(randomIntBetween(0, 100), percentileType)
                            ),
                            evaluatorString(INTEGER, percentileType),
                            INTEGER,
                            equalTo(55)
                        )
                    ),
                    new TestCaseSupplier(
                        "p0 int",
                        List.of(INTEGER, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10, 5, 10), INTEGER, "field"),
                                percentileWithType(0, percentileType)
                            ),
                            evaluatorString(INTEGER, percentileType),
                            INTEGER,
                            equalTo(-10)
                        )
                    ),
                    new TestCaseSupplier(
                        "p100 int",
                        List.of(INTEGER, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10, 5, 10), INTEGER, "field"),
                                percentileWithType(100, percentileType)
                            ),
                            evaluatorString(INTEGER, percentileType),
                            INTEGER,
                            equalTo(10)
                        )
                    ),
                    new TestCaseSupplier(
                        "averaged int",
                        List.of(INTEGER, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10, 5, 10), INTEGER, "field"),
                                percentileWithType(75, percentileType)
                            ),
                            evaluatorString(INTEGER, percentileType),
                            INTEGER,
                            equalTo(7)
                        )
                    ),
                    new TestCaseSupplier(
                        "big int difference",
                        List.of(INTEGER, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(Integer.MIN_VALUE, Integer.MAX_VALUE), INTEGER, "field"),
                                percentileWithType(50, percentileType)
                            ),
                            evaluatorString(INTEGER, percentileType),
                            INTEGER,
                            equalTo(-1) // Negative max is 1 smaller than positive max
                        )
                    ),

                    // Long
                    new TestCaseSupplier(
                        "median long",
                        List.of(LONG, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10L, 5L, 10L), LONG, "field"),
                                percentileWithType(50, percentileType)
                            ),
                            evaluatorString(LONG, percentileType),
                            LONG,
                            equalTo(5L)
                        )
                    ),
                    new TestCaseSupplier(
                        "single value long",
                        List.of(LONG, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(55L), LONG, "field"),
                                percentileWithType(randomIntBetween(0, 100), percentileType)
                            ),
                            evaluatorString(LONG, percentileType),
                            LONG,
                            equalTo(55L)
                        )
                    ),
                    new TestCaseSupplier(
                        "p0 long",
                        List.of(LONG, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10L, 5L, 10L), LONG, "field"),
                                percentileWithType(0, percentileType)
                            ),
                            evaluatorString(LONG, percentileType),
                            LONG,
                            equalTo(-10L)
                        )
                    ),
                    new TestCaseSupplier(
                        "p100 long",
                        List.of(LONG, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10L, 5L, 10L), LONG, "field"),
                                percentileWithType(100, percentileType)
                            ),
                            evaluatorString(LONG, percentileType),
                            LONG,
                            equalTo(10L)
                        )
                    ),
                    new TestCaseSupplier(
                        "averaged long",
                        List.of(LONG, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(-10L, 5L, 10L), LONG, "field"),
                                percentileWithType(75, percentileType)
                            ),
                            evaluatorString(LONG, percentileType),
                            LONG,
                            equalTo(7L)
                        )
                    ),
                    new TestCaseSupplier(
                        "big long difference",
                        List.of(LONG, percentileType),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(
                                new TestCaseSupplier.TypedData(List.of(Long.MIN_VALUE, Long.MAX_VALUE), LONG, "field"),
                                percentileWithType(50, percentileType)
                            ),
                            evaluatorString(LONG, percentileType),
                            LONG,
                            equalTo(0L)
                        )
                    )
                )
            );
        }

        return parameterSuppliersFromTypedDataWithDefaultChecks(
            (nullPosition, nullValueDataType, original) -> nullValueDataType == DataType.NULL && nullPosition == 0
                ? DataType.NULL
                : original.expectedType(),
            (nullPosition, nullData, original) -> original,
            cases,
            (v, p) -> "numeric except unsigned_long"
        );
    }

    @SuppressWarnings("unchecked")
    private static TestCaseSupplier makeSupplier(
        TestCaseSupplier.TypedDataSupplier fieldSupplier,
        TestCaseSupplier.TypedDataSupplier percentileSupplier
    ) {
        return new TestCaseSupplier(
            "field: " + fieldSupplier.name() + ", percentile: " + percentileSupplier.name(),
            List.of(fieldSupplier.type(), percentileSupplier.type()),
            () -> {
                var fieldTypedData = fieldSupplier.get();
                var percentileTypedData = percentileSupplier.get();

                var values = (List<Number>) fieldTypedData.data();
                var percentile = ((Number) percentileTypedData.data()).doubleValue();

                var expected = calculatePercentile(values, percentile);

                return new TestCaseSupplier.TestCase(
                    List.of(fieldTypedData, percentileTypedData),
                    evaluatorString(fieldSupplier.type(), percentileSupplier.type()),
                    fieldSupplier.type(),
                    expected instanceof Double expectedDouble
                        ? closeTo(expectedDouble, Math.abs(expectedDouble * 0.0000001))
                        : equalTo(expected)
                );
            }
        );
    }

    private static Number calculatePercentile(List<Number> rawValues, double percentile) {
        if (rawValues.isEmpty() || percentile < 0 || percentile > 100) {
            return null;
        }

        if (rawValues.size() == 1) {
            return rawValues.get(0);
        }

        int valueCount = rawValues.size();
        var p = percentile / 100.0;
        var index = p * (valueCount - 1);
        var lowerIndex = (int) index;
        var upperIndex = lowerIndex + 1;
        var fraction = index - lowerIndex;

        if (rawValues.get(0) instanceof Integer) {
            var values = rawValues.stream().mapToInt(Number::intValue).sorted().toArray();

            if (percentile == 0) {
                return values[0];
            } else if (percentile == 100) {
                return values[valueCount - 1];
            } else {
                assert lowerIndex >= 0 && upperIndex < valueCount;
                var difference = (long) values[upperIndex] - values[lowerIndex];
                return values[lowerIndex] + (int) (fraction * difference);
            }
        }

        if (rawValues.get(0) instanceof Long) {
            var values = rawValues.stream().mapToLong(Number::longValue).sorted().toArray();

            if (percentile == 0) {
                return values[0];
            } else if (percentile == 100) {
                return values[valueCount - 1];
            } else {
                assert lowerIndex >= 0 && upperIndex < valueCount;
                return calculatePercentile(fraction, new BigDecimal(values[lowerIndex]), new BigDecimal(values[upperIndex])).longValue();
            }
        }

        if (rawValues.get(0) instanceof Double) {
            var values = rawValues.stream().mapToDouble(Number::doubleValue).sorted().toArray();

            if (percentile == 0) {
                return values[0];
            } else if (percentile == 100) {
                return values[valueCount - 1];
            } else {
                assert lowerIndex >= 0 && upperIndex < valueCount;
                return calculatePercentile(fraction, new BigDecimal(values[lowerIndex]), new BigDecimal(values[upperIndex])).doubleValue();
            }
        }

        throw new IllegalArgumentException("Unsupported type: " + rawValues.get(0).getClass());
    }

    private static BigDecimal calculatePercentile(double fraction, BigDecimal lowerValue, BigDecimal upperValue) {
        return lowerValue.add(new BigDecimal(fraction).multiply(upperValue.subtract(lowerValue)));
    }

    private static TestCaseSupplier.TypedData percentileWithType(int value, DataType type) {
        return new TestCaseSupplier.TypedData(switch (type) {
            case INTEGER -> value;
            case LONG -> (long) value;
            default -> (double) value;
        }, type, "percentile");
    }

    private static String evaluatorString(DataType fieldDataType, DataType percentileDataType) {
        var fieldTypeName = StringUtils.underscoreToLowerCamelCase(fieldDataType.name());
        var percentileTypeName = StringUtils.underscoreToLowerCamelCase(percentileDataType.name());

        fieldTypeName = fieldTypeName.substring(0, 1).toUpperCase() + fieldTypeName.substring(1);
        percentileTypeName = percentileTypeName.substring(0, 1).toUpperCase() + percentileTypeName.substring(1);

        return "MvPercentile"
            + fieldTypeName
            + percentileTypeName
            + "Evaluator[values=Attribute[channel=0], percentile=Attribute[channel=1]]";
    }

    @Override
    protected final Expression build(Source source, List<Expression> args) {
        return new MvPercentile(source, args.get(0), args.get(1));
    }
}
