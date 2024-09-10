/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.tdigest.arrays;

import java.util.Arrays;

/**
 * Temporal TDigestArrays mock with raw arrays.
 * <p>
 *     For testing only, delete after the right implementation for BigArrays is made.
 * </p>
 */
public class WrapperTDigestArrays implements TDigestArrays {

    public static final WrapperTDigestArrays INSTANCE = new WrapperTDigestArrays();

    private WrapperTDigestArrays() {
    }

    @Override
    public TDigestDoubleArray newDoubleArray(int initialCapacity) {
        return new WrapperTDigestDoubleArray(initialCapacity);
    }

    @Override
    public TDigestIntArray newIntArray(int initialSize) {
        return new WrapperTDigestIntArray(initialSize);
    }

    public static class WrapperTDigestDoubleArray implements TDigestDoubleArray {
        private double[] array;
        private int size;

        public WrapperTDigestDoubleArray(int initialSize) {
            this.array = new double[initialSize];
            this.size = initialSize;
        }

        public WrapperTDigestDoubleArray(double[] array) {
            this.array = array;
            this.size = array.length;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public double get(int index) {
            assert index >= 0 && index < size;
            return array[index];
        }

        @Override
        public void set(int index, double value) {
            assert index >= 0 && index < size;
            array[index] = value;
        }

        @Override
        public void set(int index, TDigestDoubleArray buf, int offset, int len) {
            assert index >= 0 && index + len <= size;
            for (int i = 0; i < len; i++) {
                array[index + i] = buf.get(offset + i);
            }
        }

        @Override
        public void add(double value) {
            ensureCapacity(size + 1);
            array[size++] = value;
        }

        @Override
        public void sorted() {
            Arrays.sort(array, 0, size);
        }

        @Override
        public void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity > array.length) {
                double[] newArray = new double[requiredCapacity];
                System.arraycopy(array, 0, newArray, 0, size);
                array = newArray;
            }
        }
    }

    public static class WrapperTDigestIntArray implements TDigestIntArray {
        private final int[] array;

        public WrapperTDigestIntArray(int initialSize) {
            this.array = new int[initialSize];
        }

        public WrapperTDigestIntArray(int[] array) {
            this.array = array;
        }

        @Override
        public int size() {
            return array.length;
        }

        @Override
        public int get(int index) {
            assert index >= 0 && index < array.length;
            return array[index];
        }

        @Override
        public void set(int index, int value) {
            assert index >= 0 && index < array.length;
            array[index] = value;
        }

        @Override
        public void set(int index, TDigestIntArray buf, int offset, int len) {
            assert index >= 0 && index + len <= array.length;
            for (int i = len - 1; i >= 0; i--) {
                array[index + i] = buf.get(offset + i);
            }
        }
    }
}
