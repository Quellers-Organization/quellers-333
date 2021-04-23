/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.common.ssl;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A TrustConfiguration that merges trust anchors from a number of other trust configs to produce a single {@link X509ExtendedTrustManager}.
 */
class CompositeTrustConfig implements SslTrustConfig {
    private final List<SslTrustConfig> configs;

    public CompositeTrustConfig(List<SslTrustConfig> configs) {
        this.configs = configs;
    }

    @Override
    public Collection<Path> getDependentFiles() {
        return configs.stream().map(SslTrustConfig::getDependentFiles).flatMap(Collection::stream).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public X509ExtendedTrustManager createTrustManager() {
        try {
            Collection<Certificate> trustedIssuers = configs.stream()
                .map(c -> c.createTrustManager())
                .map(tm -> tm.getAcceptedIssuers())
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());
            final KeyStore store = KeyStoreUtil.buildTrustStore(trustedIssuers);
            return KeyStoreUtil.createTrustManager(store, TrustManagerFactory.getDefaultAlgorithm());
        } catch (GeneralSecurityException e) {
            throw new SslConfigException("Cannot combine trust configurations ["
                + configs.stream().map(SslTrustConfig::toString).collect(Collectors.joining(","))
                + "]",
                e);
        }
    }

    @Override
    public Collection<? extends StoredCertificate> getConfiguredCertificates() {
        return configs.stream().map(SslTrustConfig::getConfiguredCertificates)
            .flatMap(Collection::stream)
            .collect(Collectors.toUnmodifiableList());
    }
}
