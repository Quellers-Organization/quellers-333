/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ilm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodeRole;
import org.elasticsearch.cluster.routing.allocation.decider.AllocationDeciders;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.xpack.cluster.routing.allocation.DataTierAllocationDecider;
import org.elasticsearch.xpack.core.DataTier;
import org.elasticsearch.xpack.core.ilm.step.info.AllocationInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.elasticsearch.xpack.cluster.routing.allocation.DataTierAllocationDecider.INDEX_ROUTING_INCLUDE_SETTING;
import static org.elasticsearch.xpack.core.ilm.AllocationRoutedStep.getPendingAllocations;

/**
 * Checks whether all shards have been correctly routed in response to updating the allocation rules for an index in order
 * to migrate the index to a new tier.
 */
public class DataTierMigrationRoutedStep extends ClusterStateWaitStep {
    public static final String NAME = "check-migration";

    private static final Logger logger = LogManager.getLogger(DataTierMigrationRoutedStep.class);

    private static final Set<Setting<?>> ALL_CLUSTER_SETTINGS;

    static {
        Set<Setting<?>> allSettings = new HashSet<>(ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);
        allSettings.add(DataTierAllocationDecider.CLUSTER_ROUTING_REQUIRE_SETTING);
        allSettings.add(DataTierAllocationDecider.CLUSTER_ROUTING_INCLUDE_SETTING);
        allSettings.add(DataTierAllocationDecider.CLUSTER_ROUTING_EXCLUDE_SETTING);
        ALL_CLUSTER_SETTINGS = allSettings;
    }

    private static final AllocationDeciders ALLOCATION_DECIDERS = new AllocationDeciders(
        List.of(
            new DataTierAllocationDecider(new ClusterSettings(Settings.EMPTY, ALL_CLUSTER_SETTINGS))
        )
    );

    DataTierMigrationRoutedStep(StepKey key, StepKey nextStepKey) {
        super(key, nextStepKey);
    }

    @Override
    public boolean isRetryable() {
        return true;
    }

    @Override
    public Result isConditionMet(Index index, ClusterState clusterState) {
        IndexMetadata idxMeta = clusterState.metadata().index(index);
        if (idxMeta == null) {
            // Index must have been since deleted, ignore it
            logger.debug("[{}] lifecycle action for index [{}] executed but index no longer exists", getKey().getAction(), index.getName());
            return new Result(false, null);
        }
        if (ActiveShardCount.ALL.enoughShardsActive(clusterState, index.getName()) == false) {
            logger.debug("[{}] lifecycle action for index [{}] cannot make progress because not all shards are active",
                    getKey().getAction(), index.getName());
            return new Result(false, AllocationInfo.waitingForActiveShardsAllocationInfo(idxMeta.getNumberOfReplicas()));
        }

        int allocationPendingAllShards = getPendingAllocations(index, ALLOCATION_DECIDERS, clusterState);

        if (allocationPendingAllShards > 0) {
            String tier = INDEX_ROUTING_INCLUDE_SETTING.get(idxMeta.getSettings());
            boolean targetTierNodeFound = false;
            for (DiscoveryNode node : clusterState.nodes()) {
                for (DiscoveryNodeRole role : node.getRoles()) {
                    if (role.roleName().equals(tier)) {
                        targetTierNodeFound = true;
                        break;
                    }
                }
            }
            String statusMessage = String.format(Locale.ROOT, "%s lifecycle action [%s] waiting for [%s] shards to be moved to the [%s] " +
                    "tier" + (targetTierNodeFound ? "" : " but there are currently no [%s] nodes in the cluster"),
                index, getKey().getAction(), allocationPendingAllShards, tier, tier);
            logger.debug(statusMessage);
            return new Result(false, new AllocationInfo(idxMeta.getNumberOfReplicas(), allocationPendingAllShards, true, statusMessage));
        } else {
            logger.debug("{} lifecycle action for [{}] complete", index, getKey().getAction());
            return new Result(true, null);
        }
    }

    @Override
    public int hashCode() {
        return 711;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return super.equals(obj);
    }
}
