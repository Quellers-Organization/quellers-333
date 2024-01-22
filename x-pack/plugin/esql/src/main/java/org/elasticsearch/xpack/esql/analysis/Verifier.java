/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.analysis;

import org.elasticsearch.xpack.esql.evaluator.predicate.operator.comparison.Equals;
import org.elasticsearch.xpack.esql.evaluator.predicate.operator.comparison.NotEquals;
import org.elasticsearch.xpack.esql.expression.function.UnsupportedAttribute;
import org.elasticsearch.xpack.esql.expression.predicate.operator.arithmetic.Neg;
import org.elasticsearch.xpack.esql.plan.logical.Eval;
import org.elasticsearch.xpack.esql.plan.logical.RegexExtract;
import org.elasticsearch.xpack.esql.plan.logical.Row;
import org.elasticsearch.xpack.esql.stats.FeatureMetric;
import org.elasticsearch.xpack.esql.stats.Metrics;
import org.elasticsearch.xpack.esql.type.EsqlDataTypes;
import org.elasticsearch.xpack.ql.capabilities.Unresolvable;
import org.elasticsearch.xpack.ql.common.Failure;
import org.elasticsearch.xpack.ql.expression.Alias;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.Expressions;
import org.elasticsearch.xpack.ql.expression.NamedExpression;
import org.elasticsearch.xpack.ql.expression.TypeResolutions;
import org.elasticsearch.xpack.ql.expression.UnresolvedAttribute;
import org.elasticsearch.xpack.ql.expression.function.aggregate.AggregateFunction;
import org.elasticsearch.xpack.ql.expression.predicate.BinaryOperator;
import org.elasticsearch.xpack.ql.expression.predicate.operator.comparison.BinaryComparison;
import org.elasticsearch.xpack.ql.plan.logical.Aggregate;
import org.elasticsearch.xpack.ql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.ql.plan.logical.Project;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.elasticsearch.xpack.ql.analyzer.VerifierChecks.checkFilterConditionType;
import static org.elasticsearch.xpack.ql.common.Failure.fail;
import static org.elasticsearch.xpack.ql.expression.TypeResolutions.ParamOrdinal.FIRST;

public class Verifier {

    private final Metrics metrics;

    public Verifier(Metrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Verify that a {@link LogicalPlan} can be executed.
     *
     * @param plan The logical plan to be verified
     * @param partialMetrics a bitset indicating a certain command (or "telemetry feature") is present in the query
     * @return a collection of verification failures; empty if and only if the plan is valid
     */
    Collection<Failure> verify(LogicalPlan plan, BitSet partialMetrics) {
        assert partialMetrics != null;
        Set<Failure> failures = new LinkedHashSet<>();

        // quick verification for unresolved attributes
        plan.forEachUp(p -> {
            // if the children are unresolved, so will this node; counting it will only add noise
            if (p.childrenResolved() == false) {
                return;
            }

            if (p instanceof Unresolvable u) {
                failures.add(fail(p, u.unresolvedMessage()));
            }
            // p is resolved, skip
            else if (p.resolved()) {
                return;
            }
            // handle aggregate first to disambiguate between missing fields or incorrect function declaration
            if (p instanceof Aggregate aggregate) {
                for (NamedExpression agg : aggregate.aggregates()) {
                    var child = Alias.unwrap(agg);
                    if (child instanceof UnresolvedAttribute) {
                        failures.add(fail(child, "invalid stats declaration; [{}] is not an aggregate function", child.sourceText()));
                    }
                }
            }
            p.forEachExpression(e -> {
                // everything is fine, skip expression
                if (e.resolved()) {
                    return;
                }

                e.forEachUp(ae -> {
                    // we're only interested in the children
                    if (ae.childrenResolved() == false) {
                        return;
                    }

                    if (ae instanceof Unresolvable u) {
                        // special handling for Project and unsupported types
                        if (p instanceof Project == false || u instanceof UnsupportedAttribute == false) {
                            failures.add(fail(ae, u.unresolvedMessage()));
                        }
                    }
                    if (ae.typeResolved().unresolved()) {
                        failures.add(fail(ae, ae.typeResolved().message()));
                    }
                });
            });
        });

        // in case of failures bail-out as all other checks will be redundant
        if (failures.isEmpty() == false) {
            return failures;
        }

        // Concrete verifications
        plan.forEachDown(p -> {
            // if the children are unresolved, so will this node; counting it will only add noise
            if (p.childrenResolved() == false) {
                return;
            }
            checkFilterConditionType(p, failures);
            checkAggregate(p, failures);
            checkRegexExtractOnlyOnStrings(p, failures);

            checkRow(p, failures);
            checkEvalFields(p, failures);

            checkOperationsOnUnsignedLong(p, failures);
            checkBinaryComparison(p, failures);
        });

        // gather metrics
        if (failures.isEmpty()) {
            gatherMetrics(plan, partialMetrics);
        }

        return failures;
    }

    private static void checkAggregate(LogicalPlan p, Set<Failure> failures) {
        if (p instanceof Aggregate agg) {
            // check aggregates
            agg.aggregates().forEach(e -> {
                var exp = Alias.unwrap(e);
                if (exp instanceof AggregateFunction af) {
                    af.field().forEachDown(AggregateFunction.class, f -> {
                        failures.add(fail(f, "nested aggregations [{}] not allowed inside other aggregations [{}]", f, af));
                    });
                } else {
                    if (Expressions.match(agg.groupings(), g -> Alias.unwrap(g).semanticEquals(exp)) == false) {
                        failures.add(
                            fail(
                                exp,
                                "expected an aggregate function or group but got ["
                                    + exp.sourceText()
                                    + "] of type ["
                                    + exp.nodeName()
                                    + "]"
                            )
                        );
                    }
                }
            });

            // check grouping
            // The grouping can not be an aggregate function
            agg.groupings().forEach(e -> e.forEachUp(g -> {
                if (g instanceof AggregateFunction af) {
                    failures.add(fail(g, "cannot use an aggregate [{}] for grouping", af));
                }
            }));
        }
    }

    private static void checkRegexExtractOnlyOnStrings(LogicalPlan p, Set<Failure> failures) {
        if (p instanceof RegexExtract re) {
            Expression expr = re.input();
            DataType type = expr.dataType();
            if (EsqlDataTypes.isString(type) == false) {
                failures.add(
                    fail(
                        expr,
                        "{} only supports KEYWORD or TEXT values, found expression [{}] type [{}]",
                        re.getClass().getSimpleName(),
                        expr.sourceText(),
                        type
                    )
                );
            }
        }
    }

    private static void checkRow(LogicalPlan p, Set<Failure> failures) {
        if (p instanceof Row row) {
            row.fields().forEach(a -> {
                if (EsqlDataTypes.isRepresentable(a.dataType()) == false) {
                    failures.add(fail(a, "cannot use [{}] directly in a row assignment", a.child().sourceText()));
                }
            });
        }
    }

    private static void checkEvalFields(LogicalPlan p, Set<Failure> failures) {
        if (p instanceof Eval eval) {
            eval.fields().forEach(field -> {
                // check supported types
                DataType dataType = field.dataType();
                if (EsqlDataTypes.isRepresentable(dataType) == false) {
                    failures.add(
                        fail(field, "EVAL does not support type [{}] in expression [{}]", dataType.typeName(), field.child().sourceText())
                    );
                }
                // check no aggregate functions are used
                field.forEachDown(AggregateFunction.class, af -> {
                    failures.add(fail(af, "aggregate function [{}] not allowed outside STATS command", af.sourceText()));
                });
            });
        }
    }

    private static void checkOperationsOnUnsignedLong(LogicalPlan p, Set<Failure> failures) {
        p.forEachExpression(e -> {
            Failure f = null;

            if (e instanceof BinaryOperator<?, ?, ?, ?> bo) {
                f = validateUnsignedLongOperator(bo);
            } else if (e instanceof Neg neg) {
                f = validateUnsignedLongNegation(neg);
            }

            if (f != null) {
                failures.add(f);
            }
        });
    }

    private static void checkBinaryComparison(LogicalPlan p, Set<Failure> failures) {
        p.forEachExpression(BinaryComparison.class, bc -> {
            Failure f = validateBinaryComparison(bc);
            if (f != null) {
                failures.add(f);
            }
        });
    }

    private void gatherMetrics(LogicalPlan plan, BitSet b) {
        plan.forEachDown(p -> FeatureMetric.set(p, b));
        for (int i = b.nextSetBit(0); i >= 0; i = b.nextSetBit(i + 1)) {
            metrics.inc(FeatureMetric.values()[i]);
        }
    }

    /**
     * Limit QL's comparisons to types we support.
     */
    public static Failure validateBinaryComparison(BinaryComparison bc) {
        if (bc.left().dataType().isNumeric()) {
            if (false == bc.right().dataType().isNumeric()) {
                return fail(
                    bc,
                    "first argument of [{}] is [numeric] so second argument must also be [numeric] but was [{}]",
                    bc.sourceText(),
                    bc.right().dataType().typeName()
                );
            }
            return null;
        }

        List<DataType> allowed = new ArrayList<>();
        allowed.add(DataTypes.KEYWORD);
        allowed.add(DataTypes.TEXT);
        allowed.add(DataTypes.IP);
        allowed.add(DataTypes.DATETIME);
        allowed.add(DataTypes.VERSION);
        allowed.add(EsqlDataTypes.GEO_POINT);
        allowed.add(EsqlDataTypes.GEO_SHAPE);
        allowed.add(EsqlDataTypes.CARTESIAN_POINT);
        allowed.add(EsqlDataTypes.CARTESIAN_SHAPE);
        if (bc instanceof Equals || bc instanceof NotEquals) {
            allowed.add(DataTypes.BOOLEAN);
        }
        Expression.TypeResolution r = TypeResolutions.isType(
            bc.left(),
            allowed::contains,
            bc.sourceText(),
            FIRST,
            Stream.concat(Stream.of("numeric"), allowed.stream().map(DataType::typeName)).toArray(String[]::new)
        );
        if (false == r.resolved()) {
            return fail(bc, r.message());
        }
        if (DataTypes.isString(bc.left().dataType()) && DataTypes.isString(bc.right().dataType())) {
            return null;
        }
        if (bc.left().dataType() != bc.right().dataType()) {
            return fail(
                bc,
                "first argument of [{}] is [{}] so second argument must also be [{}] but was [{}]",
                bc.sourceText(),
                bc.left().dataType().typeName(),
                bc.left().dataType().typeName(),
                bc.right().dataType().typeName()
            );
        }
        return null;
    }

    /** Ensure that UNSIGNED_LONG types are not implicitly converted when used in arithmetic binary operator, as this cannot be done since:
     *  - unsigned longs are passed through the engine as longs, so/and
     *  - negative values cannot be represented (i.e. range [Long.MIN_VALUE, "abs"(Long.MIN_VALUE) + Long.MAX_VALUE] won't fit on 64 bits);
     *  - a conversion to double isn't possible, since upper range UL values can no longer be distinguished
     *  ex: (double) 18446744073709551615 == (double) 18446744073709551614
     *  - the implicit ESQL's Cast doesn't currently catch Exception and nullify the result.
     *  Let the user handle the operation explicitly.
     */
    public static Failure validateUnsignedLongOperator(BinaryOperator<?, ?, ?, ?> bo) {
        DataType leftType = bo.left().dataType();
        DataType rightType = bo.right().dataType();
        if ((leftType == DataTypes.UNSIGNED_LONG || rightType == DataTypes.UNSIGNED_LONG) && leftType != rightType) {
            return fail(
                bo,
                "first argument of [{}] is [{}] and second is [{}]. [{}] can only be operated on together with another [{}]",
                bo.sourceText(),
                leftType.typeName(),
                rightType.typeName(),
                DataTypes.UNSIGNED_LONG.typeName(),
                DataTypes.UNSIGNED_LONG.typeName()
            );
        }
        return null;
    }

    /**
     * Negating an unsigned long is invalid.
     */
    private static Failure validateUnsignedLongNegation(Neg neg) {
        DataType childExpressionType = neg.field().dataType();
        if (childExpressionType.equals(DataTypes.UNSIGNED_LONG)) {
            return fail(
                neg,
                "negation unsupported for arguments of type [{}] in expression [{}]",
                childExpressionType.typeName(),
                neg.sourceText()
            );
        }
        return null;
    }
}
