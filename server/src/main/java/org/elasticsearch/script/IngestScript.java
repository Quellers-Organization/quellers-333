
/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.script;

import org.elasticsearch.core.TimeValue;

import java.util.Map;

/**
 * A script used by the Ingest Script Processor.
 */
public abstract class IngestScript {

    public static final String[] PARAMETERS = {};

    /** The context used to compile {@link IngestScript} factories. */
    public static final ScriptContext<Factory> CONTEXT = new ScriptContext<>(
        "ingest",
        Factory.class,
        200,
        TimeValue.timeValueMillis(0),
        false,
        true
    );

    /** The generic runtime parameters for the script. */
    private final Map<String, Object> params;

    /** The metadata available to the script */
    private final Metadata metadata;

    /** The metadata and source available to the script */
    private final Map<String, Object> ctx;

    public IngestScript(Map<String, Object> params, Metadata metadata, Map<String, Object> ctx) {
        this.params = params;
        this.metadata = metadata;
        this.ctx = ctx;
    }

    /** Return the parameters for this script. */
    public Map<String, Object> getParams() {
        return params;
    }

    /** Provides backwards compatibility access to ctx */
    public Map<String, Object> getCtx() {
        return ctx;
    }

    /** Return the ingest metadata object */
    public Metadata metadata() {
        return metadata;
    }

    public abstract void execute();

    public interface Factory {
        IngestScript newInstance(Map<String, Object> params, Metadata metadata, Map<String, Object> ctx);
    }
}
