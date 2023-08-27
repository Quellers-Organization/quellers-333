/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.transport.netty4;

import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.TransportVersion;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNodeRole;
import org.elasticsearch.cluster.node.VersionInformation;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.MockSecureSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.PageCacheRecycler;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.core.IOUtils;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.NodeRoles;
import org.elasticsearch.test.transport.MockTransportService;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.NoSeedNodeLeftException;
import org.elasticsearch.transport.NoSuchRemoteClusterException;
import org.elasticsearch.transport.ProxyConnectionStrategy;
import org.elasticsearch.transport.RemoteClusterPortSettings;
import org.elasticsearch.transport.RemoteClusterService;
import org.elasticsearch.transport.RemoteConnectionStrategy;
import org.elasticsearch.transport.SniffConnectionStrategy;
import org.elasticsearch.transport.TestRequest;
import org.elasticsearch.transport.TestResponse;
import org.elasticsearch.transport.TransportException;
import org.elasticsearch.transport.TransportRequestOptions;
import org.elasticsearch.transport.TransportResponse;
import org.elasticsearch.transport.TransportResponseHandler;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.transport.netty4.SharedGroupFactory;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.ssl.SSLService;
import org.elasticsearch.xpack.security.authc.CrossClusterAccessAuthenticationService;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.elasticsearch.test.ActionListenerUtils.anyActionListener;
import static org.elasticsearch.test.NodeRoles.onlyRole;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class SecurityNetty4ServerTransportAuthenticationTests extends ESTestCase {

    private ThreadPool threadPool;
    private String remoteClusterName;
    private SecurityNetty4ServerTransport remoteSecurityNetty4ServerTransport;
    private MockTransportService remoteTransportService;
    private CrossClusterAccessAuthenticationService remoteCrossClusterAccessAuthenticationService;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        threadPool = new TestThreadPool(getClass().getName());
        remoteClusterName = "test-remote_cluster_service_" + randomAlphaOfLength(8);
        Settings remoteSettings = Settings.builder()
            .put("node.name", getClass().getName())
            .put(ClusterName.CLUSTER_NAME_SETTING.getKey(), remoteClusterName)
            .put(XPackSettings.TRANSPORT_SSL_ENABLED.getKey(), "false")
            .put(XPackSettings.REMOTE_CLUSTER_SERVER_SSL_ENABLED.getKey(), "false")
            .put(XPackSettings.REMOTE_CLUSTER_CLIENT_SSL_ENABLED.getKey(), "false")
            .put(RemoteClusterPortSettings.REMOTE_CLUSTER_SERVER_ENABLED.getKey(), "true")
            .build();
        remoteSettings = NodeRoles.nonRemoteClusterClientNode(remoteSettings);
        remoteCrossClusterAccessAuthenticationService = mock(CrossClusterAccessAuthenticationService.class);
        remoteSecurityNetty4ServerTransport = new SecurityNetty4ServerTransport(
            remoteSettings,
            TransportVersion.current(),
            threadPool,
            new NetworkService(List.of()),
            PageCacheRecycler.NON_RECYCLING_INSTANCE,
            new NamedWriteableRegistry(List.of()),
            new NoneCircuitBreakerService(),
            null,
            mock(SSLService.class),
            new SharedGroupFactory(remoteSettings),
            remoteCrossClusterAccessAuthenticationService
        );
        remoteTransportService = MockTransportService.createNewService(
            remoteSettings,
            remoteSecurityNetty4ServerTransport,
            VersionInformation.CURRENT,
            threadPool,
            null,
            Collections.emptySet(),
            TransportService.NOOP_TRANSPORT_INTERCEPTOR
        );
        remoteTransportService.start();
        remoteTransportService.acceptIncomingRequests();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        IOUtils.close(
            remoteTransportService,
            remoteSecurityNetty4ServerTransport,
            () -> ThreadPool.terminate(threadPool, 10, TimeUnit.SECONDS)
        );
    }

    @SuppressWarnings("unchecked")
    public void testProxyStrategyConnectionClosesWhenAuthenticatorAlwaysFails() throws Exception {
        // all requests fail authn
        doAnswer(invocation -> {
            ((ActionListener<Void>) invocation.getArguments()[1]).onFailure(new ElasticsearchSecurityException("failed authn"));
            return null;
        }).when(remoteCrossClusterAccessAuthenticationService).tryAuthenticate(any(Map.class), anyActionListener());
        Settings localSettings = Settings.builder()
            .put(onlyRole(DiscoveryNodeRole.REMOTE_CLUSTER_CLIENT_ROLE))
            .put(RemoteConnectionStrategy.REMOTE_CONNECTION_MODE.getConcreteSettingForNamespace(remoteClusterName).getKey(), "proxy")
            .put(
                ProxyConnectionStrategy.PROXY_ADDRESS.getConcreteSettingForNamespace(remoteClusterName).getKey(),
                remoteTransportService.boundRemoteAccessAddress().publishAddress().toString()
            )
            .put(
                ProxyConnectionStrategy.REMOTE_SOCKET_CONNECTIONS.getConcreteSettingForNamespace(remoteClusterName).getKey(),
                randomIntBetween(1, 3) // easier to debug with just 1 connection
            )
            .build();
        {
            final MockSecureSettings secureSettings = new MockSecureSettings();
            secureSettings.setString(
                RemoteClusterService.REMOTE_CLUSTER_CREDENTIALS.getConcreteSettingForNamespace(remoteClusterName).getKey(),
                randomAlphaOfLength(20)
            );
            localSettings = Settings.builder().put(localSettings).setSecureSettings(secureSettings).build();
        }
        try (
            MockTransportService localService = MockTransportService.createNewService(
                localSettings,
                VersionInformation.CURRENT,
                TransportVersion.current(),
                threadPool
            )
        ) {
            localService.start();
            RemoteClusterService remoteClusterService = localService.getRemoteClusterService();
            // obtain some connections and check that they'll be promptly closed
            for (int i = 0; i < randomIntBetween(4, 16); i++) {
                PlainActionFuture<Void> closeFuture = PlainActionFuture.newFuture();
                CountDownLatch connectionTestDone = new CountDownLatch(1);
                remoteClusterService.maybeEnsureConnectedAndGetConnection(remoteClusterName, true, ActionListener.wrap(connection -> {
                    // {@code RemoteClusterService.REMOTE_CLUSTER_HANDSHAKE_ACTION_NAME} fails authn and the connection is closed,
                    // but it is usually closed AFTER the handshake response returned
                    logger.info("Connection will auto-close");
                    connection.addCloseListener(closeFuture);
                    connectionTestDone.countDown();
                }, e -> {
                    // {@code RemoteClusterService.REMOTE_CLUSTER_HANDSHAKE_ACTION_NAME} fails authn and the connection is closed
                    // before the handshake response returned
                    logger.info("A connection could not be established");
                    assertThat(e, instanceOf(NoSuchRemoteClusterException.class));
                    closeFuture.onResponse(null);
                    connectionTestDone.countDown();
                }));
                closeFuture.get(10L, TimeUnit.SECONDS);
                assertTrue(connectionTestDone.await(10L, TimeUnit.SECONDS));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void testProxyStrategyCloseOnlyUnauthenticatedConnections() throws Exception {
        // use some random request header to test conveying of authn credentials
        String authnHeader = randomAlphaOfLength(8);
        String authnHeaderValue = randomAlphaOfLength(8);
        // register some dummy actions on the remote service
        remoteTransportService.registerRequestHandler(
            "internal:some_authn_action",
            ThreadPool.Names.GENERIC,
            TestRequest::new,
            (request, channel, task) -> {
                try {
                    channel.sendResponse(new TestResponse("some_authn_action_response"));
                } catch (IOException e) {
                    logger.error("Unexpected failure", e);
                    fail(e.getMessage());
                }
            }
        );
        remoteTransportService.registerRequestHandler(
            "internal:some_not_authn_action",
            ThreadPool.Names.GENERIC,
            TestRequest::new,
            (request, channel, task) -> {
                try {
                    channel.sendResponse(new TestResponse("some_not_authn_action_response"));
                } catch (IOException e) {
                    logger.error("Unexpected failure", e);
                    fail(e.getMessage());
                }
            }
        );
        // the set of actions that successfully pass authn
        Set<String> correctlyAuthnActions = new HashSet<>();
        correctlyAuthnActions.add(RemoteClusterService.REMOTE_CLUSTER_HANDSHAKE_ACTION_NAME);
        correctlyAuthnActions.add("internal:some_authn_action");
        // internal:some_not_authn_action will fail authn
        doAnswer(invocation -> {
            Map<String, String> headers = invocation.getArgument(0);
            // check headers for authn
            if (authnHeaderValue.equals(headers.get(authnHeader))) {
                ((ActionListener<Void>) invocation.getArguments()[1]).onResponse(null);
            } else {
                ((ActionListener<Void>) invocation.getArguments()[1]).onFailure(new ElasticsearchSecurityException("failed authn"));
            }
            return null;
        }).when(remoteCrossClusterAccessAuthenticationService).tryAuthenticate(any(Map.class), anyActionListener());
        Settings localSettings = Settings.builder()
            .put(onlyRole(DiscoveryNodeRole.REMOTE_CLUSTER_CLIENT_ROLE))
            .put(RemoteConnectionStrategy.REMOTE_CONNECTION_MODE.getConcreteSettingForNamespace(remoteClusterName).getKey(), "proxy")
            .put(
                ProxyConnectionStrategy.PROXY_ADDRESS.getConcreteSettingForNamespace(remoteClusterName).getKey(),
                remoteTransportService.boundRemoteAccessAddress().publishAddress().toString()
            )
            .put(
                ProxyConnectionStrategy.REMOTE_SOCKET_CONNECTIONS.getConcreteSettingForNamespace(remoteClusterName).getKey(),
                randomIntBetween(1, 3) // easier to debug with just 1 connection
            )
            .build();
        {
            final MockSecureSettings secureSettings = new MockSecureSettings();
            secureSettings.setString(
                RemoteClusterService.REMOTE_CLUSTER_CREDENTIALS.getConcreteSettingForNamespace(remoteClusterName).getKey(),
                randomAlphaOfLength(20)
            );
            localSettings = Settings.builder().put(localSettings).setSecureSettings(secureSettings).build();
        }
        try (
            MockTransportService localService = MockTransportService.createNewService(
                localSettings,
                VersionInformation.CURRENT,
                TransportVersion.current(),
                threadPool
            )
        ) {
            // authenticate some actions, but not all
            localService.addSendBehavior((connection, requestId, action, request, options) -> {
                final ThreadContext threadContext = threadPool.getThreadContext();
                try (ThreadContext.StoredContext ignore = threadContext.newStoredContext()) {
                    if (correctlyAuthnActions.contains(action)) {
                        threadContext.putHeader(authnHeader, authnHeaderValue);
                    } else if (randomBoolean()) {
                        threadContext.putHeader(authnHeader, "WRONG" + authnHeaderValue);
                    }
                    connection.sendRequest(requestId, action, request, options);
                }
            });
            localService.start();
            RemoteClusterService remoteClusterService = localService.getRemoteClusterService();
            // obtain some connections and check that requests that fail authn close the connection
            {
                CountDownLatch connectionTestDone = new CountDownLatch(1);
                remoteClusterService.maybeEnsureConnectedAndGetConnection(remoteClusterName, true, ActionListener.wrap(connection -> {
                    PlainActionFuture<Void> closeFuture = PlainActionFuture.newFuture();
                    CountDownLatch responseLatch = new CountDownLatch(1);
                    connection.addCloseListener(closeFuture);
                    localService.sendRequest(
                        connection,
                        "internal:some_not_authn_action",
                        new TestRequest("a request that does NOT pass authn"),
                        TransportRequestOptions.EMPTY,
                        new TransportResponseHandler<>() {
                            @Override
                            public Executor executor(ThreadPool threadPool) {
                                return TransportResponseHandler.TRANSPORT_WORKER;
                            }

                            @Override
                            public void handleResponse(TransportResponse response) {
                                responseLatch.countDown();
                            }

                            @Override
                            public void handleException(TransportException exp) {
                                responseLatch.countDown();
                            }

                            @Override
                            public TransportResponse read(StreamInput in) throws IOException {
                                return new TestResponse(in);
                            }
                        }
                    );
                    // the request receives SOME response
                    assertTrue(responseLatch.await(10L, TimeUnit.SECONDS));
                    // and the connection is NOT closed afterwards
                    logger.info("Waiting for the connection to be closed");
                    closeFuture.get(10L, TimeUnit.SECONDS);
                    connectionTestDone.countDown();
                }, e -> {
                    connectionTestDone.countDown();
                    fail("Connection could not be established but should've");
                    throw new RuntimeException(e);
                }));
                assertTrue(connectionTestDone.await(10L, TimeUnit.SECONDS));
            }
            {
                CountDownLatch connectionTestDone = new CountDownLatch(1);
                remoteClusterService.maybeEnsureConnectedAndGetConnection(remoteClusterName, true, ActionListener.wrap(connection -> {
                    PlainActionFuture<Void> closeFuture = PlainActionFuture.newFuture();
                    CountDownLatch responseLatch = new CountDownLatch(1);
                    connection.addCloseListener(closeFuture);
                    localService.sendRequest(
                        connection,
                        "internal:some_authn_action",
                        new TestRequest("a request that passes authn"),
                        TransportRequestOptions.EMPTY,
                        new TransportResponseHandler<>() {
                            @Override
                            public Executor executor(ThreadPool threadPool) {
                                return TransportResponseHandler.TRANSPORT_WORKER;
                            }

                            @Override
                            public void handleResponse(TransportResponse response) {
                                responseLatch.countDown();
                            }

                            @Override
                            public void handleException(TransportException exp) {
                                fail("Unexpected exception, a response was expected");
                                throw exp;
                            }

                            @Override
                            public TransportResponse read(StreamInput in) throws IOException {
                                return new TestResponse(in);
                            }
                        }
                    );
                    // a successfully authn request receives the response
                    assertTrue(responseLatch.await(10L, TimeUnit.SECONDS));
                    // and the connection is NOT closed afterwards
                    logger.info("Waiting to ensure connection does not close");
                    expectThrows(TimeoutException.class, () -> closeFuture.get(5L, TimeUnit.SECONDS));
                    connectionTestDone.countDown();
                }, e -> {
                    connectionTestDone.countDown();
                    fail("Connection could not be established but should've");
                    throw new RuntimeException(e);
                }));
                assertTrue(connectionTestDone.await(10L, TimeUnit.SECONDS));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void testSniffStrategyNoConnectionWhenAuthenticatorAlwaysFails() throws Exception {
        // all requests fail authn
        doAnswer(invocation -> {
            ((ActionListener<Void>) invocation.getArguments()[1]).onFailure(new ElasticsearchSecurityException("failed authn"));
            return null;
        }).when(remoteCrossClusterAccessAuthenticationService).tryAuthenticate(any(Map.class), anyActionListener());
        Settings localSettings = Settings.builder()
            .put(onlyRole(DiscoveryNodeRole.REMOTE_CLUSTER_CLIENT_ROLE))
            .put(RemoteConnectionStrategy.REMOTE_CONNECTION_MODE.getConcreteSettingForNamespace(remoteClusterName).getKey(), "sniff")
            .put(
                SniffConnectionStrategy.REMOTE_CLUSTER_SEEDS.getConcreteSettingForNamespace(remoteClusterName).getKey(),
                remoteTransportService.boundRemoteAccessAddress().publishAddress().toString()
            )
            .put(
                SniffConnectionStrategy.REMOTE_CONNECTIONS_PER_CLUSTER.getKey(),
                randomIntBetween(1, 3) // easier to debug with just 1 connection
            )
            .put(
                SniffConnectionStrategy.REMOTE_NODE_CONNECTIONS.getConcreteSettingForNamespace(remoteClusterName).getKey(),
                randomIntBetween(1, 3) // easier to debug with just 1 connection
            )
            .build();
        {
            final MockSecureSettings secureSettings = new MockSecureSettings();
            secureSettings.setString(
                RemoteClusterService.REMOTE_CLUSTER_CREDENTIALS.getConcreteSettingForNamespace(remoteClusterName).getKey(),
                randomAlphaOfLength(20)
            );
            localSettings = Settings.builder().put(localSettings).setSecureSettings(secureSettings).build();
        }
        try (
            MockTransportService localService = MockTransportService.createNewService(
                localSettings,
                VersionInformation.CURRENT,
                TransportVersion.current(),
                threadPool
            )
        ) {
            localService.start();
            RemoteClusterService remoteClusterService = localService.getRemoteClusterService();
            // obtain some connections and check that they'll be promptly closed
            for (int i = 0; i < randomIntBetween(4, 16); i++) {
                CountDownLatch connectionTestDone = new CountDownLatch(1);
                PlainActionFuture<Void> closeFuture = PlainActionFuture.newFuture();
                // the failed authentication during handshake must surely close the connection before
                // {@code RemoteClusterNodesAction.NAME} is executed, so node sniffing will fail
                remoteClusterService.maybeEnsureConnectedAndGetConnection(remoteClusterName, true, ActionListener.wrap(connection -> {
                    connectionTestDone.countDown();
                    fail("No connection should be available, because node sniffing should fail on connection closed");
                }, e -> {
                    logger.info("No connection could be established");
                    assertThat(e, either(instanceOf(NoSeedNodeLeftException.class)).or(instanceOf(NoSuchRemoteClusterException.class)));
                    closeFuture.onResponse(null);
                    connectionTestDone.countDown();
                }));
                closeFuture.get(10L, TimeUnit.SECONDS);
                assertTrue(connectionTestDone.await(10L, TimeUnit.SECONDS));
            }
        }
    }

}
