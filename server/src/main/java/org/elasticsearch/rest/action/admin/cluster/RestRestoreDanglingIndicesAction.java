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

package org.elasticsearch.rest.action.admin.cluster;

import org.elasticsearch.action.admin.indices.dangling.RestoreDanglingIndicesRequest;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.GET;

public class RestRestoreDanglingIndicesAction extends BaseRestHandler {
    public RestRestoreDanglingIndicesAction(RestController controller) {
        controller.registerHandler(GET, "/_dangling/restore", this);
    }

    @Override
    public String getName() {
        return "restore_dangling_indices";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, NodeClient client) throws IOException {
        final RestoreDanglingIndicesRequest restoreRequest = new RestoreDanglingIndicesRequest();
        request.applyContentParser(p -> restoreRequest.source(p.mapOrdered()));

        return channel -> client.admin().cluster().restoreDanglingIndices(restoreRequest, new RestStatusToXContentListener<>(channel));
    }
}
