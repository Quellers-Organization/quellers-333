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

package org.elasticsearch.action.bench;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.internal.InternalClient;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequestBuilder;

/**
 * Request builder for benchmark pause
 */
public class BenchmarkControlRequestBuilder extends ActionRequestBuilder<BenchmarkControlRequest, BenchmarkStatusResponse, BenchmarkControlRequestBuilder> {

    public BenchmarkControlRequestBuilder(Client client) {
        super((InternalClient) client, new BenchmarkControlRequest());
    }

    public BenchmarkControlRequestBuilder setBenchmarkName(String benchmarkName) {
        request.benchmarkName(benchmarkName);
        return this;
    }

    public BenchmarkControlRequestBuilder setCommand(BenchmarkControlRequest.Command command) {
        request.command(command);
        return this;
    }

    @Override
    protected void doExecute(ActionListener<BenchmarkStatusResponse> listener) {
        ((Client) client).controlBenchmark(request, listener);
    }
}
