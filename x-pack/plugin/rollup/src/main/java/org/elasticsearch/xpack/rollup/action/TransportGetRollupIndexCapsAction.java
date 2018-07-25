/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.rollup.action;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.rollup.action.GetRollupIndexCapsAction;
import org.elasticsearch.xpack.core.rollup.action.RollableIndexCaps;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.StreamSupport;

public class TransportGetRollupIndexCapsAction extends HandledTransportAction<GetRollupIndexCapsAction.Request,
    GetRollupIndexCapsAction.Response> {

    private final ClusterService clusterService;

    @Inject
    public TransportGetRollupIndexCapsAction(Settings settings, TransportService transportService,
                                             ClusterService clusterService, ThreadPool threadPool,
                                             ActionFilters actionFilters,
                                             IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, GetRollupIndexCapsAction.NAME, threadPool, transportService, actionFilters,
            indexNameExpressionResolver, GetRollupIndexCapsAction.Request::new);
        this.clusterService = clusterService;
    }

    @Override
    protected void doExecute(GetRollupIndexCapsAction.Request request,
                             ActionListener<GetRollupIndexCapsAction.Response> listener) {

        IndexNameExpressionResolver resolver = new IndexNameExpressionResolver(clusterService.getSettings());
        String[] indices = resolver.concreteIndexNames(clusterService.state(),
            request.indicesOptions(), request.indices());
        Map<String, RollableIndexCaps> allCaps = getCapsByRollupIndex(Arrays.asList(indices),
            clusterService.state().getMetaData().indices());
        listener.onResponse(new GetRollupIndexCapsAction.Response(allCaps));
    }

    static Map<String, RollableIndexCaps> getCapsByRollupIndex(List<String> resolvedIndexNames,
                                                               ImmutableOpenMap<String, IndexMetaData> indices) {
        Map<String, RollableIndexCaps> allCaps = new TreeMap<>();

        StreamSupport.stream(indices.spliterator(), false)
            .filter(entry -> resolvedIndexNames.contains(entry.key))
            .forEach(entry -> {
                // Does this index have rollup metadata?
                TransportGetRollupCapsAction.findRollupIndexCaps(entry.key, entry.value)
                    .ifPresent(cap -> {
                        cap.getJobCaps().forEach(jobCap -> {
                            // Do we already have an entry for this index?
                            RollableIndexCaps indexCaps = allCaps.get(jobCap.getRollupIndex());
                            if (indexCaps == null) {
                                indexCaps = new RollableIndexCaps(jobCap.getRollupIndex());
                            }
                            indexCaps.addJobCap(jobCap);
                            allCaps.put(jobCap.getRollupIndex(), indexCaps);
                        });
                    });
            });

        return allCaps;
    }

}
