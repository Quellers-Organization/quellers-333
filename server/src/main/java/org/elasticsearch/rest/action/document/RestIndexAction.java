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

package org.elasticsearch.rest.action.document;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestActions;
import org.elasticsearch.rest.action.RestStatusToXContentListener;

import java.io.IOException;
import java.util.Locale;

import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestRequest.Method.PUT;

public class RestIndexAction extends BaseRestHandler {
    public RestIndexAction(RestController controller) {
        controller.registerHandler(POST, "/{index}/{type}", this); // auto id creation
        controller.registerHandler(PUT, "/{index}/{type}/{id}", this);
        controller.registerHandler(POST, "/{index}/{type}/{id}", this);
        CreateHandler createHandler = new CreateHandler();
        controller.registerHandler(PUT, "/{index}/{type}/{id}/_create", createHandler);
        controller.registerHandler(POST, "/{index}/{type}/{id}/_create", createHandler);
    }

    @Override
    public String getName() {
        return "document_index_action";
    }

    final class CreateHandler extends BaseRestHandler {
        protected CreateHandler() {
        }

        @Override
        public String getName() {
            return "document_create_action";
        }

        @Override
        public RestChannelConsumer prepareRequest(RestRequest request, final NodeClient client) throws IOException {
            validateOpType(request.params().get("op_type"));
            request.params().put("op_type", "create");
            return RestIndexAction.this.prepareRequest(request, client);
        }

        void validateOpType(String opType) {
            if (null != opType && false == "create".equals(opType.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("opType must be 'create', found: [" + opType + "]");
            }
        }
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        final boolean includeTypeName = request.paramAsBoolean("include_type_name", true);
        final String type = request.param("type");
        if (includeTypeName == false && MapperService.SINGLE_MAPPING_NAME.equals(type) == false) {
            throw new IllegalArgumentException("You may only use the [include_type_name=false] option with the index APIs with the " +
                    "[{index}/_doc/{id}] and [{index}/_doc] endpoints.");
        }
        IndexRequest indexRequest = new IndexRequest(request.param("index"), type, request.param("id"));
        indexRequest.routing(request.param("routing"));
        indexRequest.setPipeline(request.param("pipeline"));
        indexRequest.source(request.requiredContent(), request.getXContentType());
        indexRequest.timeout(request.paramAsTime("timeout", IndexRequest.DEFAULT_TIMEOUT));
        indexRequest.setRefreshPolicy(request.param("refresh"));
        indexRequest.version(RestActions.parseVersion(request));
        indexRequest.versionType(VersionType.fromString(request.param("version_type"), indexRequest.versionType()));
        String sOpType = request.param("op_type");
        String waitForActiveShards = request.param("wait_for_active_shards");
        if (waitForActiveShards != null) {
            indexRequest.waitForActiveShards(ActiveShardCount.parseString(waitForActiveShards));
        }
        if (sOpType != null) {
            indexRequest.opType(sOpType);
        }

        return channel ->
                client.index(indexRequest, new RestStatusToXContentListener<>(channel, r -> r.getLocation(indexRequest.routing())));
    }

}
