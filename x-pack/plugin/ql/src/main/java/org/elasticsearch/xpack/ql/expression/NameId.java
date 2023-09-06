/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ql.expression;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unique identifier for a named expression.
 * <p>
 * We use an {@link AtomicLong} to guarantee that they are unique
 * and that create reproducible values when run in subsequent
 * tests. They don't produce reproducible values in production, but
 * you rarely debug with them in production and commonly do so in
 * tests.
 */
public class NameId {
    private static final AtomicLong COUNTER = new AtomicLong();
    private final long id;

    public NameId() {
        this.id = COUNTER.incrementAndGet();
    }

    public NameId(long id) {
        this.id = id;
    }

    /**
     * TODO:
     * This is a workaround to prevent NameId conflicts in multi-node environments. Currently, when a NameId is serialized,
     * its local ID is also serialized to the remote node. If the remote node generates a new NameId, it could potentially
     * have the same ID as an existing one that was deserialized, leading to an incorrect local plan on the remote node.
     * <p>
     * It's important to note that while conflicts might still occur between multiple computations with this approach, there
     * is no conflicts within a single computation. We only need to ensure no conflict in a single computation to ensure the correctness.
     */
    public static void advanceGlobalId(long other) {
        COUNTER.accumulateAndGet(other, Math::max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        NameId other = (NameId) obj;
        return id == other.id;
    }

    @Override
    public String toString() {
        return Long.toString(id);
    }
}
