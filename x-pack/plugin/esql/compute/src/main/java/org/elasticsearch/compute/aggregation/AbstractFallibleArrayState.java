/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.aggregation;

import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.BitArray;
import org.elasticsearch.core.Releasables;

public class AbstractFallibleArrayState extends AbstractArrayState {
    private BitArray failed;

    public AbstractFallibleArrayState(BigArrays bigArrays) {
        super(bigArrays);
    }

    final boolean hasFailed(int groupId) {
        return failed != null && failed.get(groupId);
    }

    protected final boolean anyFailure() {
        return failed != null;
    }

    protected final void setFailed(int groupId) {
        if (failed == null) {
            failed = new BitArray(groupId + 1, bigArrays);
        }
        failed.set(groupId);
    }

    @Override
    public void close() {
        super.close();
        Releasables.close(failed);
    }
}
