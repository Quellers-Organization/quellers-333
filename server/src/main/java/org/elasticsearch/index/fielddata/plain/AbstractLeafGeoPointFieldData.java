/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.index.fielddata.plain;

import org.apache.lucene.util.Accountable;
import org.elasticsearch.index.fielddata.FieldData;
import org.elasticsearch.index.fielddata.LeafGeoPointFieldData;
import org.elasticsearch.index.fielddata.MultiGeoPointValues;
import org.elasticsearch.index.fielddata.SortedBinaryDocValues;
import org.elasticsearch.script.field.DocValuesScriptFieldSource;
import org.elasticsearch.script.field.ToScriptFieldSource;

import java.util.Collection;
import java.util.Collections;

public abstract class AbstractLeafGeoPointFieldData implements LeafGeoPointFieldData {

    protected final ToScriptFieldSource<MultiGeoPointValues> toScriptFieldSource;

    public AbstractLeafGeoPointFieldData(ToScriptFieldSource<MultiGeoPointValues> toScriptFieldSource) {
        this.toScriptFieldSource = toScriptFieldSource;
    }

    @Override
    public final SortedBinaryDocValues getBytesValues() {
        return FieldData.toString(getGeoPointValues());
    }

    @Override
    public DocValuesScriptFieldSource getScriptFieldSource(String name) {
        return toScriptFieldSource.getScriptFieldSource(getGeoPointValues(), name);
    }

    public static LeafGeoPointFieldData empty(final int maxDoc, ToScriptFieldSource<MultiGeoPointValues> toScriptFieldSource) {
        return new AbstractLeafGeoPointFieldData(toScriptFieldSource) {

            @Override
            public long ramBytesUsed() {
                return 0;
            }

            @Override
            public Collection<Accountable> getChildResources() {
                return Collections.emptyList();
            }

            @Override
            public void close() {}

            @Override
            public MultiGeoPointValues getGeoPointValues() {
                return FieldData.emptyMultiGeoPoints();
            }
        };
    }
}
