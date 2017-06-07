/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package org.elasticsearch.test.test;

import junit.framework.AssertionFailedError;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.RandomObjects;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.elasticsearch.test.XContentTestUtils.insertRandomFields;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

public class ESTestCaseTests extends ESTestCase {

    public void testExpectThrows() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> {
            throw new IllegalArgumentException("bad arg");
        });
        assertEquals("bad arg", e.getMessage());

        try {
            expectThrows(IllegalArgumentException.class, () -> {
               throw new IllegalStateException("bad state");
            });
            fail("expected assertion error");
        } catch (AssertionFailedError assertFailed) {
            assertEquals("Unexpected exception type, expected IllegalArgumentException", assertFailed.getMessage());
            assertNotNull(assertFailed.getCause());
            assertEquals("bad state", assertFailed.getCause().getMessage());
        }

        try {
            expectThrows(IllegalArgumentException.class, () -> {});
            fail("expected assertion error");
        } catch (AssertionFailedError assertFailed) {
            assertNull(assertFailed.getCause());
            assertEquals("Expected exception IllegalArgumentException", assertFailed.getMessage());
        }
    }

    public void testShuffleMap() throws IOException {
        XContentType xContentType = randomFrom(XContentType.values());
        BytesReference source = RandomObjects.randomSource(random(), xContentType, 5);
        try (XContentParser parser = createParser(xContentType.xContent(), source)) {
            LinkedHashMap<String, Object> initialMap = (LinkedHashMap<String, Object>)parser.mapOrdered();

            Set<List<String>> distinctKeys = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                LinkedHashMap<String, Object> shuffledMap = shuffleMap(initialMap, Collections.emptySet());
                assertEquals("both maps should contain the same mappings", initialMap, shuffledMap);
                List<String> shuffledKeys = new ArrayList<>(shuffledMap.keySet());
                distinctKeys.add(shuffledKeys);
            }
            //out of 10 shuffling runs we expect to have at least more than 1 distinct output.
            //This is to make sure that we actually do the shuffling
            assertThat(distinctKeys.size(), greaterThan(1));
        }
    }

    public void testShuffleXContentExcludeFields() throws IOException {
        XContentType xContentType = randomFrom(XContentType.values());
        try (XContentBuilder builder = XContentBuilder.builder(xContentType.xContent())) {
            builder.startObject();
            {
                builder.field("field1", "value1");
                builder.field("field2", "value2");
                {
                    builder.startObject("object1");
                    {
                        builder.field("inner1", "value1");
                        builder.field("inner2", "value2");
                        builder.field("inner3", "value3");
                    }
                    builder.endObject();
                }
                {
                    builder.startObject("object2");
                    {
                        builder.field("inner4", "value4");
                        builder.field("inner5", "value5");
                        builder.field("inner6", "value6");
                    }
                    builder.endObject();
                }
            }
            builder.endObject();
            BytesReference bytes = builder.bytes();
            final LinkedHashMap<String, Object> initialMap;
            try (XContentParser parser = createParser(xContentType.xContent(), bytes)) {
                initialMap = (LinkedHashMap<String, Object>)parser.mapOrdered();
            }

            List<String> expectedInnerKeys1 = Arrays.asList("inner1", "inner2", "inner3");
            Set<List<String>> distinctTopLevelKeys = new HashSet<>();
            Set<List<String>> distinctInnerKeys2 = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                try (XContentParser parser = createParser(xContentType.xContent(), bytes)) {
                    try (XContentBuilder shuffledBuilder = shuffleXContent(parser, randomBoolean(), "object1")) {
                        try (XContentParser shuffledParser = createParser(shuffledBuilder)) {
                            Map<String, Object> shuffledMap = shuffledParser.mapOrdered();
                            assertEquals("both maps should contain the same mappings", initialMap, shuffledMap);
                            List<String> shuffledKeys = new ArrayList<>(shuffledMap.keySet());
                            distinctTopLevelKeys.add(shuffledKeys);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> innerMap1 = (Map<String, Object>)shuffledMap.get("object1");
                            List<String> actualInnerKeys1 = new ArrayList<>(innerMap1.keySet());
                            assertEquals("object1 should have been left untouched", expectedInnerKeys1, actualInnerKeys1);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> innerMap2 = (Map<String, Object>)shuffledMap.get("object2");
                            List<String> actualInnerKeys2 = new ArrayList<>(innerMap2.keySet());
                            distinctInnerKeys2.add(actualInnerKeys2);
                        }
                    }
                }
            }

            //out of 10 shuffling runs we expect to have at least more than 1 distinct output for both top level keys and inner object2
            assertThat(distinctTopLevelKeys.size(), greaterThan(1));
            assertThat(distinctInnerKeys2.size(), greaterThan(1));
        }
    }

    public void testRandomUniqueNotUnique() {
        assertThat(randomUnique(() -> 1, 10), hasSize(1));
    }

    public void testRandomUniqueTotallyUnique() {
        AtomicInteger i = new AtomicInteger();
        assertThat(randomUnique(i::incrementAndGet, 100), hasSize(100));
    }

    public void testRandomUniqueNormalUsageAlwayMoreThanOne() {
        assertThat(randomUnique(() -> randomAlphaOfLengthBetween(1, 20), 10), hasSize(greaterThan(0)));
    }

    @SuppressWarnings("unchecked")
    public void testInsertRandomXContent() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        {
            builder.startObject("foo");
            {
                builder.field("bar", 1);
            }
            builder.endObject();
            builder.startObject("foo1");
            {
                builder.startObject("foo2");
                {
                    builder.field("buzz", 1);
                }
                builder.endObject();
            }
            builder.endObject();
            builder.field("foo3", 2);
            builder.startArray("foo4");
            {
                builder.startObject();
                {
                    builder.field("foo5", 1);
                }
                builder.endObject();
            }
            builder.endArray();
        }
        builder.endObject();

        Map<String, Object> resultMap;

        try (XContentParser parser = createParser(XContentType.JSON.xContent(),
                insertRandomFields(builder.contentType(), builder.bytes(), null, random(), xContentRegistry()))) {
            resultMap = parser.map();
        }
        assertEquals(5, resultMap.keySet().size());
        assertEquals(2, ((Map<String, Object>) resultMap.get("foo")).keySet().size());
        Map<String, Object> foo1 = (Map<String, Object>) resultMap.get("foo1");
        assertEquals(2, foo1.keySet().size());
        assertEquals(2, ((Map<String, Object>) foo1.get("foo2")).keySet().size());
        List<Object> foo4List = (List<Object>) resultMap.get("foo4");
        assertEquals(1, foo4List.size());
        assertEquals(2, ((Map<String, Object>) foo4List.get(0)).keySet().size());

        Predicate<String> pathsToExclude = path -> path.endsWith("foo1");
        try (XContentParser parser = createParser(XContentType.JSON.xContent(),
                insertRandomFields(builder.contentType(), builder.bytes(), pathsToExclude, random(), xContentRegistry()))) {
            resultMap = parser.map();
        }
        assertEquals(5, resultMap.keySet().size());
        assertEquals(2, ((Map<String, Object>) resultMap.get("foo")).keySet().size());
        foo1 = (Map<String, Object>) resultMap.get("foo1");
        assertEquals(1, foo1.keySet().size());
        assertEquals(2, ((Map<String, Object>) foo1.get("foo2")).keySet().size());
        foo4List = (List<Object>) resultMap.get("foo4");
        assertEquals(1, foo4List.size());
        assertEquals(2, ((Map<String, Object>) foo4List.get(0)).keySet().size());

        pathsToExclude = path -> path.contains("foo1");
        try (XContentParser parser = createParser(XContentType.JSON.xContent(),
                insertRandomFields(builder.contentType(), builder.bytes(), pathsToExclude, random(), xContentRegistry()))) {
            resultMap = parser.map();
        }
        assertEquals(5, resultMap.keySet().size());
        assertEquals(2, ((Map<String, Object>) resultMap.get("foo")).keySet().size());
        foo1 = (Map<String, Object>) resultMap.get("foo1");
        assertEquals(1, foo1.keySet().size());
        assertEquals(1, ((Map<String, Object>) foo1.get("foo2")).keySet().size());
        foo4List = (List<Object>) resultMap.get("foo4");
        assertEquals(1, foo4List.size());
        assertEquals(2, ((Map<String, Object>) foo4List.get(0)).keySet().size());
    }
}
