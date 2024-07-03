/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.esql.core.expression.predicate.nulls;

import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.FieldAttribute;
import org.elasticsearch.xpack.esql.core.expression.Nullability;
import org.elasticsearch.xpack.esql.core.expression.function.scalar.UnaryScalarFunction;
import org.elasticsearch.xpack.esql.core.expression.predicate.Negatable;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;

import java.io.IOException;
import java.util.function.Predicate;

public class IsNotNull extends UnaryScalarFunction implements Negatable<UnaryScalarFunction> {
    public static final NamedWriteableRegistry.Entry ENTRY = new NamedWriteableRegistry.Entry(
        Expression.class,
        "IsNotNull",
        IsNotNull::new
    );

    public IsNotNull(Source source, Expression field) {
        super(source, field);
    }

    private IsNotNull(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public String getWriteableName() {
        return ENTRY.name;
    }

    @Override
    protected NodeInfo<IsNotNull> info() {
        return NodeInfo.create(this, IsNotNull::new, field());
    }

    @Override
    protected IsNotNull replaceChild(Expression newChild) {
        return new IsNotNull(source(), newChild);
    }

    @Override
    public Object fold() {
        return field().fold() != null && DataType.isNull(field().dataType()) == false;
    }

    @Override
    public Nullability nullable() {
        return Nullability.FALSE;
    }

    @Override
    public DataType dataType() {
        return DataType.BOOLEAN;
    }

    @Override
    public UnaryScalarFunction negate() {
        return new IsNull(source(), field());
    }

    @Override
    public boolean canPushQueryToSource(Predicate<FieldAttribute> hasIdenticalDelegate) {
        if (field() instanceof FieldAttribute fa && fa.dataType().equals(DataType.TEXT)) {
            return true;
        }
        return isAttributePushable(field(), false, hasIdenticalDelegate);
    }
}
