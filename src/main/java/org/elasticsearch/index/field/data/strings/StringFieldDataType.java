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

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.SortField;
import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.index.cache.field.data.FieldDataCache;
import org.elasticsearch.index.field.data.FieldDataType;

import java.io.IOException;

/**
 *
 */
public class StringFieldDataType implements FieldDataType<StringFieldData> {

    @Override
    public ExtendedFieldComparatorSource newFieldComparatorSource(final FieldDataCache cache, final String missing) {
        if (missing != null) {
            throw new ElasticSearchIllegalArgumentException("Sorting on string type field does not support missing parameter");
        }
        return new ExtendedFieldComparatorSource() {
            @Override
            public FieldComparator newComparator(String fieldname, int numHits, int sortPos, boolean reversed) throws IOException {
                return new StringOrdValFieldDataComparator(numHits, fieldname, sortPos, reversed, cache);
            }

            @Override
            public SortField.Type reducedType() {
                return SortField.Type.STRING;
            }
        };
    }

    @Override
    public StringFieldData load(AtomicReader reader, String fieldName) throws IOException {
        return StringFieldData.load(reader, fieldName);
    }
}
