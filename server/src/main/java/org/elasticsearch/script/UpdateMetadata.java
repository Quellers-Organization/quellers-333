/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.script;

import org.elasticsearch.common.util.Maps;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class UpdateMetadata extends SourceAndMetadataMap {
    protected static final String OP = "op";
    protected static final String TIMESTAMP = "_now";
    protected static final String SOURCE = "_source";

    private static final String LEGACY_NOOP_STRING = "none";
    protected static final Set<String> VALID_OPS = Set.of("noop", "index", "delete", LEGACY_NOOP_STRING);

    public static Map<String, Validator> VALIDATORS = Map.of(
        INDEX,
        UpdateMetadata::setOnceStringValidator,
        ID,
        UpdateMetadata::setOnceStringValidator,
        VERSION,
        UpdateMetadata::setOnceLongValidator,
        ROUTING,
        UpdateMetadata::setOnceStringValidator,
        TYPE,
        UpdateMetadata::setOnceStringValidator,
        OP,
        opValidatorFromValidOps(VALID_OPS),
        TIMESTAMP,
        UpdateMetadata::setOnceLongValidator
    );

    public UpdateMetadata(
        String index,
        String id,
        long version,
        String routing,
        String type,
        String op,
        long timestamp,
        Map<String, Object> source
    ) {
        super(wrapSource(source), metadataMap(index, id, version, routing, type, op, timestamp), VALIDATORS, TIMESTAMP, OP);
    }

    protected static Map<String, Object> wrapSource(Map<String, Object> source) {
        Map<String, Object> wrapper = Maps.newHashMapWithExpectedSize(1);
        wrapper.put(SOURCE, source);
        return wrapper;
    }

    protected static Map<String, Object> metadataMap(
        String index,
        String id,
        long version,
        String routing,
        String type,
        String op,
        long timestamp
    ) {
        Map<String, Object> metadata = Maps.newHashMapWithExpectedSize(VALIDATORS.size());
        metadata.put(INDEX, index);
        metadata.put(ID, id);
        metadata.put(VERSION, version);
        metadata.put(ROUTING, routing);
        metadata.put(TYPE, type);
        metadata.put(OP, op);
        metadata.put(TIMESTAMP, timestamp);
        return metadata;
    }

    @Override
    public boolean hasVersion() {
        return metadata.get(VERSION) != null;
    }

    @Override
    public long getVersion() {
        if (hasVersion() == false) {
            return Long.MIN_VALUE;
        }
        return super.getVersion();
    }

    @Override
    public String getOp() {
        String op = super.getOp();
        if (LEGACY_NOOP_STRING.equals(op)) {
            return "noop";
        }
        return op;
    }

    @Override
    public void setOp(String op) {
        if (LEGACY_NOOP_STRING.equals(op)) {
            throw new IllegalArgumentException(LEGACY_NOOP_STRING + " is deprecated, use 'noop' instead");
        }
        super.setOp(op);
    }

    public static void setOnceStringValidator(MapOperation op, String key, Object value) {
        if (op != MapOperation.INIT) {
            throw new IllegalArgumentException("Cannot " + op.name().toLowerCase(Locale.ROOT) + " key [" + key + "]");
        }
        stringValidator(op, key, value);
    }

    public static void setOnceLongValidator(MapOperation op, String key, Object value) {
        if (op != MapOperation.INIT) {
            throw new IllegalArgumentException("Cannot " + op.name().toLowerCase(Locale.ROOT) + " key [" + key + "]");
        }
        longValidator(op, key, value);
    }

    public static Validator opValidatorFromValidOps(Set<String> validOps) {
        return new Validator() {
            @Override
            public void accept(MapOperation op, String key, Object value) {
                if (op == MapOperation.REMOVE) {
                    throw new IllegalArgumentException("Cannot remove [" + key + "]");
                }
                if (value instanceof String opStr) {
                    if (validOps.contains(opStr)) {
                        return;
                    }
                    throw new IllegalArgumentException(
                        key + " must be one of " + validOps.stream().sorted().collect(Collectors.joining(",")) + ", not [" + opStr + "]"
                    );
                }
                throw new IllegalArgumentException(
                    key
                        + " must be String and one of "
                        + validOps.stream().sorted().collect(Collectors.joining(","))
                        + " but was ["
                        + value
                        + "] with type ["
                        + value.getClass().getName()
                        + "]"
                );
            }
        };
    }
}
