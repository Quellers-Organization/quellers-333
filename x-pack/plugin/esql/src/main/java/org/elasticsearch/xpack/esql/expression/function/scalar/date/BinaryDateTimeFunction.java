/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.date;

import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.function.scalar.BinaryScalarFunction;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

public abstract class BinaryDateTimeFunction extends BinaryScalarFunction {

    protected static final ZoneId DEFAULT_TZ = ZoneOffset.UTC;

    private final ZoneId zoneId;

    protected BinaryDateTimeFunction(Source source, Expression argument, Expression timestamp) {
        super(source, argument, timestamp);
        zoneId = DEFAULT_TZ;
    }

    @Override
    public DataType dataType() {
        return DataType.DATETIME;
    }

    public Expression timestampField() {
        return right();
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), zoneId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (super.equals(o) == false) {
            return false;
        }
        BinaryDateTimeFunction that = (BinaryDateTimeFunction) o;
        return zoneId().equals(that.zoneId());
    }
}
