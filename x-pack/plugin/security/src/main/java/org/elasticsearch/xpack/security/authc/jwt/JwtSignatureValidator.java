/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.authc.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.hash.MessageDigests;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.Releasable;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.xpack.core.security.authc.RealmConfig;
import org.elasticsearch.xpack.core.security.authc.RealmSettings;
import org.elasticsearch.xpack.core.security.authc.jwt.JwtRealmSettings;
import org.elasticsearch.xpack.core.ssl.SSLService;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public interface JwtSignatureValidator extends Releasable {

    Logger logger = LogManager.getLogger(JwtSignatureValidator.class);

    @Override
    default void close() {}

    void validate(String tokenPrincipal, SignedJWT jwt, ActionListener<Void> listener);

    class DelegatingJwtSignatureValidator implements JwtSignatureValidator {

        private static final Logger logger = LogManager.getLogger(DelegatingJwtSignatureValidator.class);

        private final RealmConfig realmConfig;
        final List<String> allowedJwksAlgsPkc;
        final List<String> allowedJwksAlgsHmac;
        @Nullable
        private final HmacJwtSignatureValidator hmacJwtSignatureValidator;
        @Nullable
        private final PkcJwtSignatureValidator pkcJwtSignatureValidator;

        public DelegatingJwtSignatureValidator(
            final RealmConfig realmConfig,
            final SSLService sslService,
            final PkcJwkSetReloadNotifier reloadNotifier
        ) {
            this.realmConfig = realmConfig;
            // Split configured signature algorithms by PKC and HMAC. Useful during validation, error logging, and JWK vs Alg filtering.
            final List<String> algs = realmConfig.getSetting(JwtRealmSettings.ALLOWED_SIGNATURE_ALGORITHMS);
            this.allowedJwksAlgsHmac = algs.stream().filter(JwtRealmSettings.SUPPORTED_SIGNATURE_ALGORITHMS_HMAC::contains).toList();
            this.allowedJwksAlgsPkc = algs.stream().filter(JwtRealmSettings.SUPPORTED_SIGNATURE_ALGORITHMS_PKC::contains).toList();

            final String jwkSetPath = realmConfig.getSetting(JwtRealmSettings.PKC_JWKSET_PATH);
            final SecureString hmacJwkSetContents = realmConfig.getSetting(JwtRealmSettings.HMAC_JWKSET);
            final SecureString hmacKeyContents = realmConfig.getSetting(JwtRealmSettings.HMAC_KEY);
            final boolean isConfiguredJwkSetPkc = Strings.hasText(jwkSetPath);
            final boolean isConfiguredJwkSetHmac = Strings.hasText(hmacJwkSetContents);
            final boolean isConfiguredJwkOidcHmac = Strings.hasText(hmacKeyContents);
            validateJwkSettings(realmConfig, isConfiguredJwkSetPkc, isConfiguredJwkSetHmac, isConfiguredJwkOidcHmac);

            final List<JWK> jwksHmac;
            if (isConfiguredJwkSetHmac) {
                jwksHmac = JwkValidateUtil.loadJwksFromJwkSetString(
                    RealmSettings.getFullSettingKey(realmConfig, JwtRealmSettings.HMAC_JWKSET),
                    hmacJwkSetContents.toString()
                );
            } else if (isConfiguredJwkOidcHmac) {
                final OctetSequenceKey hmacKey = JwkValidateUtil.loadHmacJwkFromJwkString(
                    RealmSettings.getFullSettingKey(realmConfig, JwtRealmSettings.HMAC_KEY),
                    hmacKeyContents
                );
                assert hmacKey != null : "Null HMAC key should not happen here";
                jwksHmac = List.of(hmacKey);
            } else {
                jwksHmac = null;
            }

            if (jwksHmac != null) {
                final JwkSetLoader.JwksAlgs jwksAlgs = JwkValidateUtil.filterJwksAndAlgorithms(jwksHmac, allowedJwksAlgsHmac);
                logger.info("Usable HMAC: JWKs [{}]. Algorithms [{}].", jwksAlgs.jwks().size(), String.join(",", jwksAlgs.algs()));
                // Filter JWK(s) vs signature algorithms. Only keep JWKs with a matching alg. Only keep algorithms with a matching JWK.
                this.hmacJwtSignatureValidator = new HmacJwtSignatureValidator(jwksAlgs);
            } else {
                this.hmacJwtSignatureValidator = null;
            }

            if (isConfiguredJwkSetPkc) {
                this.pkcJwtSignatureValidator = new PkcJwtSignatureValidator(
                    new JwkSetLoader(realmConfig, allowedJwksAlgsPkc, sslService),
                    reloadNotifier
                );
            } else {
                this.pkcJwtSignatureValidator = null;
            }
            logWarnIfAuthenticationWillAlwaysFail();
        }

        @Override
        public void validate(String tokenPrincipal, SignedJWT jwt, ActionListener<Void> listener) {
            final String algorithm = jwt.getHeader().getAlgorithm().getName();
            if (allowedJwksAlgsHmac.contains(algorithm)) {
                if (hmacJwtSignatureValidator != null) {
                    hmacJwtSignatureValidator.validate(tokenPrincipal, jwt, listener);
                } else {
                    listener.onFailure(
                        new ElasticsearchSecurityException(
                            "algorithm [%s] is a HMAC signing algorithm, but none of the HMAC JWK settings ["
                                + RealmSettings.getFullSettingKey(realmConfig, JwtRealmSettings.HMAC_KEY)
                                + ", "
                                + RealmSettings.getFullSettingKey(realmConfig, JwtRealmSettings.HMAC_JWKSET)
                                + "] is configured",
                            RestStatus.BAD_REQUEST,
                            algorithm
                        )
                    );
                }
            } else if (allowedJwksAlgsPkc.contains(algorithm)) {
                if (pkcJwtSignatureValidator != null) {
                    pkcJwtSignatureValidator.validate(tokenPrincipal, jwt, listener);
                } else {
                    listener.onFailure(
                        new ElasticsearchSecurityException(
                            "algorithm [%s] is a PKC signing algorithm, but PKC JWK setting ["
                                + RealmSettings.getFullSettingKey(realmConfig, JwtRealmSettings.PKC_JWKSET_PATH)
                                + "] is not configured",
                            RestStatus.BAD_REQUEST,
                            algorithm
                        )
                    );
                }
            } else {
                listener.onFailure(
                    new ElasticsearchSecurityException(
                        "algorithm [%s] is not in the list of supported algorithms [%s]",
                        RestStatus.BAD_REQUEST,
                        algorithm,
                        Strings.collectionToCommaDelimitedString(
                            Stream.of(allowedJwksAlgsHmac.stream(), allowedJwksAlgsPkc.stream()).toList()
                        )
                    )
                );
            }
        }

        @Override
        public void close() {
            if (pkcJwtSignatureValidator != null) {
                pkcJwtSignatureValidator.close();
            }
        }

        private void logWarnIfAuthenticationWillAlwaysFail() {
            final boolean hasUsableJwksAndAlgorithms = (hmacJwtSignatureValidator != null
                && false == hmacJwtSignatureValidator.jwksAlgs.isEmpty())
                || (pkcJwtSignatureValidator != null
                    && false == pkcJwtSignatureValidator.jwkSetLoader.getContentAndJwksAlgs().jwksAlgs().isEmpty());
            if (false == hasUsableJwksAndAlgorithms) {
                logger.warn(
                    "No available JWK and algorithm for HMAC or PKC. JWT realm authentication expected to fail until this is fixed."
                );
            }
        }

        private static void validateJwkSettings(
            RealmConfig realmConfig,
            boolean isConfiguredJwkSetPkc,
            boolean isConfiguredJwkSetHmac,
            boolean isConfiguredJwkOidcHmac
        ) {
            if (isConfiguredJwkSetPkc == false && isConfiguredJwkSetHmac == false && isConfiguredJwkOidcHmac == false) {
                throw new SettingsException(
                    "At least one of ["
                        + RealmSettings.getFullSettingKey(realmConfig, JwtRealmSettings.HMAC_KEY)
                        + "] or ["
                        + RealmSettings.getFullSettingKey(realmConfig, JwtRealmSettings.HMAC_JWKSET)
                        + "] or ["
                        + RealmSettings.getFullSettingKey(realmConfig, JwtRealmSettings.PKC_JWKSET_PATH)
                        + "] must be set"
                );
            }

            // HMAC Key vs HMAC JWKSet settings must be mutually exclusive
            if (isConfiguredJwkSetHmac && isConfiguredJwkOidcHmac) {
                throw new SettingsException(
                    "Settings ["
                        + RealmSettings.getFullSettingKey(realmConfig, JwtRealmSettings.HMAC_JWKSET)
                        + "] and ["
                        + RealmSettings.getFullSettingKey(realmConfig, JwtRealmSettings.HMAC_KEY)
                        + "] are not allowed at the same time."
                );
            }
        }

        // Package private for testing Only
        Tuple<JwkSetLoader.JwksAlgs, JwkSetLoader.JwksAlgs> getAllJwksAlgs() {
            final JwkSetLoader.JwksAlgs jwksAlgsHmac;
            if (hmacJwtSignatureValidator == null) {
                jwksAlgsHmac = new JwkSetLoader.JwksAlgs(List.of(), List.of());
            } else {
                jwksAlgsHmac = hmacJwtSignatureValidator.jwksAlgs;
            }

            final JwkSetLoader.JwksAlgs jwksAlgsPkc;
            if (pkcJwtSignatureValidator == null) {
                jwksAlgsPkc = new JwkSetLoader.JwksAlgs(List.of(), List.of());
            } else {
                jwksAlgsPkc = pkcJwtSignatureValidator.jwkSetLoader.getContentAndJwksAlgs().jwksAlgs();
            }
            return new Tuple<>(jwksAlgsHmac, jwksAlgsPkc);
        }
    }

    class HmacJwtSignatureValidator implements JwtSignatureValidator {

        private final JwkSetLoader.JwksAlgs jwksAlgs;

        HmacJwtSignatureValidator(JwkSetLoader.JwksAlgs jwksAlgs) {
            this.jwksAlgs = jwksAlgs;
        }

        public void validate(String tokenPrincipal, SignedJWT jwt, ActionListener<Void> listener) {
            // TODO: assert algorithm?
            try {
                validateSignature(jwt, jwksAlgs.jwks());
                listener.onResponse(null);
            } catch (Exception e) {
                listener.onFailure(e);
            }
        }
    }

    class PkcJwtSignatureValidator implements JwtSignatureValidator {

        private static final Logger logger = LogManager.getLogger(PkcJwtSignatureValidator.class);

        private final JwkSetLoader jwkSetLoader;
        private final PkcJwkSetReloadNotifier reloadNotifier;

        PkcJwtSignatureValidator(JwkSetLoader jwkSetLoader, PkcJwkSetReloadNotifier reloadNotifier) {
            this.jwkSetLoader = jwkSetLoader;
            this.reloadNotifier = reloadNotifier;
        }

        public void validate(String tokenPrincipal, SignedJWT signedJWT, ActionListener<Void> listener) {
            // TODO: assert algorithm?
            final JwkSetLoader.ContentAndJwksAlgs contentAndJwksAlgs = jwkSetLoader.getContentAndJwksAlgs();
            final JwkSetLoader.JwksAlgs jwksAlgs = contentAndJwksAlgs.jwksAlgs();
            final byte[] initialJwksVersion = contentAndJwksAlgs.sha256();
            try {
                validateSignature(signedJWT, jwksAlgs.jwks());
                listener.onResponse(null);
            } catch (Exception primaryException) {
                logger.debug(
                    () -> org.elasticsearch.core.Strings.format(
                        "Signature verification failed for JWT [%s] reloading JWKSet (was: #[%s] JWKs, #[%s] algs, sha256=[%s])",
                        tokenPrincipal,
                        jwksAlgs.jwks().size(),
                        jwksAlgs.algs().size(),
                        MessageDigests.toHexString(contentAndJwksAlgs.sha256())
                    ),
                    primaryException
                );

                jwkSetLoader.reload(ActionListener.wrap(reloadResult -> {

                    if (initialJwksVersion != null && Arrays.equals(jwkSetLoader.getContentAndJwksAlgs().sha256(), initialJwksVersion)) {
                        logger.debug(
                            "No change in reloaded JWK set with sha256=[{}] will not retry signature verification",
                            jwkSetLoader.getContentAndJwksAlgs() == null
                                ? "null"
                                : MessageDigests.toHexString(jwkSetLoader.getContentAndJwksAlgs().sha256())
                        );
                        listener.onFailure(primaryException);
                        return;
                    }

                    // If all PKC JWKs were replaced, all PKC JWT cache entries need to be invalidated.
                    // Enhancement idea: Use separate caches for PKC vs HMAC JWKs, so only PKC entries get invalidated.
                    // Enhancement idea: When some JWKs are retained (ex: rotation), only invalidate for removed JWKs.
                    reloadNotifier.reloaded();

                    final JwkSetLoader.JwksAlgs reloadedJwksAlgs = reloadResult;
                    if (reloadedJwksAlgs.isEmpty()) {
                        logger.debug("Reloaded empty PKC JWKs, signature verification will fail for JWT [{}]", tokenPrincipal);
                        // fall through and let try/catch below handle empty JWKs failure log and response
                    }

                    try {
                        validateSignature(signedJWT, reloadedJwksAlgs.jwks());
                        listener.onResponse(null);
                    } catch (Exception secondaryException) {
                        logger.debug(
                            "Signature verification of JWT [{}] failed - original failure: [{}], failure after reload: [{}]",
                            tokenPrincipal,
                            primaryException.getMessage(),
                            secondaryException.getMessage()
                        );
                        secondaryException.addSuppressed(primaryException);
                        listener.onFailure(secondaryException);
                    }
                }, listener::onFailure));
            }
        }

        @Override
        public void close() {
            jwkSetLoader.close();
        }
    }

    /**
     * Look through each JWK in the JWKSet to see if they can validate the Signed JWT signature.
     * Apply JWT kid and JWT alg filters to the JWKs to skip unnecessary signature checking.
     *
     * If JWT kid is present, and any JWK kid matches, only use the matching subset of JWKs. Ignore the rest.
     * Note: JWK kid should be unique. However, this method does not assume they are unique. Each match will be tried.
     *
     * Depending on the JWT alg, certain HMAC/RSA/EC JWKs can be excluded.
     * HMAC JWKs that do not meet the minimum length requirement are ignored.
     * RSA JWKs that do not meet the minimum length requirement are ignored.
     * EC JWKs that do not meet the exact curve requirement are ignored.
     *
     * @param jwt Signed JWT to be validated.
     * @param jwks JWKSet of HMAC/RSA/EC JWKs. At least one JWK is required to succeed.
     * @throws Exception Error if JWKs fail to validate the Signed JWT.
     */
    default void validateSignature(final SignedJWT jwt, final List<JWK> jwks) throws Exception {
        assert jwks != null : "Verify requires a non-null JWK list";
        if (jwks.isEmpty()) {
            throw new ElasticsearchException("Verify requires a non-empty JWK list");
        }
        final String id = jwt.getHeader().getKeyID();
        final JWSAlgorithm alg = jwt.getHeader().getAlgorithm();
        logger.trace("JWKs [{}], JWT KID [{}], and JWT Algorithm [{}] before filters.", jwks.size(), id, alg.getName());

        // If JWT has optional kid header, and realm JWKs have optional kid attribute, any mismatches JWT.kid vs JWK.kid can be ignored.
        // Keep any JWKs if JWK optional kid attribute is missing. Keep all JWKs if JWT optional kid header is missing.
        final List<JWK> jwksKid = jwks.stream().filter(j -> ((id == null) || (j.getKeyID() == null) || (id.equals(j.getKeyID())))).toList();
        logger.trace("JWKs [{}] after KID [{}](|null) filter.", jwksKid.size(), id);

        // JWT has mandatory alg header. If realm JWKs have optional alg attribute, any mismatches JWT.alg vs JWK.alg can be ignored.
        // Keep any JWKs if JWK optional alg attribute is missing.
        final List<JWK> jwksAlg = jwksKid.stream().filter(j -> (j.getAlgorithm() == null) || (alg.equals(j.getAlgorithm()))).toList();
        logger.trace("JWKs [{}] after Algorithm [{}](|null) filter.", jwksAlg.size(), alg.getName());

        // PKC Example: Realm has five PKC JWKs RSA-2048, RSA-3072, EC-P256, EC-P384, and EC-P512. JWT alg allows ignoring some.
        // - If JWT alg is RS256, only RSA-2048 and RSA-3072 are valid for a JWT RS256 signature. Ignore three EC JWKs.
        // - If JWT alg is ES512, only EC-P512 is valid for a JWT ES512 signature. Ignore four JWKs (two RSA, two EC).
        // - If JWT alg is ES384, only EC-P384 is valid for a JWT ES384 signature. Ignore four JWKs (two RSA, two EC).
        // - If JWT alg is ES256, only EC-P256 is valid for a JWT ES256 signature. Ignore four JWKs (two RSA, two EC).
        //
        // HMAC Example: Realm has six HMAC JWKs of bit lengths 256, 320, 384, 400, 512, and 1000. JWT alg allows ignoring some.
        // - If JWT alg is HS256, all are valid for a JWT HS256 signature. Don't ignore any HMAC JWKs.
        // - If JWT alg is HS384, only 384, 400, 512, and 1000 are valid for a JWT HS384 signature. Ignore two HMAC JWKs.
        // - If JWT alg is HS512, only 512 and 1000 are valid for a JWT HS512 signature. Ignore four HMAC JWKs.
        final List<JWK> jwksStrength = jwksAlg.stream().filter(j -> JwkValidateUtil.isMatch(j, alg.getName())).toList();
        logger.debug("JWKs [{}] after Algorithm [{}] match filter.", jwksStrength.size(), alg);

        // No JWKs passed the kid, alg, and strength checks, so nothing left to use in verifying the JWT signature
        if (jwksStrength.isEmpty()) {
            throw new ElasticsearchException("Verify failed because all " + jwks.size() + " provided JWKs were filtered.");
        }

        for (final JWK jwk : jwksStrength) {
            if (jwt.verify(createJwsVerifier(jwk))) {
                logger.trace(
                    "JWT signature validation succeeded with JWK kty=[{}], jwtAlg=[{}], jwtKid=[{}], use=[{}], ops=[{}]",
                    jwk.getKeyType(),
                    jwk.getAlgorithm(),
                    jwk.getKeyID(),
                    jwk.getKeyUse(),
                    jwk.getKeyOperations()
                );
                return;
            } else {
                logger.trace(
                    "JWT signature validation failed with JWK kty=[{}], jwtAlg=[{}], jwtKid=[{}], use=[{}], ops={}",
                    jwk.getKeyType(),
                    jwk.getAlgorithm(),
                    jwk.getKeyID(),
                    jwk.getKeyUse(),
                    jwk.getKeyOperations() == null ? "[null]" : jwk.getKeyOperations()
                );
            }
        }

        throw new ElasticsearchException("Verify failed using " + jwksStrength.size() + " of " + jwks.size() + " provided JWKs.");
    }

    default JWSVerifier createJwsVerifier(final JWK jwk) throws JOSEException {
        if (jwk instanceof RSAKey rsaKey) {
            return new RSASSAVerifier(rsaKey);
        } else if (jwk instanceof ECKey ecKey) {
            return new ECDSAVerifier(ecKey);
        } else if (jwk instanceof OctetSequenceKey octetSequenceKey) {
            return new MACVerifier(octetSequenceKey);
        }
        throw new JOSEException(
            "Unsupported JWK class ["
                + (jwk == null ? "null" : jwk.getClass().getCanonicalName())
                + "]. Supported classes are ["
                + RSAKey.class.getCanonicalName()
                + ", "
                + ECKey.class.getCanonicalName()
                + ", "
                + OctetSequenceKey.class.getCanonicalName()
                + "]."
        );
    }

    interface PkcJwkSetReloadNotifier {
        void reloaded();
    }
}
