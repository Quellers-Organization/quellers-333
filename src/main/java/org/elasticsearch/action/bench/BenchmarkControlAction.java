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

import org.elasticsearch.action.Action;
import org.elasticsearch.client.Client;

/**
 * Benchmark pause action
 */
public class BenchmarkControlAction extends Action<BenchmarkControlRequest, BenchmarkStatusResponse, BenchmarkControlRequestBuilder> {

    public static final BenchmarkControlAction INSTANCE = new BenchmarkControlAction();
    public static final String NAME = "benchmark/control";

    public BenchmarkControlAction() {
        super(NAME);
    }

    @Override
    public BenchmarkControlRequestBuilder newRequestBuilder(Client client) {
        return new BenchmarkControlRequestBuilder(client);
    }

    @Override
    public BenchmarkStatusResponse newResponse() {
        return new BenchmarkStatusResponse();
    }
}
