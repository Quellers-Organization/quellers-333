/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.downsample;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.index.fielddata.HistogramValue;
import org.elasticsearch.index.fielddata.HistogramValues;
import org.elasticsearch.index.fielddata.LeafFieldData;
import org.elasticsearch.index.fielddata.LeafHistogramFieldData;
import org.elasticsearch.index.fielddata.SortedBinaryDocValues;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.NumberFieldMapper;
import org.elasticsearch.script.field.DocValuesScriptFieldFactory;
import org.elasticsearch.search.aggregations.AggregatorTestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class LabelFieldProducerTests extends AggregatorTestCase {

    public void testLastValueKeywordLabel() {
        final LabelFieldProducer.Label label = new LabelFieldProducer.LastValueLabel();
        label.collect("aaa");
        label.collect("bbb");
        label.collect("ccc");
        assertEquals("aaa", label.get());
        label.reset();
        assertNull(label.get());
    }

    public void testLastValueDoubleLabel() {
        final LabelFieldProducer.Label label = new LabelFieldProducer.LastValueLabel();
        label.collect(10.20D);
        label.collect(17.30D);
        label.collect(12.60D);
        assertEquals(10.20D, label.get());
        label.reset();
        assertNull(label.get());
    }

    public void testLastValueIntegerLabel() {
        final LabelFieldProducer.Label label = new LabelFieldProducer.LastValueLabel();
        label.collect(10);
        label.collect(17);
        label.collect(12);
        assertEquals(10, label.get());
        label.reset();
        assertNull(label.get());
    }

    public void testLastValueLongLabel() {
        final LabelFieldProducer.Label label = new LabelFieldProducer.LastValueLabel();
        label.collect(10L);
        label.collect(17L);
        label.collect(12L);
        assertEquals(10L, label.get());
        label.reset();
        assertNull(label.get());
    }

    public void testLastValueBooleanLabel() {
        final LabelFieldProducer.Label label = new LabelFieldProducer.LastValueLabel();
        label.collect(true);
        label.collect(false);
        label.collect(true);
        assertEquals(true, label.get());
        label.reset();
        assertNull(label.get());
    }

    public void testHistogramLabelFieldProducer() throws IOException {
        final MappedFieldType fieldType = new HistogramFieldMapper.HistogramFieldType("histogram", Collections.emptyMap());
        final LabelFieldProducer producer = new LabelFieldProducer.HistogramLabelFieldProducer(fieldType, "dummy");
        assertTrue(producer.isEmpty());
        assertEquals("dummy", producer.name());
        assertEquals("last_value", producer.label().name());
        double[] histogramValues = new double[] { 1.0, 2.0, 3.0 };
        int[] histogramCounts = new int[] { 10, 20, 30 };
        final LeafHistogramFieldData histogramFieldData = new LeafHistogramFieldData() {
            @Override
            public HistogramValues getHistogramValues() {
                return new HistogramValues() {
                    @Override
                    public boolean advanceExact(int doc) {
                        return true;
                    }

                    @Override
                    public HistogramValue histogram() {
                        return new HistogramValue() {
                            int current = -1;

                            @Override
                            public boolean next() {
                                boolean hasNext = current < histogramCounts.length;
                                current++;
                                return hasNext;
                            }

                            @Override
                            public double value() {
                                return histogramValues[current];
                            }

                            @Override
                            public int count() {
                                return histogramCounts[current];
                            }
                        };
                    }
                };
            }

            @Override
            public DocValuesScriptFieldFactory getScriptFieldFactory(String name) {
                throw new UnsupportedOperationException(name);
            }

            @Override
            public SortedBinaryDocValues getBytesValues() {
                throw new UnsupportedOperationException();
            }

            @Override
            public long ramBytesUsed() {
                return 0;
            }

            @Override
            public void close() {}
        };

        producer.collect(histogramFieldData, 0);
        assertFalse(producer.isEmpty());
        HistogramValue histogramValue = (HistogramValue) producer.label().get();
        for (int i = 0; i < histogramCounts.length; i++) {
            assertTrue(histogramValue.next());
            assertEquals(histogramValues[i], histogramValue.value(), 0.1);
            assertEquals(histogramCounts[i], histogramValue.count());
        }
        producer.reset();
        assertTrue(producer.isEmpty());
        assertNull(producer.label().get());
    }

    public void testLabelFieldProducer() throws IOException {
        final MappedFieldType fieldType = new NumberFieldMapper.NumberFieldType("number", NumberFieldMapper.NumberType.LONG);
        final LabelFieldProducer producer = new LabelFieldProducer.LabelLastValueFieldProducer(fieldType, "dummy");
        assertTrue(producer.isEmpty());
        assertEquals("dummy", producer.name());
        assertEquals("last_value", producer.label().name());
        LeafFieldData leafFieldData = new LeafFieldData() {
            @Override
            public DocValuesScriptFieldFactory getScriptFieldFactory(String name) {
                throw new UnsupportedOperationException(name);
            }

            @Override
            public SortedBinaryDocValues getBytesValues() {
                return new SortedBinaryDocValues() {
                    @Override
                    public boolean advanceExact(int doc) {
                        return true;
                    }

                    @Override
                    public int docValueCount() {
                        return 1;
                    }

                    @Override
                    public BytesRef nextValue() {
                        return new BytesRef("dummy".getBytes(StandardCharsets.UTF_8));
                    }
                };
            }

            @Override
            public long ramBytesUsed() {
                return 0;
            }

            @Override
            public void close() {

            }
        };
        producer.collect(leafFieldData, 1);
        assertFalse(producer.isEmpty());
        assertEquals("dummy", producer.label().get());
        producer.reset();
        assertTrue(producer.isEmpty());
        assertNull(producer.label().get());
    }
}
