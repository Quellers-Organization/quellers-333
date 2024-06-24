// Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
// or more contributor license agreements. Licensed under the Elastic License
// 2.0; you may not use this file except in compliance with the Elastic License
// 2.0.
package org.elasticsearch.xpack.esql.expression.function.scalar.spatial;

import java.lang.IllegalArgumentException;
import java.lang.Override;
import java.lang.String;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.compute.data.LongBlock;
import org.elasticsearch.compute.data.LongVector;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.geometry.Point;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.expression.function.Warnings;

/**
 * {@link EvalOperator.ExpressionEvaluator} implementation for {@link StDWithin}.
 * This class is generated. Do not edit it.
 */
public final class StDWithinGeoPointDocValuesAndConstantAndConstantEvaluator implements EvalOperator.ExpressionEvaluator {
  private final Warnings warnings;

  private final EvalOperator.ExpressionEvaluator leftValue;

  private final Point rightValue;

  private final double argValue;

  private final DriverContext driverContext;

  public StDWithinGeoPointDocValuesAndConstantAndConstantEvaluator(Source source,
      EvalOperator.ExpressionEvaluator leftValue, Point rightValue, double argValue,
      DriverContext driverContext) {
    this.leftValue = leftValue;
    this.rightValue = rightValue;
    this.argValue = argValue;
    this.driverContext = driverContext;
    this.warnings = Warnings.createWarnings(driverContext.warningsMode(), source);
  }

  @Override
  public Block eval(Page page) {
    try (LongBlock leftValueBlock = (LongBlock) leftValue.eval(page)) {
      LongVector leftValueVector = leftValueBlock.asVector();
      if (leftValueVector == null) {
        return eval(page.getPositionCount(), leftValueBlock);
      }
      return eval(page.getPositionCount(), leftValueVector);
    }
  }

  public BooleanBlock eval(int positionCount, LongBlock leftValueBlock) {
    try(BooleanBlock.Builder result = driverContext.blockFactory().newBooleanBlockBuilder(positionCount)) {
      position: for (int p = 0; p < positionCount; p++) {
        if (leftValueBlock.isNull(p)) {
          result.appendNull();
          continue position;
        }
        if (leftValueBlock.getValueCount(p) != 1) {
          if (leftValueBlock.getValueCount(p) > 1) {
            warnings.registerException(new IllegalArgumentException("single-value function encountered multi-value"));
          }
          result.appendNull();
          continue position;
        }
        try {
          result.appendBoolean(StDWithin.processGeoPointDocValuesAndConstant(leftValueBlock.getLong(leftValueBlock.getFirstValueIndex(p)), rightValue, argValue));
        } catch (IllegalArgumentException e) {
          warnings.registerException(e);
          result.appendNull();
        }
      }
      return result.build();
    }
  }

  public BooleanBlock eval(int positionCount, LongVector leftValueVector) {
    try(BooleanBlock.Builder result = driverContext.blockFactory().newBooleanBlockBuilder(positionCount)) {
      position: for (int p = 0; p < positionCount; p++) {
        try {
          result.appendBoolean(StDWithin.processGeoPointDocValuesAndConstant(leftValueVector.getLong(p), rightValue, argValue));
        } catch (IllegalArgumentException e) {
          warnings.registerException(e);
          result.appendNull();
        }
      }
      return result.build();
    }
  }

  @Override
  public String toString() {
    return "StDWithinGeoPointDocValuesAndConstantAndConstantEvaluator[" + "leftValue=" + leftValue + ", rightValue=" + rightValue + ", argValue=" + argValue + "]";
  }

  @Override
  public void close() {
    Releasables.closeExpectNoException(leftValue);
  }

  static class Factory implements EvalOperator.ExpressionEvaluator.Factory {
    private final Source source;

    private final EvalOperator.ExpressionEvaluator.Factory leftValue;

    private final Point rightValue;

    private final double argValue;

    public Factory(Source source, EvalOperator.ExpressionEvaluator.Factory leftValue,
        Point rightValue, double argValue) {
      this.source = source;
      this.leftValue = leftValue;
      this.rightValue = rightValue;
      this.argValue = argValue;
    }

    @Override
    public StDWithinGeoPointDocValuesAndConstantAndConstantEvaluator get(DriverContext context) {
      return new StDWithinGeoPointDocValuesAndConstantAndConstantEvaluator(source, leftValue.get(context), rightValue, argValue, context);
    }

    @Override
    public String toString() {
      return "StDWithinGeoPointDocValuesAndConstantAndConstantEvaluator[" + "leftValue=" + leftValue + ", rightValue=" + rightValue + ", argValue=" + argValue + "]";
    }
  }
}
