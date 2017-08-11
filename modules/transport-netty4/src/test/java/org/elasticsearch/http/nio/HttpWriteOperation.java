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

package org.elasticsearch.http.nio;

import io.netty.handler.codec.http.HttpResponse;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.transport.nio.WriteOperation;
import org.elasticsearch.transport.nio.channel.NioChannel;
import org.elasticsearch.transport.nio.channel.NioSocketChannel;

public class HttpWriteOperation extends WriteOperation {

    private final HttpResponse httpResponse;

    public HttpWriteOperation(NioSocketChannel channel, HttpResponse response, ActionListener<NioChannel> listener) {
        super(channel, listener);
        this.httpResponse = response;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }
}
