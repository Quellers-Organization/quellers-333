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

package org.elasticsearch.index.query;

import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class MatchAllQueryBuilderTest extends BaseQueryTest {

    protected MatchAllQueryBuilder testQuery;

    @Before
    public void setUpTest() throws IOException {
        testQuery = createTestQuery();
        String contentString = testQuery.toString();
        parser = XContentFactory.xContent(contentString).createParser(contentString);
        context.reset(parser);
    }

    @Test
    public void testFromXContent() throws IOException {
        MatchAllQueryBuilder newMatchAllQuery = new MatchAllQueryParser().fromXContent(context);
        assertThat(testQuery, is(newMatchAllQuery));
    }

    @Test
    public void testToQuery() throws IOException {
        MatchAllQueryBuilder newMatchAllQuery = new MatchAllQueryParser().fromXContent(context);
        Query query = newMatchAllQuery.toQuery(context);
        if (testQuery.boost() != 1.0f) {
            assertThat(query, instanceOf(MatchAllDocsQuery.class));
        } else {
            assertThat(query, instanceOf(ConstantScoreQuery.class));
        }
        assertThat(query.getBoost(), is(testQuery.boost()));
    }

    @Test
    public void testSerialization() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        testQuery.writeTo(output);

        BytesStreamInput bytesStreamInput = new BytesStreamInput(output.bytes());
        MatchAllQueryBuilder deserializedQuery = new MatchAllQueryBuilder();
        deserializedQuery.readFrom(bytesStreamInput);

        assertEquals(deserializedQuery, testQuery);
        assertNotSame(deserializedQuery, testQuery);
    }

    /**
     * @return a MatchAllQuery with random boost between 0.1f and 2.0f
     */
    public static MatchAllQueryBuilder createTestQuery() {
        MatchAllQueryBuilder query = new MatchAllQueryBuilder();
        if (randomBoolean()) {
            query.boost(2.0f / randomIntBetween(1, 20));
        }
        return query;
    }
}