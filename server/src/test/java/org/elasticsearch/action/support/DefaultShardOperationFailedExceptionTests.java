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

package org.elasticsearch.action.support;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.support.broadcast.BroadcastShardOperationFailedException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;

public class DefaultShardOperationFailedExceptionTests extends ESTestCase {

    public void testToString() {
        {
            DefaultShardOperationFailedException exception = new DefaultShardOperationFailedException(
                new ElasticsearchException("foo", new IllegalArgumentException("bar", new RuntimeException("baz"))));
            assertEquals("[null][-1] failed, reason [ElasticsearchException[foo]; nested: " +
                "IllegalArgumentException[bar]; nested: RuntimeException[baz]; ]", exception.toString());
        }
        {
            ElasticsearchException elasticsearchException = new ElasticsearchException("foo");
            elasticsearchException.setIndex(new Index("index1", "_na_"));
            elasticsearchException.setShard(new ShardId("index1", "_na_", 1));
            DefaultShardOperationFailedException exception = new DefaultShardOperationFailedException(elasticsearchException);
            assertEquals("[index1][1] failed, reason [ElasticsearchException[foo]]", exception.toString());
        }
        {
            DefaultShardOperationFailedException exception = new DefaultShardOperationFailedException("index2", 2, new Exception("foo"));
            assertEquals("[index2][2] failed, reason [Exception[foo]]", exception.toString());
        }
    }

    public void testToXContent() throws IOException {
        {
            DefaultShardOperationFailedException exception = new DefaultShardOperationFailedException(new ElasticsearchException("foo"));
            assertEquals("{\"shard\":-1,\"index\":null,\"status\":\"INTERNAL_SERVER_ERROR\"," +
                "\"reason\":{\"type\":\"exception\",\"reason\":\"foo\"}}", Strings.toString(exception));
        }
        {
            DefaultShardOperationFailedException exception = new DefaultShardOperationFailedException(
                new ElasticsearchException("foo", new IllegalArgumentException("bar")));
            assertEquals("{\"shard\":-1,\"index\":null,\"status\":\"INTERNAL_SERVER_ERROR\",\"reason\":{\"type\":\"exception\"," +
                "\"reason\":\"foo\",\"caused_by\":{\"type\":\"illegal_argument_exception\",\"reason\":\"bar\"}}}",
                Strings.toString(exception));
        }
        {
            DefaultShardOperationFailedException exception = new DefaultShardOperationFailedException(
                new BroadcastShardOperationFailedException(new ShardId("test", "_uuid", 2), "foo", new IllegalStateException("bar")));
            assertEquals("{\"shard\":2,\"index\":\"test\",\"status\":\"INTERNAL_SERVER_ERROR\"," +
                "\"reason\":{\"type\":\"illegal_state_exception\",\"reason\":\"bar\"}}", Strings.toString(exception));
        }
        {
            DefaultShardOperationFailedException exception = new DefaultShardOperationFailedException("test", 1,
                new IllegalArgumentException("foo"));
            assertEquals("{\"shard\":1,\"index\":\"test\",\"status\":\"BAD_REQUEST\"," +
                "\"reason\":{\"type\":\"illegal_argument_exception\",\"reason\":\"foo\"}}", Strings.toString(exception));
        }
    }

    public void testFromXContent() throws IOException {
        XContent xContent = randomFrom(XContentType.values()).xContent();
        XContentBuilder builder = XContentBuilder.builder(xContent)
            .startObject()
            .field("shard", 1)
            .field("index", "test")
            .field("status", "INTERNAL_SERVER_ERROR")
            .startObject("reason")
                .field("type", "exception")
                .field("reason", "foo")
            .endObject()
            .endObject();
        builder = shuffleXContent(builder);
        DefaultShardOperationFailedException parsed;
        try(XContentParser parser = createParser(xContent, BytesReference.bytes(builder))) {
            assertEquals(XContentParser.Token.START_OBJECT, parser.nextToken());
            parsed = DefaultShardOperationFailedException.fromXContent(parser);
            assertEquals(XContentParser.Token.END_OBJECT, parser.currentToken());
            assertNull(parser.nextToken());
        }

        assertNotNull(parsed);
        assertEquals(parsed.shardId(), 1);
        assertEquals(parsed.index(), "test");
        assertEquals(parsed.status(), RestStatus.INTERNAL_SERVER_ERROR);
        assertEquals(parsed.getCause().getMessage(), "Elasticsearch exception [type=exception, reason=foo]");
    }
}
