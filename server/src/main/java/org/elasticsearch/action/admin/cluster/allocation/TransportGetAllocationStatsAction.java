/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.cluster.allocation;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.MasterNodeReadRequest;
import org.elasticsearch.action.support.master.TransportMasterNodeReadAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.routing.allocation.AllocationStatsService;
import org.elasticsearch.cluster.routing.allocation.NodeAllocationStats;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.Map;

public class TransportGetAllocationStatsAction extends TransportMasterNodeReadAction<
    TransportGetAllocationStatsAction.Request,
    TransportGetAllocationStatsAction.Response> {

    public static final ActionType<DesiredBalanceResponse> TYPE = new ActionType<>("cluster:monitor/allocation/stats");

    private final AllocationStatsService allocationStatsService;

    @Inject
    public TransportGetAllocationStatsAction(
        TransportService transportService,
        ClusterService clusterService,
        ThreadPool threadPool,
        ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver,
        AllocationStatsService allocationStatsService
    ) {
        super(
            TYPE.name(),
            transportService,
            clusterService,
            threadPool,
            actionFilters,
            TransportGetAllocationStatsAction.Request::new,
            indexNameExpressionResolver,
            TransportGetAllocationStatsAction.Response::new,
            threadPool.executor(ThreadPool.Names.MANAGEMENT)
        );
        this.allocationStatsService = allocationStatsService;
    }

    @Override
    protected void masterOperation(Task task, Request request, ClusterState state, ActionListener<Response> listener) throws Exception {
        listener.onResponse(new Response(allocationStatsService.stats()));
    }

    @Override
    protected ClusterBlockException checkBlock(Request request, ClusterState state) {
        return null;
    }

    public static class Request extends MasterNodeReadRequest<Request> {
        public Request() {}

        public Request(StreamInput in) throws IOException {
            super(in);
        }

        @Override
        public ActionRequestValidationException validate() {
            return null;
        }
    }

    public static class Response extends ActionResponse {

        private final Map<String, NodeAllocationStats> stats;

        public Response(Map<String, NodeAllocationStats> stats) {
            this.stats = stats;
        }

        public Response(StreamInput in) throws IOException {
            super(in);
            this.stats = in.readImmutableMap(StreamInput::readString, NodeAllocationStats::new);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeMap(stats, StreamOutput::writeString, StreamOutput::writeWriteable);
        }
    }

}
