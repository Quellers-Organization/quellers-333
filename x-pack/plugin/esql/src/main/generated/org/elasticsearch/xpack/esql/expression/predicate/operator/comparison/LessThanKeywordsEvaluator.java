// Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
// or more contributor license agreements. Licensed under the Elastic License
// 2.0; you may not use this file except in compliance with the Elastic License
// 2.0.
package org.elasticsearch.xpack.esql.expression.predicate.operator.comparison;

import java.lang.IllegalArgumentException;
import java.lang.Override;
import java.lang.String;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.compute.data.BooleanVector;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.data.BytesRefVector;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.expression.function.Warnings;

/**
 * {@link EvalOperator.ExpressionEvaluator} implementation for {@link LessThan}.
 * This class is generated. Do not edit it.
 */
public final class LessThanKeywordsEvaluator implements EvalOperator.ExpressionEvaluator {
  private final Warnings warnings;

  private final EvalOperator.ExpressionEvaluator lhs;

  private final EvalOperator.ExpressionEvaluator rhs;

  private final DriverContext driverContext;

  public LessThanKeywordsEvaluator(Source source, EvalOperator.ExpressionEvaluator lhs,
      EvalOperator.ExpressionEvaluator rhs, DriverContext driverContext) {
    this.warnings = new Warnings(source);
    this.lhs = lhs;
    this.rhs = rhs;
    this.driverContext = driverContext;
  }

  @Override
  public Block eval(Page page) {
    try (BytesRefBlock lhsBlock = (BytesRefBlock) lhs.eval(page)) {
      try (BytesRefBlock rhsBlock = (BytesRefBlock) rhs.eval(page)) {
        BytesRefVector lhsVector = lhsBlock.asVector();
        if (lhsVector == null) {
          return eval(page.getPositionCount(), lhsBlock, rhsBlock);
        }
        BytesRefVector rhsVector = rhsBlock.asVector();
        if (rhsVector == null) {
          return eval(page.getPositionCount(), lhsBlock, rhsBlock);
        }
        return eval(page.getPositionCount(), lhsVector, rhsVector).asBlock();
      }
    }
  }

  public BooleanBlock eval(int positionCount, BytesRefBlock lhsBlock, BytesRefBlock rhsBlock) {
    try(BooleanBlock.Builder result = driverContext.blockFactory().newBooleanBlockBuilder(positionCount)) {
      BytesRef lhsScratch = new BytesRef();
      BytesRef rhsScratch = new BytesRef();
      position: for (int p = 0; p < positionCount; p++) {
        if (lhsBlock.isNull(p)) {
          result.appendNull();
          continue position;
        }
        if (lhsBlock.getValueCount(p) != 1) {
          if (lhsBlock.getValueCount(p) > 1) {
            warnings.registerException(new IllegalArgumentException("single-value function encountered multi-value"));
          }
          result.appendNull();
          continue position;
        }
        if (rhsBlock.isNull(p)) {
          result.appendNull();
          continue position;
        }
        if (rhsBlock.getValueCount(p) != 1) {
          if (rhsBlock.getValueCount(p) > 1) {
            warnings.registerException(new IllegalArgumentException("single-value function encountered multi-value"));
          }
          result.appendNull();
          continue position;
        }
        result.appendBoolean(LessThan.processKeywords(lhsBlock.getBytesRef(lhsBlock.getFirstValueIndex(p), lhsScratch), rhsBlock.getBytesRef(rhsBlock.getFirstValueIndex(p), rhsScratch)));
      }
      return result.build();
    }
  }

  public BooleanVector eval(int positionCount, BytesRefVector lhsVector, BytesRefVector rhsVector) {
    try(BooleanVector.Builder result = driverContext.blockFactory().newBooleanVectorBuilder(positionCount)) {
      BytesRef lhsScratch = new BytesRef();
      BytesRef rhsScratch = new BytesRef();
      position: for (int p = 0; p < positionCount; p++) {
        result.appendBoolean(LessThan.processKeywords(lhsVector.getBytesRef(p, lhsScratch), rhsVector.getBytesRef(p, rhsScratch)));
      }
      return result.build();
    }
  }

  @Override
  public String toString() {
    return "LessThanKeywordsEvaluator[" + "lhs=" + lhs + ", rhs=" + rhs + "]";
  }

  @Override
  public void close() {
    Releasables.closeExpectNoException(lhs, rhs);
  }

  static class Factory implements EvalOperator.ExpressionEvaluator.Factory {
    private final Source source;

    private final EvalOperator.ExpressionEvaluator.Factory lhs;

    private final EvalOperator.ExpressionEvaluator.Factory rhs;

    public Factory(Source source, EvalOperator.ExpressionEvaluator.Factory lhs,
        EvalOperator.ExpressionEvaluator.Factory rhs) {
      this.source = source;
      this.lhs = lhs;
      this.rhs = rhs;
    }

    @Override
    public LessThanKeywordsEvaluator get(DriverContext context) {
      return new LessThanKeywordsEvaluator(source, lhs.get(context), rhs.get(context), context);
    }

    @Override
    public String toString() {
      return "LessThanKeywordsEvaluator[" + "lhs=" + lhs + ", rhs=" + rhs + "]";
    }
  }
}
