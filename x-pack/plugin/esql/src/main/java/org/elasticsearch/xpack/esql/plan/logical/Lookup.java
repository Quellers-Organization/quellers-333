/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.logical;

import org.elasticsearch.core.Nullable;
import org.elasticsearch.xpack.esql.io.stream.PlanStreamInput;
import org.elasticsearch.xpack.esql.io.stream.PlanStreamOutput;
import org.elasticsearch.xpack.esql.plan.logical.join.Join;
import org.elasticsearch.xpack.esql.plan.logical.local.LocalRelation;
import org.elasticsearch.xpack.ql.capabilities.Resolvables;
import org.elasticsearch.xpack.ql.expression.Attribute;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.NamedExpression;
import org.elasticsearch.xpack.ql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.ql.plan.logical.UnaryPlan;
import org.elasticsearch.xpack.ql.tree.NodeInfo;
import org.elasticsearch.xpack.ql.tree.Source;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.xpack.esql.expression.NamedExpressions.mergeOutputAttributes;

/**
 * Looks up values from the associated {@code tables}.
 * The class is used only during the analysis phased, after which it is replaced by a {@code Join}.
 */
public class Lookup extends UnaryPlan {
    private final Expression tableName;
    private final List<NamedExpression> matchFields;
    // initialized during the analysis phase for output and validation
    // afterward, it is converted into a Join (BinaryPlan) hence why here it is not a child
    private final LocalRelation localRelation;
    private List<Attribute> lazyOutput;

    public Lookup(
        Source source,
        LogicalPlan child,
        Expression tableName,
        List<NamedExpression> matchFields,
        @Nullable LocalRelation localRelation
    ) {
        super(source, child);
        this.tableName = tableName;
        this.matchFields = matchFields;
        this.localRelation = localRelation;
    }

    public Lookup(PlanStreamInput in) throws IOException {
        super(in.readSource(), in.readLogicalPlanNode());
        this.tableName = in.readExpression();
        this.matchFields = in.readCollectionAsList(i -> ((PlanStreamInput) i).readNamedExpression());
        this.localRelation = in.readBoolean() ? new LocalRelation(in) : null;
    }

    public void writeTo(PlanStreamOutput out) throws IOException {
        out.writeSource(source());
        out.writeLogicalPlanNode(child());
        out.writeExpression(tableName);
        out.writeCollection(matchFields, (o, v) -> ((PlanStreamOutput) o).writeNamedExpression(v));
        if (localRelation == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            localRelation.writeTo(out);
        }
    }

    public Expression tableName() {
        return tableName;
    }

    public List<NamedExpression> matchFields() {
        return matchFields;
    }

    public LocalRelation localRelation() {
        return localRelation;
    }

    @Override
    public boolean expressionsResolved() {
        return tableName.resolved() && Resolvables.resolved(matchFields);
    }

    @Override
    public UnaryPlan replaceChild(LogicalPlan newChild) {
        return new Lookup(source(), newChild, tableName, matchFields, localRelation);
    }

    @Override
    protected NodeInfo<? extends LogicalPlan> info() {
        return NodeInfo.create(this, Lookup::new, child(), tableName, matchFields, localRelation);
    }

    @Override
    public List<Attribute> output() {
        if (lazyOutput == null) {
            List<? extends NamedExpression> rightSide = localRelation != null
                ? Join.makeNullable(Join.makeReference(localRelation.output()))
                : matchFields;
            lazyOutput = mergeOutputAttributes(child().output(), rightSide);
        }
        return lazyOutput;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (super.equals(o) == false) {
            return false;
        }
        Lookup lookup = (Lookup) o;
        return Objects.equals(tableName, lookup.tableName)
            && Objects.equals(matchFields, lookup.matchFields)
            && Objects.equals(localRelation, lookup.localRelation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tableName, matchFields, localRelation);
    }
}
