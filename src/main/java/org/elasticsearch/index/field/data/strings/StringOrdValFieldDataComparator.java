/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.field.data.strings;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.index.cache.field.data.FieldDataCache;
import org.elasticsearch.index.field.data.FieldData;
import org.elasticsearch.index.field.data.FieldDataType;

import java.io.IOException;

/**
 *
 */
// LUCENE MONITOR: Monitor against FieldComparator#String
public class StringOrdValFieldDataComparator extends FieldComparator<BytesRef> {

    private final FieldDataCache fieldDataCache;

    private final int[] ords;
    private final BytesRef[] values;
    private final int[] readerGen;

    private int currentReaderGen = -1;
    private BytesRef[] lookup;
    private int[] order;
    private final String field;

    private int bottomSlot = -1;
    private int bottomOrd;
    private boolean bottomSameReader;
    private BytesRef bottomValue;

    public StringOrdValFieldDataComparator(int numHits, String field, int sortPos, boolean reversed, FieldDataCache fieldDataCache) {
        this.fieldDataCache = fieldDataCache;
        ords = new int[numHits];
        values = new BytesRef[numHits];
        readerGen = new int[numHits];
        this.field = field;
    }

    @Override
    public int compare(int slot1, int slot2) {
        if (readerGen[slot1] == readerGen[slot2]) {
            return ords[slot1] - ords[slot2];
        }

        final BytesRef val1 = values[slot1];
        final BytesRef val2 = values[slot2];
        if (val1 == null) {
            if (val2 == null) {
                return 0;
            }
            return -1;
        } else if (val2 == null) {
            return 1;
        }
        return val1.compareTo(val2);
    }

    @Override
    public int compareBottom(int doc) {
        assert bottomSlot != -1;
        if (bottomSameReader) {
            // ord is precisely comparable, even in the equal case
            return bottomOrd - this.order[doc];
        } else {
            // ord is only approx comparable: if they are not
            // equal, we can use that; if they are equal, we
            // must fallback to compare by value
            final int order = this.order[doc];
            final int cmp = bottomOrd - order;
            if (cmp != 0) {
                return cmp;
            }

            final BytesRef val2 = lookup[order];
            if (bottomValue == null) {
                if (val2 == null) {
                    return 0;
                }
                // bottom wins
                return -1;
            } else if (val2 == null) {
                // doc wins
                return 1;
            }
            return bottomValue.compareTo(val2);
        }
    }

    @Override
    public void copy(int slot, int doc) {
        final int ord = order[doc];
        ords[slot] = ord;
        assert ord >= 0;
        values[slot] = lookup[ord];
        readerGen[slot] = currentReaderGen;
    }

    @Override
    public FieldComparator<BytesRef> setNextReader(AtomicReaderContext context) throws IOException {
        FieldData cleanFieldData = fieldDataCache.cache(FieldDataType.DefaultTypes.STRING, context.reader(), field);
        if (cleanFieldData instanceof MultiValueStringFieldData) {
            throw new IOException("Can't sort on string types with more than one value per doc, or more than one token per field");
        }
        SingleValueStringFieldData fieldData = (SingleValueStringFieldData) cleanFieldData;
        currentReaderGen++;
        order = fieldData.ordinals();
        lookup = fieldData.values();
        assert lookup.length > 0;
        if (bottomSlot != -1) {
            setBottom(bottomSlot);
        }
        return this;
    }

    @Override
    public int compareDocToValue(int doc, BytesRef otherVal) throws IOException {
        BytesRef val = values[ords[doc]];
        if (otherVal == null) {
            if (val == null) {
                return 0;
            }
            return -1;
        } else if (val == null) {
            return 1;
        }
        return val.compareTo(otherVal);
    }

    @Override
    public void setBottom(final int bottom) {
        bottomSlot = bottom;

        bottomValue = values[bottomSlot];
        if (currentReaderGen == readerGen[bottomSlot]) {
            bottomOrd = ords[bottomSlot];
            bottomSameReader = true;
        } else {
            if (bottomValue == null) {
                ords[bottomSlot] = 0;
                bottomOrd = 0;
                bottomSameReader = true;
                readerGen[bottomSlot] = currentReaderGen;
            } else {
                final int index = binarySearch(bottomValue, lookup);
                if (index < 0) {
                    bottomOrd = -index - 2;
                    bottomSameReader = false;
                } else {
                    bottomOrd = index;
                    // exact value match
                    bottomSameReader = true;
                    readerGen[bottomSlot] = currentReaderGen;
                    ords[bottomSlot] = bottomOrd;
                }
            }
        }
    }

    private static int binarySearch(BytesRef value, BytesRef[] values) {
        return binarySearch(value, values, 1, values.length-1);
    }

    private static int binarySearch(BytesRef value, BytesRef[] values, int low, int high) {
        while (low <= high) {
            int mid = (low + high) >>> 1;
            BytesRef midVal = values[mid];
            int cmp;
            if (midVal != null) {
                cmp = midVal.compareTo(value);
            } else {
                cmp = -1;
            }

            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid;
        }
        return -(low + 1);
    }

    @Override
    public BytesRef value(int slot) {
        return values[slot];
    }

    public BytesRef[] getValues() {
        return values;
    }

    public int getBottomSlot() {
        return bottomSlot;
    }

    public String getField() {
        return field;
    }

}
