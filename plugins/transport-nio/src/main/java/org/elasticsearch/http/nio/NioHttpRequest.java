package org.elasticsearch.http.nio;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.nio.NioSocketChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.transport.netty4.Netty4Utils;

import java.net.SocketAddress;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class NioHttpRequest extends RestRequest {

    private final FullHttpRequest request;
    private final NioSocketChannel channel;
    private final BytesReference content;

    NioHttpRequest(NamedXContentRegistry xContentRegistry, FullHttpRequest request, NioSocketChannel channel) {
        super(xContentRegistry, request.uri(), new HttpHeadersMap(request.headers()));
        this.request = request;
        this.channel = channel;
        if (request.content().isReadable()) {
            this.content = Netty4Utils.toBytesReference(request.content());
        } else {
            this.content = BytesArray.EMPTY;
        }
    }

    FullHttpRequest request() {
        return this.request;
    }

    @Override
    public Method method() {
        HttpMethod httpMethod = request.method();
        if (httpMethod == HttpMethod.GET)
            return Method.GET;

        if (httpMethod == HttpMethod.POST)
            return Method.POST;

        if (httpMethod == HttpMethod.PUT)
            return Method.PUT;

        if (httpMethod == HttpMethod.DELETE)
            return Method.DELETE;

        if (httpMethod == HttpMethod.HEAD) {
            return Method.HEAD;
        }

        if (httpMethod == HttpMethod.OPTIONS) {
            return Method.OPTIONS;
        }

        return Method.GET;
    }

    @Override
    public String uri() {
        return request.uri();
    }

    @Override
    public boolean hasContent() {
        return content.length() > 0;
    }

    @Override
    public BytesReference content() {
        return content;
    }

    /**
     * Returns the remote address where this rest request channel is "connected to".  The
     * returned {@link SocketAddress} is supposed to be down-cast into more
     * concrete type such as {@link java.net.InetSocketAddress} to retrieve
     * the detailed information.
     */
    @Override
    public SocketAddress getRemoteAddress() {
        return channel.getRemoteAddress();
    }

    /**
     * Returns the local address where this request channel is bound to.  The returned
     * {@link SocketAddress} is supposed to be down-cast into more concrete
     * type such as {@link java.net.InetSocketAddress} to retrieve the detailed
     * information.
     */
    @Override
    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    /**
     * A wrapper of {@link HttpHeaders} that implements a map to prevent copying unnecessarily. This class does not support modifications
     * and due to the underlying implementation, it performs case insensitive lookups of key to values.
     *
     * It is important to note that this implementation does have some downsides in that each invocation of the
     * {@link #values()} and {@link #entrySet()} methods will perform a copy of the values in the HttpHeaders rather than returning a
     * view of the underlying values.
     */
    private static class HttpHeadersMap implements Map<String, List<String>> {

        private final HttpHeaders httpHeaders;

        private HttpHeadersMap(HttpHeaders httpHeaders) {
            this.httpHeaders = httpHeaders;
        }

        @Override
        public int size() {
            return httpHeaders.size();
        }

        @Override
        public boolean isEmpty() {
            return httpHeaders.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return key instanceof String && httpHeaders.contains((String) key);
        }

        @Override
        public boolean containsValue(Object value) {
            return value instanceof List && httpHeaders.names().stream().map(httpHeaders::getAll).anyMatch(value::equals);
        }

        @Override
        public List<String> get(Object key) {
            return key instanceof String ? httpHeaders.getAll((String) key) : null;
        }

        @Override
        public List<String> put(String key, List<String> value) {
            throw new UnsupportedOperationException("modifications are not supported");
        }

        @Override
        public List<String> remove(Object key) {
            throw new UnsupportedOperationException("modifications are not supported");
        }

        @Override
        public void putAll(Map<? extends String, ? extends List<String>> m) {
            throw new UnsupportedOperationException("modifications are not supported");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("modifications are not supported");
        }

        @Override
        public Set<String> keySet() {
            return httpHeaders.names();
        }

        @Override
        public Collection<List<String>> values() {
            return httpHeaders.names().stream().map(k -> Collections.unmodifiableList(httpHeaders.getAll(k))).collect(Collectors.toList());
        }

        @Override
        public Set<Entry<String, List<String>>> entrySet() {
            return httpHeaders.names().stream().map(k -> new AbstractMap.SimpleImmutableEntry<>(k, httpHeaders.getAll(k)))
                .collect(Collectors.toSet());
        }
    }
}
