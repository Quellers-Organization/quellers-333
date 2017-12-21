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
package org.elasticsearch.common;

import org.elasticsearch.common.ParseField.DeprecationHandler;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.logging.Loggers;

public class LoggingDeprecationHandler implements DeprecationHandler {
    public static LoggingDeprecationHandler INSTANCE = new LoggingDeprecationHandler();
    private static final DeprecationLogger DEPRECATION_LOGGER = new DeprecationLogger(Loggers.getLogger(ParseField.class));

    private LoggingDeprecationHandler() {
        // Singleton
    }

    @Override
    public void usedDeprecatedName(String usedName, String modernName) {
        DEPRECATION_LOGGER.deprecated("Deprecated field [{}] used, expected [{}] instead", usedName, modernName);
    }

    @Override
    public void usedDeprecatedField(String usedName, String replacedWith) {
        DEPRECATION_LOGGER.deprecated("Deprecated field [{}] used, replaced by [{}]", usedName, replacedWith);
    }
}
