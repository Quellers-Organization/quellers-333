/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.testclusters;

import com.google.protobuf.ByteString;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * This is a server which just accepts lines of JSON code and if the JSON
 * is valid and the root node is "transaction", then adds that JSON object
 * to a transaction list which is accessible externally to the class.
 * <p>
 * The Elastic agent sends lines of JSON code, and so this mock server
 * can be used as a basic APM server for testing.
 * <p>
 * The HTTP server used is the JDK embedded com.sun.net.httpserver
 */
public class MockGrpcApmServer {
    private static final Logger logger = Logging.getLogger(MockGrpcApmServer.class);
    private int port;

    public MockGrpcApmServer(int port) {
        this.port = port;
    }

    /**
     * Simple main that starts a mock APM server and prints the port it is
     * running on. This is not needed
     * for testing, it is just a convenient template for trying things out
     * if you want play around.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        MockGrpcApmServer server = new MockGrpcApmServer(9999);
        server.start();
        server.blockUntilShutdown();
    }
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    private Server server;

    /**
     * Start the Mock APM server. Just returns empty JSON structures for every incoming message
     *
     * @return - the port the Mock APM server started on
     * @throws IOException
     */
    public synchronized int start() throws IOException {
        if (server != null) {
            int port = server.getPort();
            logger.lifecycle("MockApmServer is already running. Reusing on address:port "+ port);
            return port;
        }


        server = ServerBuilder.forPort(port)
            .addService(new TraceServiceImpl())
            .addService(new MetricsServiceImpl())
            .build()
            .start();
        System.out.println("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            MockGrpcApmServer.this.stop();
            System.err.println("*** server shut down");
        }));
        logger.lifecycle("MockApmServer started on port " + server.getPort());
        return server.getPort();
    }

    public int getPort() {
        return port;
    }

    /**
     * Stop the server gracefully if possible
     */
    public synchronized void stop() {
        logger.lifecycle("stopping apm server");
        server.shutdownNow();
        server = null;
    }

    static class TraceServiceImpl extends TraceServiceGrpc.TraceServiceImplBase {
        @Override
        public void export(ExportTraceServiceRequest request, StreamObserver<ExportTraceServiceResponse> responseObserver) {
            super.export(request, responseObserver);
            System.out.println("TRACE SERVICE RECEIVED "+request.toString());
        }
    }

    static class MetricsServiceImpl extends MetricsServiceGrpc.MetricsServiceImplBase {
        @Override
        public void export(ExportMetricsServiceRequest request, StreamObserver<ExportMetricsServiceResponse> responseObserver) {
            System.out.println("METRICS SERVICE RECEIVED "+request.toString());
        }
    }
}
