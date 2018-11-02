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

package org.elasticsearch.rest.action.admin.indices;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.rest.FakeRestRequest;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class RestGetMappingActionTests extends ESTestCase {

    public void testTypeExistsDeprecation() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("type", "_doc");
        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.HEAD)
            .withParams(params)
            .build();

        RestGetMappingAction handler = new RestGetMappingAction(mock(RestController.class));
        handler.prepareRequest(request, mock(NodeClient.class));

        assertWarnings("Type exists requests are deprecated, as types have been deprecated.");
    }
}
