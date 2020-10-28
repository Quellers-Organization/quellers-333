/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.spatial.index.fielddata.plain;

import org.apache.lucene.document.XYDocValuesField;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.SortField;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.IndexFieldData.XFieldComparatorSource.Nested;
import org.elasticsearch.index.fielddata.IndexFieldDataCache;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.MultiValueMode;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;
import org.elasticsearch.search.sort.BucketedSort;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xpack.spatial.index.fielddata.IndexPointFieldData;
import org.elasticsearch.xpack.spatial.index.fielddata.LeafPointFieldData;
import org.elasticsearch.xpack.spatial.search.aggregations.support.PointValuesSourceType;

public abstract class AbstractPointDVIndexFieldData implements IndexPointFieldData {

    protected final String name;
    protected final ValuesSourceType valuesSourceType;

    AbstractPointDVIndexFieldData(String name, ValuesSourceType valuesSourceType) {
        this.name = name;
        this.valuesSourceType = valuesSourceType;
    }

    @Override
    public final String getFieldName() {
        return name;
    }


    @Override
    public SortField sortField(@Nullable Object missingValue, MultiValueMode sortMode, Nested nested,
                               boolean reverse) {
        throw new IllegalArgumentException("can't sort on point field without using specific sorting feature, like distance");
    }

    @Override
    public BucketedSort newBucketedSort(BigArrays bigArrays, Object missingValue, MultiValueMode sortMode, Nested nested,
                                        SortOrder sortOrder, DocValueFormat format, int bucketSize, BucketedSort.ExtraData extra) {
        throw new IllegalArgumentException("can't sort on point field without using specific sorting feature, like distance");
    }

    @Override
    public ValuesSourceType getValuesSourceType() {
        return PointValuesSourceType.instance();
    }

    public static class CartesianPointDVIndexFieldData extends AbstractPointDVIndexFieldData {
        public CartesianPointDVIndexFieldData(String name, ValuesSourceType valuesSourceType) {
            super(name, valuesSourceType);
        }

        @Override
        public LeafPointFieldData load(LeafReaderContext context) {
            LeafReader reader = context.reader();
            FieldInfo info = reader.getFieldInfos().fieldInfo(name);
            if (info != null) {
                checkCompatible(info);
            }
            return new CartesianPointDVLeafFieldData(reader, name);
        }

        @Override
        public LeafPointFieldData loadDirect(LeafReaderContext context) throws Exception {
            return load(context);
        }

        /**
         * helper: checks a fieldinfo and throws exception if its definitely not a XYDocValuesField
         */
        static void checkCompatible(FieldInfo fieldInfo) {
            // dv properties could be "unset", if you e.g. used only StoredField with this same name in the segment.
            if (fieldInfo.getDocValuesType() != DocValuesType.NONE
                && fieldInfo.getDocValuesType() != XYDocValuesField.TYPE.docValuesType()) {
                throw new IllegalArgumentException("field=\"" + fieldInfo.name + "\" was indexed with docValuesType="
                    + fieldInfo.getDocValuesType() + " but this type has docValuesType="
                    + XYDocValuesField.TYPE.docValuesType() + ", is the field really a XYDocValuesField?");
            }
        }
    }

    public static class Builder implements IndexFieldData.Builder {

        private final String name;
        private final ValuesSourceType valuesSourceType;

        public Builder(String name, ValuesSourceType valuesSourceType) {
            this.name = name;
            this.valuesSourceType = valuesSourceType;
        }

        @Override
        public IndexFieldData<?> build(IndexFieldDataCache cache, CircuitBreakerService breakerService) {
            // ignore breaker
            return new CartesianPointDVIndexFieldData(name, valuesSourceType);
        }
    }
}
