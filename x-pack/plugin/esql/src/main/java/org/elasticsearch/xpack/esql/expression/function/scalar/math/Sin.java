/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.math;

import org.elasticsearch.compute.ann.Evaluator;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.xpack.esql.expression.function.Described;
import org.elasticsearch.xpack.esql.expression.function.Named;
import org.elasticsearch.xpack.esql.expression.function.Typed;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.tree.NodeInfo;
import org.elasticsearch.xpack.ql.tree.Source;

import java.util.List;

/**
 * Sine trigonometric function.
 */
public class Sin extends AbstractTrigonometricFunction {

    @Described("Returns the trigonometric sine of an angle")
    @Typed("double")
    public Sin(Source source, @Named("n") @Typed("numeric") @Described("An angle, in radians") Expression n) {
        super(source, n);
    }

    @Override
    protected EvalOperator.ExpressionEvaluator doubleEvaluator(EvalOperator.ExpressionEvaluator field, DriverContext dvrCtx) {
        return new SinEvaluator(field, dvrCtx);
    }

    @Override
    public Expression replaceChildren(List<Expression> newChildren) {
        return new Sin(source(), newChildren.get(0));
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, Sin::new, field());
    }

    @Evaluator
    static double process(double val) {
        return Math.sin(val);
    }
}
