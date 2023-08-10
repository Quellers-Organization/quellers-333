/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.planner;

import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.xpack.ql.expression.predicate.operator.arithmetic.Neg;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

abstract class NegMapper extends EvalMapper.ExpressionMapper<Neg> {
    static final EvalMapper.ExpressionMapper<?> NEG_MAPPER = new NegMapper(
        org.elasticsearch.xpack.esql.expression.predicate.operator.arithmetic.NegIntsEvaluator::new,
        org.elasticsearch.xpack.esql.expression.predicate.operator.arithmetic.NegLongsEvaluator::new,
        org.elasticsearch.xpack.esql.expression.predicate.operator.arithmetic.NegDoublesEvaluator::new
    ) {
    };

    private final BiFunction<Source, EvalOperator.ExpressionEvaluator, EvalOperator.ExpressionEvaluator> ints;

    private final BiFunction<Source, EvalOperator.ExpressionEvaluator, EvalOperator.ExpressionEvaluator> longs;
    private final Function<EvalOperator.ExpressionEvaluator, EvalOperator.ExpressionEvaluator> doubles;

    private NegMapper(
        BiFunction<Source, EvalOperator.ExpressionEvaluator, EvalOperator.ExpressionEvaluator> ints,
        BiFunction<Source, EvalOperator.ExpressionEvaluator, EvalOperator.ExpressionEvaluator> longs,
        Function<EvalOperator.ExpressionEvaluator, EvalOperator.ExpressionEvaluator> doubles
    ) {
        this.ints = ints;
        this.longs = longs;
        this.doubles = doubles;
    }

    @Override
    protected final Supplier<EvalOperator.ExpressionEvaluator> map(Neg neg, Layout layout) {
        DataType type = neg.dataType();
        if (type.isNumeric()) {
            var childEvaluator = EvalMapper.toEvaluator(neg.field(), layout).get();

            if (type == DataTypes.INTEGER) {
                return () -> ints.apply(neg.source(), childEvaluator);
            }
            if (type == DataTypes.LONG) {
                return () -> longs.apply(neg.source(), childEvaluator);
            }
            if (type == DataTypes.UNSIGNED_LONG) {
                // TODO requires cast to long
                throw new AssertionError("negation of a ulong");
            }
            if (type == DataTypes.DOUBLE) {
                return () -> doubles.apply(childEvaluator);
            }
        }
        throw new AssertionError("arithmetic negation operator with non-numeric data type");
    }
}
