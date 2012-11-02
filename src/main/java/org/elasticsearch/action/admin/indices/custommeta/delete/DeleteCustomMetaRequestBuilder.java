/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.action.admin.indices.custommeta.delete;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.MasterNodeOperationRequestBuilder;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.client.internal.InternalIndicesAdminClient;

/**
 *
 */
public class DeleteCustomMetaRequestBuilder extends MasterNodeOperationRequestBuilder<DeleteCustomMetaRequest, DeleteCustomMetaResponse, DeleteCustomMetaRequestBuilder> {

    public DeleteCustomMetaRequestBuilder(IndicesAdminClient indicesClient) {
        super((InternalIndicesAdminClient) indicesClient, new DeleteCustomMetaRequest());
    }

    public DeleteCustomMetaRequestBuilder setIndices(String... indices) {
        request.indices(indices);
        return this;
    }

    /**
     * The name (or wildcard expression) of the index custom meta to delete, or null
     * to delete all warmers.
     */
    public DeleteCustomMetaRequestBuilder setName(String name) {
        request.name(name);
        return this;
    }

    @Override
    protected void doExecute(ActionListener<DeleteCustomMetaResponse> listener) {
        ((IndicesAdminClient) client).deleteCustomMeta(request, listener);
    }
}