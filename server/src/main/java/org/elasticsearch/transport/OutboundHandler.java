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
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.Releasable;
import org.elasticsearch.core.Releasables;
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
        sendMessage(channel, message, () -> {
            try {
                messageListener.onRequestSent(node, requestId, action, request, options);
            } finally {
                request.decRef();
            }
        });
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
        try {
            sendMessage(channel, message, () -> {
                try {
                    messageListener.onResponseSent(requestId, action, response);
                } finally {
                    response.decRef();
                }
            });
        } catch (Exception e) {
            if ( e instanceof MsgTooBigException) {
                sendErrorResponse(nodeVersion, channel, requestId, action, e);
            } else {
                throw e;
            }
        }
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
        sendMessage(channel, message, () -> messageListener.onResponseSent(requestId, action, error));
    }

    private void sendMessage(TcpChannel channel, OutboundMessage networkMessage, Releasable onAfter) throws IOException {
        final RecyclerBytesStreamOutput byteStreamOutput;
        boolean bufferSuccess = false;
        try {
            byteStreamOutput = new RecyclerBytesStreamOutput(recycler);
            bufferSuccess = true;
        } finally {
            if (bufferSuccess == false) {
                Releasables.closeExpectNoException(onAfter);
            }
        }
        final Releasable release = Releasables.wrap(byteStreamOutput, onAfter);
        final BytesReference message;
        boolean serializeSuccess = false;
        try {
            message = networkMessage.serialize(byteStreamOutput);
            if (networkMessage.isResponse() && !networkMessage.isError()) {
                final int messageLength = message.getInt(TcpHeader.MARKER_BYTES_SIZE);
                if (messageLength > TcpTransport.THIRTY_PER_HEAP_SIZE) {
                    throw new MsgTooBigException("illegal transport message of size [" + ByteSizeValue.ofBytes(messageLength) +
                        "] which exceeds 30% of this node's heap size [" + ByteSizeValue.ofBytes(TcpTransport.THIRTY_PER_HEAP_SIZE) + "]");
                }
            }
            serializeSuccess = true;
        } catch (Exception e) {
            logger.warn(() -> "failed to serialize outbound message [" + networkMessage + "]", e);
            throw e;
        } finally {
            if (serializeSuccess == false) {
                release.close();
            }
        }
        internalSend(channel, message, networkMessage, ActionListener.wrap(release::close));
    }

    private void internalSend(
        TcpChannel channel,
        BytesReference reference,
        @Nullable OutboundMessage message,
        ActionListener<Void> listener
    ) {
        final long startTime = threadPool.rawRelativeTimeInMillis();
        channel.getChannelStats().markAccessed(startTime);
        final long messageSize = reference.length();
        TransportLogger.logOutboundMessage(channel, reference);
        // stash thread context so that channel event loop is not polluted by thread context
        try (ThreadContext.StoredContext existing = threadPool.getThreadContext().stashContext()) {
            channel.sendMessage(reference, new ActionListener<>() {
                @Override
                public void onResponse(Void v) {
                    statsTracker.markBytesWritten(messageSize);
                    listener.onResponse(v);
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
                    listener.onFailure(e);
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
                                messageSize,
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
            Releasables.closeExpectNoException(() -> listener.onFailure(ex), () -> CloseableChannel.closeChannel(channel));
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
