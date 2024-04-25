/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.logging.plugin;

import org.elasticsearch.plugins.Plugin;

/**
 * Dummy plugin, does not implement any methods, only exists in order to allow {@link CustomDataProvider} to be loaded via SPI.
 */
public class TestLoggingPlugin extends Plugin {

}
