// Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
// or more contributor license agreements. Licensed under the Elastic License
// 2.0; you may not use this file except in compliance with the Elastic License
// 2.0.
package org.elasticsearch.compute.aggregation;

import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.util.List;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.data.BytesRefVector;
import org.elasticsearch.compute.data.ElementType;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.DriverContext;

/**
 * {@link AggregatorFunction} implementation for {@link ValuesBytesRefAggregator}.
 * This class is generated. Do not edit it.
 */
public final class ValuesBytesRefAggregatorFunction implements AggregatorFunction {
  private static final List<IntermediateStateDesc> INTERMEDIATE_STATE_DESC = List.of(
      new IntermediateStateDesc("values", ElementType.BYTES_REF)  );

  private final DriverContext driverContext;

  private final ValuesBytesRefAggregator.SingleState state;

  private final List<Integer> channels;

  public ValuesBytesRefAggregatorFunction(DriverContext driverContext, List<Integer> channels,
      ValuesBytesRefAggregator.SingleState state) {
    this.driverContext = driverContext;
    this.channels = channels;
    this.state = state;
  }

  public static ValuesBytesRefAggregatorFunction create(DriverContext driverContext,
      List<Integer> channels) {
    return new ValuesBytesRefAggregatorFunction(driverContext, channels, ValuesBytesRefAggregator.initSingle(driverContext.bigArrays()));
  }

  public static List<IntermediateStateDesc> intermediateStateDesc() {
    return INTERMEDIATE_STATE_DESC;
  }

  @Override
  public int intermediateBlockCount() {
    return INTERMEDIATE_STATE_DESC.size();
  }

  @Override
  public void addRawInput(Page page) {
    BytesRefBlock block = page.getBlock(channels.get(0));
    BytesRefVector vector = block.asVector();
    if (vector != null) {
      addRawVector(vector);
    } else {
      addRawBlock(block);
    }
  }

  private void addRawVector(BytesRefVector vector) {
    BytesRef scratch = new BytesRef();
    for (int i = 0; i < vector.getPositionCount(); i++) {
      ValuesBytesRefAggregator.combine(state, vector.getBytesRef(i, scratch));
    }
  }

  private void addRawBlock(BytesRefBlock block) {
    BytesRef scratch = new BytesRef();
    for (int p = 0; p < block.getPositionCount(); p++) {
      if (block.isNull(p)) {
        continue;
      }
      int start = block.getFirstValueIndex(p);
      int end = start + block.getValueCount(p);
      for (int i = start; i < end; i++) {
        ValuesBytesRefAggregator.combine(state, block.getBytesRef(i, scratch));
      }
    }
  }

  @Override
  public void addIntermediateInput(Page page) {
    assert channels.size() == intermediateBlockCount();
    assert page.getBlockCount() >= channels.get(0) + intermediateStateDesc().size();
    Block valuesUncast = page.getBlock(channels.get(0));
    if (valuesUncast.areAllValuesNull()) {
      return;
    }
    BytesRefBlock values = (BytesRefBlock) valuesUncast;
    assert values.getPositionCount() == 1;
    BytesRef scratch = new BytesRef();
    ValuesBytesRefAggregator.combineIntermediate(state, values);
  }

  @Override
  public void evaluateIntermediate(Block[] blocks, int offset, DriverContext driverContext) {
    state.toIntermediate(blocks, offset, driverContext);
  }

  @Override
  public void evaluateFinal(Block[] blocks, int offset, DriverContext driverContext) {
    blocks[offset] = ValuesBytesRefAggregator.evaluateFinal(state, driverContext);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getSimpleName()).append("[");
    sb.append("channels=").append(channels);
    sb.append("]");
    return sb.toString();
  }

  @Override
  public void close() {
    state.close();
  }
}
