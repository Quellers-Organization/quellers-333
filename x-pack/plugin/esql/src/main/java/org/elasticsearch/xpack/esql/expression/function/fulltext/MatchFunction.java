/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.fulltext;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.FieldAttribute;
import org.elasticsearch.xpack.esql.core.expression.TypeResolutions;
import org.elasticsearch.xpack.esql.core.querydsl.query.MatchQuery;
import org.elasticsearch.xpack.esql.core.querydsl.query.Query;
import org.elasticsearch.xpack.esql.core.querydsl.query.QueryStringQuery;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.expression.function.Example;
import org.elasticsearch.xpack.esql.expression.function.FunctionInfo;
import org.elasticsearch.xpack.esql.expression.function.Param;
import org.elasticsearch.xpack.esql.io.stream.PlanStreamInput;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.ParamOrdinal.FIRST;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.ParamOrdinal.SECOND;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.isNotNull;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.isString;

/**
 * Full text function that performs a {@link QueryStringQuery} .
 */
public class MatchFunction extends FullTextFunction {

    public static final NamedWriteableRegistry.Entry ENTRY = new NamedWriteableRegistry.Entry(
        Expression.class,
        "Match",
        MatchFunction::new
    );

    private final Expression field;

    @FunctionInfo(
        returnType = "boolean",
        preview = true,
        description = "Performs a match query on the specified fields. Returns true if the provided query matches the row.",
        examples = { @Example(file = "match-function", tag = "match-with-field") }
    )
    public MatchFunction(
        Source source,
        @Param(
            name = "field",
            type = { "keyword", "text" },
            description = "Field or field pattern that the query will target."
        ) Expression field,
        @Param(
            name = "query",
            type = { "keyword", "text" },
            description = "Query string in Lucene query string format."
        ) Expression matchQuery
    ) {
        super(source, matchQuery, field);
        this.field = field;
    }

    private MatchFunction(StreamInput in) throws IOException {
        this(Source.readFrom((PlanStreamInput) in), in.readNamedWriteable(Expression.class), in.readNamedWriteable(Expression.class));
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        source().writeTo(out);
        out.writeNamedWriteable(field);
        out.writeNamedWriteable(query());
    }

    @Override
    public String functionName() {
        return "MATCH";
    }

    @Override
    public Query asQuery() {
        Object queryAsObject = query().fold();
        if (queryAsObject instanceof BytesRef == false) {
            throw new IllegalArgumentException("Query in MATCH function needs to be resolved to a string");
        }
        if (field instanceof FieldAttribute == false) {
            throw new IllegalArgumentException("Field in MATCH function needs to be a field");
        }

        return new MatchQuery(source(), ((FieldAttribute) field).name(), ((BytesRef) queryAsObject).utf8ToString());
    }

    @Override
    protected TypeResolution resolveNonQueryParamTypes() {
        return isNotNull(field, sourceText(), FIRST).and(isString(field, sourceText(), FIRST)).and(super.resolveNonQueryParamTypes());
    }

    @Override
    public Expression replaceChildren(List<Expression> newChildren) {
        // Query is the first child, field is the second child
        return new MatchFunction(source(), newChildren.get(1), newChildren.getFirst());
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, MatchFunction::new, field, query());
    }

    @Override
    public String getWriteableName() {
        return ENTRY.name;
    }

    protected TypeResolutions.ParamOrdinal queryParamOrdinal() {
        return SECOND;
    }
}
