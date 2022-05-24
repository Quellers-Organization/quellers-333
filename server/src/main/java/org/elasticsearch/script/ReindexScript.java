
/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.script;

import org.elasticsearch.script.field.AbstractBulkMetadata;
import org.elasticsearch.script.field.Op;

import java.util.Map;

/**
 * A script for reindex.
 *
 * Metadata
 *   RW: _index (non-null), _id, _routing, _version, _op {@link org.elasticsearch.script.field.Op} One of NOOP, INDEX, DELETE
 */
public abstract class ReindexScript {

    public static final String[] PARAMETERS = {};

    /** The context used to compile {@link ReindexScript} factories. */
    public static final ScriptContext<Factory> CONTEXT = new ScriptContext<>("reindex", Factory.class);

    /** The generic runtime parameters for the script. */
    private final Map<String, Object> params;

    private final Metadata metadata;

    public ReindexScript(Map<String, Object> params, Metadata metadata) {
        this.params = params;
        this.metadata = metadata;
    }

    /** Return the parameters for this script. */
    public Map<String, Object> getParams() {
        return params;
    }

    public Metadata meta() {
        return metadata;
    }

    public Map<String, Object> getCtx() {
        return metadata != null ? metadata.getCtx() : null;
    }

    public abstract void execute();

    public interface Factory {
        ReindexScript newInstance(Map<String, Object> params, Metadata metadata);
    }

    /**
     *  Metadata
     *    RW: _index (non-null), _id, _routing, _version, _op {@link org.elasticsearch.script.field.Op} One of NOOP, INDEX, DELETE
     */
    public static class Metadata extends AbstractBulkMetadata {
        public Metadata(String index, String id, Long version, String routing, Op op, Map<String, Object> source) {
            super(index, id, version, routing, op, source);
        }

        public void setIndex(String index) {
            if (index == null) {
                throw new NullPointerException("destination index must be non-null");
            }
            store.setIndex(index);
        }

        public String getIndex() {
            String index = store.getIndex();
            if (index == null) {
                throw new NullPointerException("destination index must be non-null");
            }
            return index;
        }

        public void setId(String id) {
            store.setId(id);
        }

        public void setRouting(String routing) {
            store.setRouting(routing);
        }

        public void setVersion(Long version) {
            store.setVersion(version);
        }
    }
}
