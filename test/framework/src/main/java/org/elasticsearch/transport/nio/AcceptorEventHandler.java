/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package org.elasticsearch.transport.nio;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.transport.nio.channel.ChannelFactory;
import org.elasticsearch.transport.nio.channel.NioChannel;
import org.elasticsearch.transport.nio.channel.NioServerSocketChannel;
import org.elasticsearch.transport.nio.channel.NioSocketChannel;

import java.io.IOException;
import java.util.function.Consumer;

public class AcceptorEventHandler extends EventHandler {

    private final ChildSelectorStrategy strategy;
    private final OpenChannels openChannels;
    private final AcceptedChannelCloseListener closeListener = new AcceptedChannelCloseListener();

    public AcceptorEventHandler(Logger logger, OpenChannels openChannels, ChildSelectorStrategy strategy) {
        super(logger);
        this.openChannels = openChannels;
        this.strategy = strategy;
    }

    public void serverChannelRegistered(NioServerSocketChannel nioServerSocketChannel) {
        openChannels.serverChannelOpened(nioServerSocketChannel);
    }

    public void acceptChannel(NioServerSocketChannel nioChannel) throws IOException {
        ChannelFactory channelFactory = nioChannel.getChannelFactory();
        NioSocketChannel nioSocketChannel = channelFactory.acceptNioChannel(nioChannel);
        openChannels.acceptedChannelOpened(nioSocketChannel);
        nioSocketChannel.getCloseFuture().setListener(closeListener);
        strategy.next().registerSocketChannel(nioSocketChannel);
    }

    public void acceptException(NioServerSocketChannel nioChannel, IOException exception) {
        logger.trace("exception while accepting new channel", exception);
    }

    public void genericServerChannelException(NioServerSocketChannel channel, Exception e) {
        logger.trace("event handling exception", e);
    }

    class AcceptedChannelCloseListener implements Consumer<NioChannel> {

        @Override
        public void accept(final NioChannel channel) {
            openChannels.channelClosed(channel);
        }
    }
}
