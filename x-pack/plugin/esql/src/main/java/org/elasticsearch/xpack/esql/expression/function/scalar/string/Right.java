/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.string;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;
import org.elasticsearch.compute.ann.Evaluator;
import org.elasticsearch.compute.ann.Fixed;
import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.xpack.esql.evaluator.mapper.EvaluatorMapper;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.function.scalar.ScalarFunction;
import org.elasticsearch.xpack.ql.expression.gen.script.ScriptTemplate;
import org.elasticsearch.xpack.ql.tree.NodeInfo;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.elasticsearch.xpack.ql.expression.TypeResolutions.ParamOrdinal.FIRST;
import static org.elasticsearch.xpack.ql.expression.TypeResolutions.ParamOrdinal.SECOND;
import static org.elasticsearch.xpack.ql.expression.TypeResolutions.isInteger;
import static org.elasticsearch.xpack.ql.expression.TypeResolutions.isString;

/**
 * {code right(foo, len)} is an alias to {code substring(foo, foo.length-len, len)}
 */
public class Right extends ScalarFunction implements EvaluatorMapper {

    private final Source source;

    private final Expression str;

    private final Expression length;

    public Right(Source source, Expression str, Expression length) {
        super(source, Arrays.asList(str, length));
        this.source = source;
        this.str = str;
        this.length = length;
    }

    @Evaluator
    static BytesRef process(
        @Fixed(includeInToString = false) BytesRef out,
        @Fixed(includeInToString = false) UnicodeUtil.UTF8CodePoint cp,
        BytesRef str,
        int length
    ) {
        out.bytes = str.bytes;
        out.offset = str.offset;
        out.length = str.length;
        int codeLen = UnicodeUtil.codePointCount(str);
        // skip the first skipLen codePoint
        int skipLen = Math.max(codeLen - length, 0);
        int endOffset = str.offset + str.length;
        for (int i = 0; i < skipLen && out.offset < endOffset; i++) {
            UnicodeUtil.codePointAt(out.bytes, out.offset, cp);
            out.offset += cp.numBytes;
            out.length -= cp.numBytes;
        }
        return out;
    }

    @Override
    public Supplier<EvalOperator.ExpressionEvaluator> toEvaluator(
        Function<Expression, Supplier<EvalOperator.ExpressionEvaluator>> toEvaluator
    ) {

        Supplier<EvalOperator.ExpressionEvaluator> strSupplier = toEvaluator.apply(str);
        Supplier<EvalOperator.ExpressionEvaluator> lengthSupplier = toEvaluator.apply(length);
        return () -> {
            BytesRef out = new BytesRef();
            UnicodeUtil.UTF8CodePoint cp = new UnicodeUtil.UTF8CodePoint();
            return new RightEvaluator(out, cp, strSupplier.get(), lengthSupplier.get());
        };
    }

    @Override
    public Expression replaceChildren(List<Expression> newChildren) {
        return new Right(source(), newChildren.get(0), newChildren.get(1));
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, Right::new, str, length);
    }

    @Override
    public DataType dataType() {
        return DataTypes.KEYWORD;
    }

    @Override
    protected TypeResolution resolveType() {
        if (childrenResolved() == false) {
            return new TypeResolution("Unresolved children");
        }

        TypeResolution resolution = isString(str, sourceText(), FIRST);
        if (resolution.unresolved()) {
            return resolution;
        }

        resolution = isInteger(length, sourceText(), SECOND);
        if (resolution.unresolved()) {
            return resolution;
        }

        return TypeResolution.TYPE_RESOLVED;
    }

    @Override
    public boolean foldable() {
        return str.foldable() && length.foldable();
    }

    @Override
    public Object fold() {
        return EvaluatorMapper.super.fold();
    }

    @Override
    public ScriptTemplate asScript() {
        throw new UnsupportedOperationException();
    }
}
