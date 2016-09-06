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

package org.elasticsearch.common.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

class PrefixLogger extends ExtendedLoggerWrapper {

    private final String prefix;
    private final Marker marker;

    public String prefix() {
        return prefix;
    }

    private static final WeakHashMap<String, WeakReference<Marker>> markers = new WeakHashMap<>();

    PrefixLogger(final ExtendedLogger logger, final String name, final String prefix) {
        super(logger, name, null);
        this.prefix = prefix;

        final String actualPrefix = (prefix == null ? "[] " : prefix).intern();
        final Marker actualMarker;
        synchronized (markers) {
            final WeakReference<Marker> marker = markers.get(actualPrefix);
            if (marker == null || marker.get() == null) {
                actualMarker = new MarkerManager.Log4jMarker(actualPrefix);
                markers.put(actualPrefix, new WeakReference<>(actualMarker));
            } else {
                actualMarker = marker.get();
            }
        }
        this.marker = actualMarker;
    }

    @Override
    public void logMessage(final String fqcn, final Level level, final Marker marker, final Message message, final Throwable t) {
        assert marker == null;
        super.logMessage(fqcn, level, this.marker, message, t);
    }

}
