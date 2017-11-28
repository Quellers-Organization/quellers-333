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

package org.elasticsearch.transport.nio.channel;

import org.elasticsearch.common.bytes.ByteBufferReference;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.bytes.CompositeBytesReference;
import org.elasticsearch.transport.nio.InboundChannelBuffer;
import org.elasticsearch.transport.nio.TcpReadHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

public class TcpReadContext implements ReadContext {

    private final TcpReadHandler handler;
    private final TcpNioSocketChannel channel;
    private final TcpFrameDecoder frameDecoder;
    private final InboundChannelBuffer channelBuffer = new InboundChannelBuffer();

    public TcpReadContext(NioSocketChannel channel, TcpReadHandler handler) {
        this((TcpNioSocketChannel) channel, handler, new TcpFrameDecoder());
    }

    public TcpReadContext(TcpNioSocketChannel channel, TcpReadHandler handler, TcpFrameDecoder frameDecoder) {
        this.handler = handler;
        this.channel = channel;
        this.frameDecoder = frameDecoder;
    }

    @Override
    public int read() throws IOException {
        if (channelBuffer.getRemaining() == 0) {
            channelBuffer.expandCapacity(channelBuffer.getCapacity() + InboundChannelBuffer.PAGE_SIZE);
        }

        int bytesRead = channel.read(channelBuffer);

        if (bytesRead == -1) {
            return bytesRead;
        }

        BytesReference message;

        // Frame decoder will throw an exception if the message is improperly formatted, the header is incorrect,
        // or the message is corrupted
        while ((message = frameDecoder.decode(toBytesReference(channelBuffer))) != null) {
            int messageLengthWithHeader = message.length();

            try {
                BytesReference messageWithoutHeader = message.slice(6, message.length() - 6);

                // A message length of 6 bytes it is just a ping. Ignore for now.
                if (messageLengthWithHeader != 6) {
                    handler.handleMessage(messageWithoutHeader, channel, messageWithoutHeader.length());
                }
            } catch (Exception e) {
                handler.handleException(channel, e);
            } finally {
                channelBuffer.releasePagesFromHead(messageLengthWithHeader);
            }
        }

        return bytesRead;
    }

    private static BytesReference toBytesReference(InboundChannelBuffer channelBuffer) {
        ByteBuffer[] writtenToBuffers = channelBuffer.getPreIndexBuffers();
        ByteBufferReference[] references = new ByteBufferReference[writtenToBuffers.length];
        for (int i = 0; i < references.length; ++i) {
            references[i] = new ByteBufferReference(writtenToBuffers[i]);
        }

        return new CompositeBytesReference(references);
    }

}
