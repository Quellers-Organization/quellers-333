/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.spatial;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;
import org.elasticsearch.xpack.esql.expression.function.FunctionName;
import org.elasticsearch.xpack.esql.expression.function.TestCaseSupplier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@FunctionName("st_dwithin")
public class StDWithinTests extends BinarySpatialFunctionTestCase {
    public StDWithinTests(@Name("TestCase") Supplier<TestCaseSupplier.TestCase> testCaseSupplier) {
        this.testCase = testCaseSupplier.get();
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() {
        List<TestCaseSupplier> suppliers = new ArrayList<>();
        DataType[] geoDataTypes = { DataType.GEO_POINT };
        StDWithinTests.addSpatialCombinations(suppliers, geoDataTypes);
        DataType[] cartesianDataTypes = { DataType.CARTESIAN_POINT };
        StDWithinTests.addSpatialCombinations(suppliers, cartesianDataTypes);
        return parameterSuppliersFromTypedData(
            errorsForCasesWithoutExamples(anyNullIsNull(true, suppliers), StDWithinTests::typeErrorMessage)
        );
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new StDWithin(source, args.get(0), args.get(1), args.get(2));
    }

    protected static void addSpatialCombinations(List<TestCaseSupplier> suppliers, DataType[] dataTypes) {
        List<TestCaseSupplier.TypedDataSupplier> argSupplier = TestCaseSupplier.doubleCases(0.0, 1e10, true);
        addSpatialCombinations(suppliers, argSupplier, dataTypes, DataType.BOOLEAN, StDWithinTests::compareDistances, true);
    }

    private static boolean compareDistances(Object result, Object argument) {
        return ((Number) result).doubleValue() < ((Number) argument).doubleValue();
    }

    protected static String typeErrorMessage(boolean includeOrdinal, List<Set<DataType>> validPerPosition, List<DataType> types) {
        return typeErrorMessage(includeOrdinal, validPerPosition, types, true);
    }
}
