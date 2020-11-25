/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.indices;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.OriginSettingClient;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.routing.IndexRoutingTable;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.gateway.GatewayService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.elasticsearch.cluster.metadata.IndexMetadata.INDEX_FORMAT_SETTING;

public class SystemIndexManager implements ClusterStateListener {
    private static final Logger logger = LogManager.getLogger(SystemIndexManager.class);

    private final SystemIndices systemIndices;
    private final Client client;

    public SystemIndexManager(SystemIndices systemIndices, Client client) {
        this.systemIndices = systemIndices;
        this.client = client;
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        final ClusterState state = event.state();
        if (state.blocks().hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK)) {
            // wait until the gateway has recovered from disk, otherwise we may think we don't have some
            // indices but they may not have been restored from the cluster state on disk
            logger.debug("system indices manager waiting until state has been recovered");
            return;
        }

        // If this node is not a master node, exit.
        if (state.nodes().isLocalNodeElectedMaster() == false) {
            return;
        }

        getEligibleDescriptors(state.getMetadata()).stream()
            .filter(descriptor -> isUpgradeRequired(state, descriptor) == UpgradeStatus.NEEDS_MAPPINGS_UPDATE)
            .forEach(this::upgradeIndexMetadata);
    }

    List<SystemIndexDescriptor> getEligibleDescriptors(Metadata metadata) {
        return this.systemIndices.getSystemIndexDescriptors()
            .stream()
            .filter(SystemIndexDescriptor::isAutomaticallyManaged)
            .filter(d -> metadata.hasConcreteIndex(d.getPrimaryIndex()))
            .collect(Collectors.toList());
    }

    enum UpgradeStatus {
        CLOSED,
        UNHEALTHY,
        NEEDS_UPGRADE,
        UP_TO_DATE,
        NEEDS_MAPPINGS_UPDATE
    }

    UpgradeStatus isUpgradeRequired(ClusterState clusterState, SystemIndexDescriptor descriptor) {
        final State indexState = calculateIndexState(clusterState, descriptor);

        final String indexDescription = "Index [" + descriptor.getPrimaryIndex() + "] (alias [" + descriptor.getAliasName() + "])";

        // The messages below will be logged on every cluster state update, which is why even in the index closed / red
        // cases, the log levels are DEBUG.

        if (indexState.indexState == IndexMetadata.State.CLOSE) {
            logger.debug(
                "Index {} is closed. This is likely to prevent some features from functioning correctly", indexDescription);
            return UpgradeStatus.CLOSED;
        }

        if (indexState.indexHealth == ClusterHealthStatus.RED) {
            logger.debug("Index {} health status is RED, any pending mapping upgrades will wait until this changes", indexDescription);
            return UpgradeStatus.UNHEALTHY;
        }

        if (indexState.isIndexUpToDate == false) {
            logger.debug(
                "Index {} is not on the current version. Features relying "
                    + "on the index will not be available until the index is upgraded",
                indexDescription
            );
            return UpgradeStatus.NEEDS_UPGRADE;
        } else if (indexState.mappingUpToDate) {
            logger.trace("Index {} is up-to-date, no action required", indexDescription);
            return UpgradeStatus.UP_TO_DATE;
        } else {
            logger.info("Index {} mappings are not up-to-date and will be updated", indexDescription);
            return UpgradeStatus.NEEDS_MAPPINGS_UPDATE;
        }
    }

    private void upgradeIndexMetadata(SystemIndexDescriptor descriptor) {
        final String indexName = descriptor.getPrimaryIndex();

        PutMappingRequest request = new PutMappingRequest(indexName).source(descriptor.getMappings(), XContentType.JSON);

        final OriginSettingClient originSettingClient = new OriginSettingClient(this.client, descriptor.getOrigin());

        originSettingClient.admin().indices().putMapping(request, new ActionListener<>() {
            @Override
            public void onResponse(AcknowledgedResponse acknowledgedResponse) {
                if (acknowledgedResponse.isAcknowledged() == false) {
                    logger.error("Put mapping request for [{}] was not acknowledged", indexName);
                }
            }

            @Override
            public void onFailure(Exception e) {
                logger.error("Put mapping request for [" + indexName + "] failed", e);
            }
        });
    }

    State calculateIndexState(ClusterState state, SystemIndexDescriptor descriptor) {
        final IndexMetadata indexMetadata = state.metadata().index(descriptor.getPrimaryIndex());
        assert indexMetadata != null;

        final boolean isIndexUpToDate = INDEX_FORMAT_SETTING.get(indexMetadata.getSettings()) == descriptor.getIndexFormat();

        final boolean isMappingIsUpToDate = checkIndexMappingUpToDate(descriptor, indexMetadata);
        final String concreteIndexName = indexMetadata.getIndex().getName();

        final ClusterHealthStatus indexHealth;
        final IndexMetadata.State indexState = indexMetadata.getState();

        if (indexState == IndexMetadata.State.CLOSE) {
            indexHealth = null;
            logger.warn(
                "Index [{}] (alias [{}]) is closed. This is likely to prevent some features from functioning correctly",
                concreteIndexName,
                descriptor.getAliasName()
            );
        } else {
            final IndexRoutingTable routingTable = state.getRoutingTable().index(indexMetadata.getIndex());
            indexHealth = new ClusterIndexHealth(indexMetadata, routingTable).getStatus();
        }

        return new State(indexState, indexHealth, isIndexUpToDate, isMappingIsUpToDate);
    }

    private boolean checkIndexMappingUpToDate(SystemIndexDescriptor descriptor, IndexMetadata indexMetadata) {
        final MappingMetadata mappingMetadata = indexMetadata.mapping();
        if (mappingMetadata == null) {
            return false;
        }

        return Version.CURRENT.equals(readMappingVersion(descriptor, mappingMetadata));
    }

    @SuppressWarnings("unchecked")
    private Version readMappingVersion(SystemIndexDescriptor descriptor, MappingMetadata mappingMetadata) {
        final String indexName = descriptor.getPrimaryIndex();
        try {
            Map<String, Object> meta = (Map<String, Object>) mappingMetadata.sourceAsMap().get("_meta");
            if (meta == null) {
                logger.info("Missing _meta field in mapping [{}] of index [{}]", mappingMetadata.type(), indexName);
                throw new IllegalStateException("Cannot read version string in index " + indexName);
            }
            return Version.fromString((String) meta.get(descriptor.getVersionMetaKey()));
        } catch (ElasticsearchParseException e) {
            logger.error(new ParameterizedMessage("Cannot parse the mapping for index [{}]", indexName), e);
            throw new ElasticsearchException("Cannot parse the mapping for index [{}]", e, indexName);
        }
    }

    static class State {
        final IndexMetadata.State indexState;
        final ClusterHealthStatus indexHealth;
        final boolean isIndexUpToDate;
        final boolean mappingUpToDate;

        State(IndexMetadata.State indexState, ClusterHealthStatus indexHealth, boolean isIndexUpToDate, boolean mappingUpToDate) {
            this.indexState = indexState;
            this.indexHealth = indexHealth;
            this.isIndexUpToDate = isIndexUpToDate;
            this.mappingUpToDate = mappingUpToDate;
        }
    }
}
