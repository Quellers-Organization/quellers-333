/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.transport;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.RecyclerBytesStreamOutput;
import org.elasticsearch.common.network.CloseableChannel;
import org.elasticsearch.common.network.HandlingTimeTracker;
import org.elasticsearch.common.recycler.Recycler;
import org.elasticsearch.common.transport.NetworkExceptionHelper;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.threadpool.ThreadPool;

import java.io.IOException;

final class OutboundHandler {

    private static final Logger logger = LogManager.getLogger(OutboundHandler.class);

    private final String nodeName;
    private final Version version;
    private final StatsTracker statsTracker;
    private final ThreadPool threadPool;
    private final Recycler<BytesRef> recycler;
    private final HandlingTimeTracker handlingTimeTracker;
    private final boolean rstOnClose;

    private volatile long slowLogThresholdMs = Long.MAX_VALUE;

    private volatile TransportMessageListener messageListener = TransportMessageListener.NOOP_LISTENER;

    OutboundHandler(
        String nodeName,
        Version version,
        StatsTracker statsTracker,
        ThreadPool threadPool,
        Recycler<BytesRef> recycler,
        HandlingTimeTracker handlingTimeTracker,
        boolean rstOnClose
    ) {
        this.nodeName = nodeName;
        this.version = version;
        this.statsTracker = statsTracker;
        this.threadPool = threadPool;
        this.recycler = recycler;
        this.handlingTimeTracker = handlingTimeTracker;
        this.rstOnClose = rstOnClose;
    }

    void setSlowLogThreshold(TimeValue slowLogThreshold) {
        this.slowLogThresholdMs = slowLogThreshold.getMillis();
    }

    void sendBytes(TcpChannel channel, BytesReference bytes, ActionListener<Void> listener) {
        internalSend(channel, OutboundMessage.SerializedBytes.fromBytesReference(bytes), null, listener);
    }

    void sendBytes(TcpChannel channel, OutboundMessage.SerializedBytes bytes, ActionListener<Void> listener) {
        internalSend(channel, bytes, null, listener);
    }

    /**
     * Sends the request to the given channel. This method should be used to send {@link TransportRequest}
     * objects back to the caller.
     */
    void sendRequest(
        final DiscoveryNode node,
        final TcpChannel channel,
        final long requestId,
        final String action,
        final TransportRequest request,
        final TransportRequestOptions options,
        final Version channelVersion,
        final Compression.Scheme compressionScheme,
        final boolean isHandshake
    ) throws IOException, TransportException {
        Version version = Version.min(this.version, channelVersion);
        OutboundMessage.Request message = new OutboundMessage.Request(
            threadPool.getThreadContext(),
            request,
            version,
            action,
            requestId,
            isHandshake,
            compressionScheme
        );
        if (request.tryIncRef() == false) {
            assert false : "request [" + request + "] has been released already";
            throw new AlreadyClosedException("request [" + request + "] has been released already");
        }
        ActionListener<Void> listener = ActionListener.wrap(() -> {
            try {
                messageListener.onRequestSent(node, requestId, action, request, options);
            } finally {
                request.decRef();
            }
        });
        sendMessage(channel, message, listener);
    }

    /**
     * Sends the response to the given channel. This method should be used to send {@link TransportResponse}
     * objects back to the caller.
     *
     * @see #sendErrorResponse(Version, TcpChannel, long, String, Exception) for sending error responses
     */
    void sendResponse(
        final Version nodeVersion,
        final TcpChannel channel,
        final long requestId,
        final String action,
        final TransportResponse response,
        final Compression.Scheme compressionScheme,
        final boolean isHandshake
    ) throws IOException {
        Version version = Version.min(this.version, nodeVersion);
        OutboundMessage.Response message = new OutboundMessage.Response(
            threadPool.getThreadContext(),
            response,
            version,
            requestId,
            isHandshake,
            compressionScheme
        );
        ActionListener<Void> listener = ActionListener.wrap(() -> {
            try {
                messageListener.onResponseSent(requestId, action, response);
            } finally {
                response.decRef();
            }
        });
        sendMessage(channel, message, listener);
    }

    /**
     * Sends back an error response to the caller via the given channel
     */
    void sendErrorResponse(
        final Version nodeVersion,
        final TcpChannel channel,
        final long requestId,
        final String action,
        final Exception error
    ) throws IOException {
        Version version = Version.min(this.version, nodeVersion);
        RemoteTransportException tx = new RemoteTransportException(nodeName, channel.getLocalAddress(), action, error);
        OutboundMessage.Response message = new OutboundMessage.Response(threadPool.getThreadContext(), tx, version, requestId, false, null);
        ActionListener<Void> listener = ActionListener.wrap(() -> messageListener.onResponseSent(requestId, action, error));
        sendMessage(channel, message, listener);
    }

    private void sendMessage(TcpChannel channel, OutboundMessage networkMessage, ActionListener<Void> listener) throws IOException {
        final OutboundMessage.SerializedBytes message;
        try (RecyclerBytesStreamOutput byteStreamOutput = new RecyclerBytesStreamOutput(recycler)) {
            message = networkMessage.serialize(byteStreamOutput);
        } catch (Exception e) {
            logger.warn(() -> "failed to serialize outbound message [" + networkMessage + "]", e);
            listener.onFailure(e);
            throw e;
        }
        internalSend(channel, message, networkMessage, listener);
    }

    private void internalSend(
        TcpChannel channel,
        OutboundMessage.SerializedBytes serializedBytes,
        @Nullable OutboundMessage message,
        ActionListener<Void> listener
    ) {
        final ActionListener<Void> wrappedListener = ActionListener.runBefore(listener, serializedBytes::close);

        final long startTime = threadPool.rawRelativeTimeInMillis();
        channel.getChannelStats().markAccessed(startTime);
        TransportLogger.logOutboundMessage(channel, serializedBytes);
        // stash thread context so that channel event loop is not polluted by thread context
        try (ThreadContext.StoredContext existing = threadPool.getThreadContext().stashContext()) {
            channel.sendMessage(serializedBytes, new ActionListener<>() {
                @Override
                public void onResponse(Void v) {
                    statsTracker.markBytesWritten(serializedBytes.messageLength());
                    wrappedListener.onResponse(v);
                    maybeLogSlowMessage(true);
                }

                @Override
                public void onFailure(Exception e) {
                    final Level closeConnectionExceptionLevel = NetworkExceptionHelper.getCloseConnectionExceptionLevel(e, rstOnClose);
                    if (closeConnectionExceptionLevel == Level.OFF) {
                        logger.warn(() -> "send message failed [channel: " + channel + "]", e);
                    } else if (closeConnectionExceptionLevel == Level.INFO && logger.isDebugEnabled() == false) {
                        logger.info("send message failed [channel: {}]: {}", channel, e.getMessage());
                    } else {
                        logger.log(closeConnectionExceptionLevel, () -> "send message failed [channel: " + channel + "]", e);
                    }
                    wrappedListener.onFailure(e);
                    maybeLogSlowMessage(false);
                }

                private void maybeLogSlowMessage(boolean success) {
                    final long logThreshold = slowLogThresholdMs;
                    if (logThreshold > 0) {
                        final long took = threadPool.rawRelativeTimeInMillis() - startTime;
                        handlingTimeTracker.addHandlingTime(took);
                        if (took > logThreshold) {
                            logger.warn(
                                "sending transport message [{}] of size [{}] on [{}] took [{}ms] which is above the warn "
                                    + "threshold of [{}ms] with success [{}]",
                                message,
                                serializedBytes.messageLength(),
                                channel,
                                took,
                                logThreshold,
                                success
                            );
                        }
                    }
                }
            });
        } catch (RuntimeException ex) {
            wrappedListener.onFailure(ex);
            CloseableChannel.closeChannel(channel);
            throw ex;
        }
    }

    void setMessageListener(TransportMessageListener listener) {
        if (messageListener == TransportMessageListener.NOOP_LISTENER) {
            messageListener = listener;
        } else {
            throw new IllegalStateException("Cannot set message listener twice");
        }
    }

    public boolean rstOnClose() {
        return rstOnClose;
    }

}
