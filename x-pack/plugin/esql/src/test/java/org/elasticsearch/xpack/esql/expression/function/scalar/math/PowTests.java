/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.math;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

import org.elasticsearch.xpack.esql.expression.function.TestCaseSupplier;
import org.elasticsearch.xpack.esql.expression.function.scalar.AbstractScalarFunctionTestCase;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;

import java.util.List;
import java.util.function.Supplier;

public class PowTests extends AbstractScalarFunctionTestCase {
    public PowTests(@Name("TestCase") Supplier<TestCaseSupplier.TestCase> testCaseSupplier) {
        this.testCase = testCaseSupplier.get();
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() {
        List<TestCaseSupplier> suppliers = TestCaseSupplier.forBinaryCastingToDouble(
            "PowEvaluator",
            "base",
            "exponent",
            Math::pow,
            // 143^143 is still representable, but 144^144 is infinite
            0d,
            143d,
            0d,
            143d,
            List.of()
        );
        return parameterSuppliersFromTypedData(errorsForCasesWithoutExamples(anyNullIsNull(true, suppliers)));
    }

    @Override
    protected DataType expectedType(List<DataType> argTypes) {
        return DataTypes.DOUBLE;
    }

    @Override
    protected List<ArgumentSpec> argSpec() {
        return List.of(required(numerics()), required(numerics()));
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new Pow(source, args.get(0), args.get(1));
    }

}
