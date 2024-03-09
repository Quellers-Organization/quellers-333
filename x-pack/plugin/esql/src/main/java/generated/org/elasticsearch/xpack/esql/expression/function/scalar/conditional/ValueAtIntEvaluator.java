// Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
// or more contributor license agreements. Licensed under the Elastic License
// 2.0; you may not use this file except in compliance with the Elastic License
// 2.0.
package org.elasticsearch.xpack.esql.expression.function.scalar.conditional;

import java.lang.IllegalArgumentException;
import java.lang.Override;
import java.lang.String;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.IntBlock;
import org.elasticsearch.compute.data.IntVector;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.xpack.esql.expression.function.Warnings;
import org.elasticsearch.xpack.ql.tree.Source;

/**
 * {@link EvalOperator.ExpressionEvaluator} implementation for {@link ValueAt}.
 * This class is generated. Do not edit it.
 */
public final class ValueAtIntEvaluator implements EvalOperator.ExpressionEvaluator {
  private final Warnings warnings;

  private final EvalOperator.ExpressionEvaluator index;

  private final IntBlock copyFrom;

  private final DriverContext driverContext;

  public ValueAtIntEvaluator(Source source, EvalOperator.ExpressionEvaluator index,
      IntBlock copyFrom, DriverContext driverContext) {
    this.warnings = new Warnings(source);
    this.index = index;
    this.copyFrom = copyFrom;
    this.driverContext = driverContext;
  }

  @Override
  public Block eval(Page page) {
    try (IntBlock indexBlock = (IntBlock) index.eval(page)) {
      IntVector indexVector = indexBlock.asVector();
      if (indexVector == null) {
        return eval(page.getPositionCount(), indexBlock);
      }
      return eval(page.getPositionCount(), indexVector);
    }
  }

  public IntBlock eval(int positionCount, IntBlock indexBlock) {
    try(IntBlock.Builder result = driverContext.blockFactory().newIntBlockBuilder(positionCount)) {
      position: for (int p = 0; p < positionCount; p++) {
        if (indexBlock.isNull(p)) {
          result.appendNull();
          continue position;
        }
        if (indexBlock.getValueCount(p) != 1) {
          if (indexBlock.getValueCount(p) > 1) {
            warnings.registerException(new IllegalArgumentException("single-value function encountered multi-value"));
          }
          result.appendNull();
          continue position;
        }
        ValueAt.processInt(result, indexBlock.getInt(indexBlock.getFirstValueIndex(p)), copyFrom);
      }
      return result.build();
    }
  }

  public IntBlock eval(int positionCount, IntVector indexVector) {
    try(IntBlock.Builder result = driverContext.blockFactory().newIntBlockBuilder(positionCount)) {
      position: for (int p = 0; p < positionCount; p++) {
        ValueAt.processInt(result, indexVector.getInt(p), copyFrom);
      }
      return result.build();
    }
  }

  @Override
  public String toString() {
    return "ValueAtIntEvaluator[" + "index=" + index + ", copyFrom=" + copyFrom + "]";
  }

  @Override
  public void close() {
    Releasables.closeExpectNoException(index, copyFrom);
  }

  static class Factory implements EvalOperator.ExpressionEvaluator.Factory {
    private final Source source;

    private final EvalOperator.ExpressionEvaluator.Factory index;

    private final IntBlock copyFrom;

    public Factory(Source source, EvalOperator.ExpressionEvaluator.Factory index,
        IntBlock copyFrom) {
      this.source = source;
      this.index = index;
      this.copyFrom = copyFrom;
    }

    @Override
    public ValueAtIntEvaluator get(DriverContext context) {
      return new ValueAtIntEvaluator(source, index.get(context), copyFrom, context);
    }

    @Override
    public String toString() {
      return "ValueAtIntEvaluator[" + "index=" + index + ", copyFrom=" + copyFrom + "]";
    }
  }
}
