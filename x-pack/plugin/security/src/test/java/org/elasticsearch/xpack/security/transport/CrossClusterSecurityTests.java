/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.security.transport;

import org.elasticsearch.action.support.DestructiveOperations;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.RoutingTable;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ClusterServiceUtils;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TcpTransport;
import org.elasticsearch.xpack.core.security.SecurityContext;
import org.elasticsearch.xpack.core.ssl.SSLService;
import org.elasticsearch.xpack.security.authc.AuthenticationService;
import org.elasticsearch.xpack.security.authz.AuthorizationService;
import org.junit.After;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class CrossClusterSecurityTests extends ESTestCase {

    private ThreadPool threadPool;
    private ClusterService clusterService;

    @Override
    public void setUp() throws Exception {
        if (TcpTransport.isUntrustedRemoteClusterEnabled()) {
            super.setUp();
            this.threadPool = new TestThreadPool(getTestName());
        }

    }

    @After
    public void stopThreadPool() throws Exception {
        if (TcpTransport.isUntrustedRemoteClusterEnabled()) {
            this.clusterService.close();
            terminate(this.threadPool);
        }
    }

    public void testSendAsync() {
        assumeThat(TcpTransport.isUntrustedRemoteClusterEnabled(), is(true));
        final Settings fixedSettings = Settings.builder().put("path.home", createTempDir()).build();
        final String clusterNameA = "action";
        final String clusterNameB = "clusterB";
        final Settings initialSettings = Settings.builder()
            .put(fixedSettings)
            .put("cluster.remote." + clusterNameA + ".authorization", "initialize")
            .build();

        this.clusterService = ClusterServiceUtils.createClusterService(this.threadPool);
        SecurityContext securityContext = spy(new SecurityContext(initialSettings, this.threadPool.getThreadContext()));

        new SecurityServerTransportInterceptor(
            initialSettings,
            this.threadPool,
            mock(AuthenticationService.class),
            mock(AuthorizationService.class),
            mock(SSLService.class),
            securityContext,
            new DestructiveOperations(initialSettings, this.clusterService.getClusterSettings()),
            new CrossClusterSecurity(initialSettings, this.clusterService.getClusterSettings())
        );
        final DiscoveryNode masterNodeA = this.clusterService.state().nodes().getMasterNode();

        // Add clusterB authorization setting
        final Settings newSettings1 = Settings.builder()
            .put(fixedSettings)
            .put("cluster.remote." + clusterNameA + ".authorization", "addedB")
            .put("cluster.remote." + clusterNameB + ".authorization", randomApiKey())
            .build();
        final ClusterState newClusterState1 = createClusterState(clusterNameA, masterNodeA, newSettings1);
        ClusterServiceUtils.setState(this.clusterService, newClusterState1);

        // Change clusterB authorization setting
        final Settings newSettings2 = Settings.builder()
            .put(fixedSettings)
            .put("cluster.remote." + clusterNameA + ".authorization", "updatedB")
            .put("cluster.remote." + clusterNameB + ".authorization", randomApiKey())
            .build();
        final ClusterState newClusterState2 = createClusterState(clusterNameA, masterNodeA, newSettings2);
        ClusterServiceUtils.setState(this.clusterService, newClusterState2);

        // Remove clusterB authorization setting
        final Settings newSettings3 = Settings.builder()
            .put(fixedSettings)
            .put("cluster.remote." + clusterNameA + ".authorization", "removedB")
            .build();
        final ClusterState newClusterState3 = createClusterState(clusterNameA, masterNodeA, newSettings3);
        ClusterServiceUtils.setState(this.clusterService, newClusterState3);
    }

    private static ClusterState createClusterState(final String clusterName, final DiscoveryNode masterNode, final Settings newSettings) {
        final DiscoveryNodes.Builder discoBuilder = DiscoveryNodes.builder();
        discoBuilder.add(masterNode);
        discoBuilder.masterNodeId(masterNode.getId());

        final ClusterState.Builder state = ClusterState.builder(new ClusterName(clusterName));
        state.nodes(discoBuilder);
        state.metadata(Metadata.builder().persistentSettings(newSettings).generateClusterUuidIfNeeded());
        state.routingTable(RoutingTable.builder().build());
        return state.build();
    }

    private String randomApiKey() {
        final String id = "apikey_" + randomAlphaOfLength(6);
        // Sufficient for testing. See ApiKeyService and ApiKeyService.ApiKeyCredentials for actual API Key generation.
        try (SecureString secret = UUIDs.randomBase64UUIDSecureString()) {
            final String apiKey = id + ":" + secret;
            return Base64.getEncoder().withoutPadding().encodeToString(apiKey.getBytes(StandardCharsets.UTF_8));
        }
    }
}
