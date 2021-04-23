/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.common.ssl;

import javax.net.ssl.X509ExtendedTrustManager;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A {@link SslTrustConfig} that builds a Trust Manager from a keystore file.
 */
final class StoreTrustConfig implements SslTrustConfig {
    private final Path path;
    private final char[] password;
    private final String type;
    private final String algorithm;
    private final boolean requireTrustAnchors;

    /**
     * @param path      The path to the keystore file
     * @param password  The password for the keystore
     * @param type      The {@link KeyStore#getType() type} of the keystore (typically "PKCS12" or "jks").
 *                  See {@link KeyStoreUtil#inferKeyStoreType(Path)}.
     * @param algorithm The algorithm to use for the Trust Manager (see {@link javax.net.ssl.TrustManagerFactory#getAlgorithm()}).
     * @param requireTrustAnchors If true, the truststore will be checked to ensure that it contains at least one valid trust anchor.
     */
    StoreTrustConfig(Path path, char[] password, String type, String algorithm, boolean requireTrustAnchors) {
        this.path = Objects.requireNonNull(path, "Truststore path cannot be null");
        this.type = Objects.requireNonNull(type, "Truststore type cannot be null");
        this.algorithm = Objects.requireNonNull(algorithm, "Truststore algorithm cannot be null");
        this.password = Objects.requireNonNull(password, "Truststore password cannot be null (but may be empty)");
        this.requireTrustAnchors = requireTrustAnchors;
    }

    @Override
    public Collection<Path> getDependentFiles() {
        return List.of(path);
    }

    @Override
    public Collection<? extends StoredCertificate> getConfiguredCertificates() {
        try {
            final KeyStore trustStore = readKeyStore();
            return KeyStoreUtil.stream(trustStore)
                .map(entry -> {
                    try {
                        final X509Certificate certificate = entry.getX509Certificate();
                        if (certificate != null) {
                            final boolean hasKey = entry.isKeyEntry();
                            return new StoredCertificate(certificate, this.path, this.type, entry.getAlias(), hasKey);
                        } else {
                            return null;
                        }
                    } catch (KeyStoreException ex) {
                        throw keystoreException(ex, "read keystore certificates");
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
        } catch (GeneralSecurityException e) {
            throw keystoreException(e, "process keystore");
        }
    }

    @Override
    public X509ExtendedTrustManager createTrustManager() {
        try {
            final KeyStore store = readKeyStore();
            if (requireTrustAnchors) {
                checkTrustStore(store);
            }
            return KeyStoreUtil.createTrustManager(store, algorithm);
        } catch (GeneralSecurityException e) {
            throw keystoreException(e, "create trust manager from keystore");
        }
    }

    private KeyStore readKeyStore() {
        try {
            return KeyStoreUtil.readKeyStore(path, type, password);
        } catch (GeneralSecurityException e) {
            throw keystoreException(e, "read keystore");
        }
    }

    private SslConfigException keystoreException(GeneralSecurityException e, String context) {
        return new SslConfigException("failed to " + context + " for path=[" + path.toAbsolutePath()
            + "] type=[" + type + "] password=[" + (password.length == 0 ? "<empty>" : "<non-empty>") + "]", e);
    }

    /**
     * Verifies that the keystore contains at least 1 trusted certificate entry.
     */
    private void checkTrustStore(KeyStore store) throws GeneralSecurityException {
        Enumeration<String> aliases = store.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (store.isCertificateEntry(alias)) {
                return;
            }
        }
        throw new SslConfigException("the truststore [" + path + "] does not contain any trusted certificate entries");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoreTrustConfig that = (StoreTrustConfig) o;
        return path.equals(that.path)
            && Arrays.equals(password, that.password)
            && type.equals(that.type)
            && algorithm.equals(that.algorithm);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(path, type, algorithm);
        result = 31 * result + Arrays.hashCode(password);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StoreTrustConfig{");
        sb.append("path=").append(path);
        sb.append(", password=").append(password.length == 0 ? "<empty>" : "<non-empty>");
        sb.append(", type=").append(type);
        sb.append(", algorithm=").append(algorithm);
        sb.append('}');
        return sb.toString();
    }
}
