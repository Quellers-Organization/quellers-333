/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.logical.join;

import org.elasticsearch.xpack.esql.expression.NamedExpressions;
import org.elasticsearch.xpack.ql.expression.Attribute;
import org.elasticsearch.xpack.ql.expression.FieldAttribute;
import org.elasticsearch.xpack.ql.expression.Nullability;
import org.elasticsearch.xpack.ql.expression.ReferenceAttribute;
import org.elasticsearch.xpack.ql.plan.logical.BinaryPlan;
import org.elasticsearch.xpack.ql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.ql.tree.NodeInfo;
import org.elasticsearch.xpack.ql.tree.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Join extends BinaryPlan {

    private final JoinConfig config;
    private List<Attribute> lazyOutput;

    public Join(Source source, LogicalPlan left, LogicalPlan right, JoinConfig config) {
        super(source, left, right);
        this.config = config;
    }

    public JoinConfig config() {
        return config;
    }

    @Override
    protected NodeInfo<Join> info() {
        return NodeInfo.create(this, Join::new, left(), right(), config);
    }

    @Override
    public Join replaceChildren(List<LogicalPlan> newChildren) {
        return new Join(source(), newChildren.get(0), newChildren.get(1), config);
    }

    public Join replaceChildren(LogicalPlan left, LogicalPlan right) {
        return new Join(source(), left, right, config);
    }

    @Override
    public List<Attribute> output() {
        if (lazyOutput == null) {
            lazyOutput = computeOutput();
        }
        return lazyOutput;
    }

    private List<Attribute> computeOutput() {
        List<Attribute> right = makeReference(right().output());
        return switch (config.type()) {
            case LEFT -> // right side becomes nullable
                NamedExpressions.mergeOutputAttributes(left().output(), makeNullable(right));
            case RIGHT -> // left side becomes nullable
                NamedExpressions.mergeOutputAttributes(makeNullable(left().output()), right);
            case FULL -> // both sides become nullable
                NamedExpressions.mergeOutputAttributes(makeNullable(left().output()), makeNullable(right));
            default -> // neither side becomes nullable
                NamedExpressions.mergeOutputAttributes(left().output(), right);
        };
    }

    /**
     * Make fields references, so we don't check if they exist in the index.
     * We do this for fields that we know don't come from the index.
     * NOCOMMIT we should signal this more clearly
     */
    private static List<Attribute> makeReference(List<Attribute> output) {
        List<Attribute> out = new ArrayList<>(output.size());
        for (Attribute a : output) {
            out.add(new ReferenceAttribute(a.source(), a.name(), a.dataType(), a.qualifier(), a.nullable(), a.id(), a.synthetic()));
        }
        return out;
    }

    private static List<Attribute> makeNullable(List<Attribute> output) {
        List<Attribute> out = new ArrayList<>(output.size());
        for (Attribute a : output) {
            out.add(a.withNullability(Nullability.TRUE));
        }
        return out;
    }

    @Override
    public boolean expressionsResolved() {
        return true;
    }

    public boolean duplicatesResolved() {
        return left().outputSet().intersect(right().outputSet()).isEmpty();
    }

    @Override
    public boolean resolved() {
        // resolve the join if
        // - the children are resolved
        // - there are no conflicts in output
        // - the condition (if present) is resolved to a boolean
        return childrenResolved() && duplicatesResolved() && expressionsResolved();
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, left(), right());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Join other = (Join) obj;
        return config.equals(other.config) && Objects.equals(left(), other.left()) && Objects.equals(right(), other.right());
    }
}
