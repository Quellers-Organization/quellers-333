/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.fulltext;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.xpack.core.security.authc.support.mapper.expressiondsl.FieldExpression;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;
import org.elasticsearch.xpack.esql.expression.function.AbstractFunctionTestCase;
import org.elasticsearch.xpack.esql.expression.function.FunctionName;
import org.elasticsearch.xpack.esql.expression.function.TestCaseSupplier;
import org.hamcrest.Matcher;
import org.junit.BeforeClass;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.elasticsearch.xpack.esql.action.EsqlCapabilities.Cap.MATCH_FUNCTION;
import static org.hamcrest.Matchers.equalTo;

@FunctionName("match")
public class MatchFunctionTests extends AbstractFunctionTestCase {

    @BeforeClass
    public static void checkFunctionEnabled() {
        assumeTrue("MATCH function should be enabled ", MATCH_FUNCTION.isEnabled());
    }

    public MatchFunctionTests(@Name("TestCase") Supplier<TestCaseSupplier.TestCase> testCaseSupplier) {
        this.testCase = testCaseSupplier.get();
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() {
        final Set<DataType> validTypes = Set.of(DataType.KEYWORD, DataType.TEXT);
        final List<Set<DataType>> validPerPosition = List.of(validTypes, validTypes);
        List<TestCaseSupplier> suppliers = new LinkedList<>();
        // Don't test null, as it is not allowed but the expected message is not a type error - so we check it separately in VerifierTests

        for (DataType fieldType : validStringDataTypes()) {
            for (DataType queryType : validStringDataTypes()) {
                suppliers.add(
                    new TestCaseSupplier(
                        "<" + fieldType + ", " + queryType + ">",
                        List.of(fieldType, queryType),
                        () -> testCase(fieldType, randomIdentifier(), queryType, randomAlphaOfLengthBetween(1, 10), equalTo(true))
                    )
                );
            }
        }
        List<TestCaseSupplier> errorsSuppliers = errorsForCasesWithoutExamples(suppliers, (v, p) -> "string");
        // Don't test null, as it is not allowed but the expected message is not a type error - so we check it separately in VerifierTests
        return parameterSuppliersFromTypedData(errorsSuppliers.stream().filter(s -> s.types().contains(DataType.NULL) == false).toList());
    }

    private static List<DataType> validStringDataTypes() {
        return Arrays.stream(DataType.values()).filter(DataType::isString).toList();
    }

    public final void testFold() {
        Expression expression = buildLiteralExpression(testCase);
        if (testCase.getExpectedTypeError() != null) {
            assertTrue("expected unresolved", expression.typeResolved().unresolved());
            assertThat(expression.typeResolved().message(), equalTo(testCase.getExpectedTypeError()));
        }
    }

    private static TestCaseSupplier.TestCase testCase(
        DataType fieldType,
        String field,
        DataType queryType,
        String query,
        Matcher<Boolean> matcher
    ) {
        return new TestCaseSupplier.TestCase(
            List.of(
                new TestCaseSupplier.TypedData(
                    new FieldExpression(field, List.of(new FieldExpression.FieldValue(field))),
                    fieldType,
                    "field"
                ),
                new TestCaseSupplier.TypedData(new BytesRef(query), queryType, "query")
            ),
            "EndsWithEvaluator[str=Attribute[channel=0], suffix=Attribute[channel=1]]",
            DataType.BOOLEAN,
            matcher
        );
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new MatchFunction(source, args.get(0), args.get(1));
    }
}
