/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.planner;

import org.elasticsearch.xpack.esql.EsqlIllegalArgumentException;
import org.elasticsearch.xpack.esql.expression.FieldAttribute;
import org.elasticsearch.xpack.esql.querydsl.query.SingleValueQuery;
import org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.Expressions;
import org.elasticsearch.xpack.ql.expression.MetadataAttribute;
import org.elasticsearch.xpack.ql.expression.function.scalar.ScalarFunction;
import org.elasticsearch.xpack.ql.expression.predicate.nulls.IsNotNull;
import org.elasticsearch.xpack.ql.expression.predicate.nulls.IsNull;
import org.elasticsearch.xpack.ql.querydsl.query.NestedQuery;
import org.elasticsearch.xpack.ql.querydsl.query.Query;
import org.elasticsearch.xpack.ql.type.DataType;

import java.util.function.Supplier;

public final class EsqlTranslatorHandler {

    public Query asQuery(Expression e) {
        return EsqlExpressionTranslators.toQuery(e, this);
    }

    public Object convert(Object value, DataType dataType) {
        return EsqlDataTypeConverter.convert(value, dataType);
    }

    public Query wrapFunctionQuery(ScalarFunction sf, Expression field, Supplier<Query> querySupplier) {
        if (field instanceof FieldAttribute fa) {
            if (fa.getExactInfo().hasExact()) {
                var exact = fa.exactAttribute();
                if (exact != fa) {
                    fa = exact;
                }
            }
            // don't wrap is null/is not null with SVQ
            Query query = querySupplier.get();
            if ((sf instanceof IsNull || sf instanceof IsNotNull) == false) {
                query = new SingleValueQuery(query, fa.name());
            }
            return wrapIfNested(query, field);
        }
        if (field instanceof MetadataAttribute) {
            return querySupplier.get(); // MetadataAttributes are always single valued
        }
        throw new EsqlIllegalArgumentException("Expected a FieldAttribute or MetadataAttribute but received [" + field + "]");
    }

    public String nameOf(Expression e) {
        return Expressions.name(e);
    }

    private static Query wrapIfNested(Query query, Expression exp) {
        if (query != null && exp instanceof FieldAttribute fa) {
            if (fa.isNested()) {
                return new NestedQuery(fa.source(), fa.nestedParent().name(), query);
            }
        }
        return query;
    }
}
