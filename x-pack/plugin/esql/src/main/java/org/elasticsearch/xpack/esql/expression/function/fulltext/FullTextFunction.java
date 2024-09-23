/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.fulltext;

import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.xpack.esql.action.EsqlCapabilities;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.TypeResolutions;
import org.elasticsearch.xpack.esql.core.expression.function.Function;
import org.elasticsearch.xpack.esql.core.querydsl.query.Query;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.ParamOrdinal.DEFAULT;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.isNotNullAndFoldable;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.isString;

/**
 * Base class for full-text functions that use ES queries to match documents.
 * These functions needs to be pushed down to Lucene queries to be executed - there's no Evaluator for them, but depend on
 * {@link org.elasticsearch.xpack.esql.optimizer.LocalPhysicalPlanOptimizer} to rewrite them into Lucene queries.
 */
public abstract class FullTextFunction extends Function {
    public static List<NamedWriteableRegistry.Entry> getNamedWriteables() {
        List<NamedWriteableRegistry.Entry> entries = new ArrayList<>();
        if (EsqlCapabilities.Cap.QSTR_FUNCTION.isEnabled()) {
            entries.add(QueryStringFunction.ENTRY);
        }
        if (EsqlCapabilities.Cap.MATCH_FUNCTION.isEnabled()) {
            entries.add(MatchFunction.ENTRY);
        }
        return entries;
    }

    private final Expression query;

    protected FullTextFunction(Source source, Expression query, Expression... nonQueryChildren) {
        super(source, Stream.concat(Stream.of(query), Stream.of(nonQueryChildren)).toList());
        this.query = query;
    }

    @Override
    public DataType dataType() {
        return DataType.BOOLEAN;
    }

    @Override
    protected final TypeResolution resolveType() {
        if (childrenResolved() == false) {
            return new TypeResolution("Unresolved children");
        }

        return resolveNonQueryParamTypes().and(resolveQueryType());
    }

    private TypeResolution resolveQueryType() {
        return isString(query(), sourceText(), queryParamOrdinal()).and(isNotNullAndFoldable(query(), sourceText(), queryParamOrdinal()));
    }

    /**
     * Subclasses can override this method for custom type resolution
     * @return type resolution for non-query parameter types
     */
    protected TypeResolution resolveNonQueryParamTypes() {
        return TypeResolution.TYPE_RESOLVED;
    }

    public Expression query() {
        return query;
    }

    /**
     * Returns the resulting Query for the function parameters so it can be pushed down to Lucene
     * @return
     */
    public abstract Query asQuery();

    /**
     * Returns the param ordinal for the query parameter so it can be used in error messages
     *
     * @return Query ordinal for the
     */
    protected TypeResolutions.ParamOrdinal queryParamOrdinal() {
        return DEFAULT;
    }
}
