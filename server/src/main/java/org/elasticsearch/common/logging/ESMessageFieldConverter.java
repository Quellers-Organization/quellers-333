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

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.PatternParser;
import org.apache.logging.log4j.util.StringBuilders;

/**
 * Pattern converter to format ...
 */
@Plugin(category = PatternConverter.CATEGORY, name = "ESMessageField")
@ConverterKeys({"ESMessageField"})
public final class ESMessageFieldConverter extends LogEventPatternConverter {

    private String key;

    /**
     * Called by log4j2 to initialize this converter.
     */
    public static ESMessageFieldConverter newInstance(final Configuration config, final String[] options) {
        final String key = options[0];

        return new ESMessageFieldConverter(key);
    }

    public ESMessageFieldConverter(String key) {
        super("ESMessageField", "ESMessageField");
        this.key = key;
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        if (event.getMessage() instanceof ESLogMessage) {//TODO this vs instanceof
            ESLogMessage ESLogMessage = (ESLogMessage)event.getMessage();
            final Object value = ESLogMessage.getValueFor(key);
            if (value != null) {
                StringBuilders.appendValue(toAppendTo, value);
            }
        }
    }
}
