/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.aggs.correlation;

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.test.AbstractSerializingTestCase;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.stream.Stream;

public class CorrelativeValueTests extends AbstractSerializingTestCase<CorrelativeValue> {

    public static CorrelativeValue randomInstance() {
        double[] expectations = Stream.generate(ESTestCase::randomDouble)
            .limit(randomIntBetween(5, 100))
            .mapToDouble(Double::doubleValue).toArray();
        double[] fractions = Stream.generate(ESTestCase::randomDouble)
            .limit(expectations.length)
            .mapToDouble(Double::doubleValue).toArray();
        return new CorrelativeValue(expectations, randomBoolean() ? null : fractions, randomLongBetween(1, Long.MAX_VALUE - 1));
    }

    @Override
    protected CorrelativeValue doParseInstance(XContentParser parser) throws IOException {
        return CorrelativeValue.fromXContent(parser);
    }

    @Override
    protected Writeable.Reader<CorrelativeValue> instanceReader() {
        return CorrelativeValue::new;
    }

    @Override
    protected CorrelativeValue createTestInstance() {
        return randomInstance();
    }
}
