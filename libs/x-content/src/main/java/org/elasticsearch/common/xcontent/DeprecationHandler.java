/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.common.xcontent;

import java.util.function.Supplier;

/**
 * Callback for notifying the creator of the {@link XContentParser} that
 * parsing hit a deprecated field.
 */
public interface DeprecationHandler {
    /**
     * Throws an {@link UnsupportedOperationException} when parsing hits a
     * deprecated field. Use this when creating an {@link XContentParser}
     * that won't interact with deprecation logic at all or when you want
     * to fail fast when parsing deprecated fields.
     */
    DeprecationHandler THROW_UNSUPPORTED_OPERATION = new DeprecationHandler() {
        @Override
        public void usedDeprecatedField(String parserName, Supplier<XContentLocation> location, String usedName, String replacedWith) {
            if (parserName != null) {
                throw new UnsupportedOperationException("deprecated fields not supported in [" + parserName + "] but got ["
                    + usedName + "] at [" + location.get() + "] which is a deprecated name for [" + replacedWith + "]");
            } else {
                throw new UnsupportedOperationException("deprecated fields not supported here but got ["
                    + usedName + "] which is a deprecated name for [" + replacedWith + "]");
            }
        }
        @Override
        public void usedDeprecatedName(String parserName, Supplier<XContentLocation> location, String usedName, String modernName) {
            if (parserName != null) {
                throw new UnsupportedOperationException("deprecated fields not supported in [" + parserName + "] but got ["
                    + usedName + "] at [" + location.get() + "] which has been replaced with [" + modernName + "]");
            } else {
                throw new UnsupportedOperationException("deprecated fields not supported here but got ["
                    + usedName + "] which has been replaced with [" + modernName + "]");
            }
        }

        @Override
        public void usedDeprecatedField(String parserName, Supplier<XContentLocation> location, String usedName) {
            if (parserName != null) {
                throw new UnsupportedOperationException("deprecated fields not supported in [" + parserName + "] but got ["
                    + usedName + "] at [" + location.get() + "] which has been deprecated entirely");
            } else {
                throw new UnsupportedOperationException("deprecated fields not supported here but got ["
                    + usedName + "] which has been deprecated entirely");
            }
        }
    };

    /**
     * Ignores all deprecations
     */
    DeprecationHandler IGNORE_DEPRECATIONS = new DeprecationHandler() {
        @Override
        public void usedDeprecatedName(String parserName, Supplier<XContentLocation> location, String usedName, String modernName) {

        }

        @Override
        public void usedDeprecatedField(String parserName, Supplier<XContentLocation> location, String usedName, String replacedWith) {

        }

        @Override
        public void usedDeprecatedField(String parserName, Supplier<XContentLocation> location, String usedName) {

        }
    };

    /**
     * Called when the provided field name matches a deprecated name for the field.
     * @param usedName the provided field name
     * @param modernName the modern name for the field
     */
    void usedDeprecatedName(String parserName, Supplier<XContentLocation> location, String usedName, String modernName);

    /**
     * Called when the provided field name matches the current field but the entire
     * field has been marked as deprecated and another field should be used
     * @param usedName the provided field name
     * @param replacedWith the name of the field that replaced this field
     */
    void usedDeprecatedField(String parserName, Supplier<XContentLocation> location, String usedName, String replacedWith);

    /**
     * Called when the provided field name matches the current field but the entire
     * field has been marked as deprecated with no replacement
     * @param usedName the provided field name
     */
    void usedDeprecatedField(String parserName, Supplier<XContentLocation> location, String usedName);

}
