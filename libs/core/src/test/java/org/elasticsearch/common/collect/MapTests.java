/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.collect;

import org.elasticsearch.test.ESTestCase;

import static org.hamcrest.CoreMatchers.equalTo;

public class MapTests extends ESTestCase {

    private static final String[] numbers = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};

    public void testMapOfOne() {
        final java.util.Map<?, ?> map = Map.of(numbers[0], 0);
        validateMapContents(map, 1);
    }

    public void testMapOfTwo() {
        final java.util.Map<?, ?> map = Map.of(numbers[0], 0, numbers[1], 1);
        validateMapContents(map, 2);
    }

    public void testMapOfThree() {
        final java.util.Map<?, ?> map = Map.of(numbers[0], 0, numbers[1], 1, numbers[2], 2);
        validateMapContents(map, 3);
    }

    public void testMapOfFour() {
        final java.util.Map<?, ?> map = Map.of(numbers[0], 0, numbers[1], 1, numbers[2], 2, numbers[3], 3);
        validateMapContents(map, 4);
    }

    public void testMapOfFive() {
        final java.util.Map<?, ?> map = Map.of(numbers[0], 0, numbers[1], 1, numbers[2], 2, numbers[3], 3,
            numbers[4], 4);
        validateMapContents(map, 5);
    }

    public void testMapOfSix() {
        final java.util.Map<?, ?> map = Map.of(numbers[0], 0, numbers[1], 1, numbers[2], 2, numbers[3], 3,
            numbers[4], 4, numbers[5], 5);
        validateMapContents(map, 6);
    }

    public void testMapOfSeven() {
        final java.util.Map<?, ?> map = Map.of(numbers[0], 0, numbers[1], 1, numbers[2], 2, numbers[3], 3,
            numbers[4], 4, numbers[5], 5, numbers[6], 6);
        validateMapContents(map, 7);
    }

    public void testMapOfEight() {
        final java.util.Map<?, ?> map = Map.of(numbers[0], 0, numbers[1], 1, numbers[2], 2, numbers[3], 3,
            numbers[4], 4, numbers[5], 5, numbers[6], 6, numbers[7], 7);
        validateMapContents(map, 8);
    }

    public void testMapOfNine() {
        final java.util.Map<?, ?> map = Map.of(numbers[0], 0, numbers[1], 1, numbers[2], 2, numbers[3], 3,
            numbers[4], 4, numbers[5], 5, numbers[6], 6, numbers[7], 7, numbers[8], 8);
        validateMapContents(map, 9);
    }

    public void testMapOfTen() {
        final java.util.Map<?, ?> map = Map.of(numbers[0], 0, numbers[1], 1, numbers[2], 2, numbers[3], 3,
            numbers[4], 4, numbers[5], 5, numbers[6], 6, numbers[7], 7, numbers[8], 8, numbers[9], 9);
        validateMapContents(map, 10);
    }

    private static void validateMapContents(java.util.Map<?,?> map, int size) {
        assertThat(map.size(), equalTo(size));
        for (int k = 0; k < map.size(); k++) {
            assertEquals(Integer.class, map.get(numbers[k]).getClass());
            assertEquals(k, map.get(numbers[k]));
        }
    }

    public void testMapIsImmutable() {
        java.util.Map<?,?> map = Map.of(new Object(), new Object());
        UnsupportedOperationException e = expectThrows(UnsupportedOperationException.class, () -> map.remove(new Object()));
        assertNotNull(e);
    }
}
