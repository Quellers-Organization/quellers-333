/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.authc.support;

import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.core.security.authc.support.Hasher;

import static org.hamcrest.Matchers.sameInstance;

public class HasherTests extends ESTestCase {
    public void testBcryptFamilySelfGenerated() throws Exception {
        testHasherSelfGenerated(Hasher.BCRYPT);
        testHasherSelfGenerated(Hasher.BCRYPT4);
        testHasherSelfGenerated(Hasher.BCRYPT5);
        testHasherSelfGenerated(Hasher.BCRYPT6);
        testHasherSelfGenerated(Hasher.BCRYPT7);
        testHasherSelfGenerated(Hasher.BCRYPT8);
        testHasherSelfGenerated(Hasher.BCRYPT9);
        testHasherSelfGenerated(Hasher.BCRYPT10);
        testHasherSelfGenerated(Hasher.BCRYPT11);
        testHasherSelfGenerated(Hasher.BCRYPT12);
        testHasherSelfGenerated(Hasher.BCRYPT13);
        testHasherSelfGenerated(Hasher.BCRYPT14);
    }

    public void testPBKDF2FamilySelfGenerated() throws Exception {
        testHasherSelfGenerated(Hasher.PBKDF2);
        testHasherSelfGenerated(Hasher.PBKDF2_1000);
        testHasherSelfGenerated(Hasher.PBKDF2_10000);
        testHasherSelfGenerated(Hasher.PBKDF2_50000);
        testHasherSelfGenerated(Hasher.PBKDF2_100000);
        testHasherSelfGenerated(Hasher.PBKDF2_500000);
        testHasherSelfGenerated(Hasher.PBKDF2_1000000);
    }

    public void testMd5SelfGenerated() throws Exception {
        testHasherSelfGenerated(Hasher.MD5);
    }

    public void testSha1SelfGenerated() throws Exception {
        testHasherSelfGenerated(Hasher.SHA1);
    }

    public void testSSHA256SelfGenerated() throws Exception {
        testHasherSelfGenerated(Hasher.SSHA256);
    }

    public void testNoopSelfGenerated() throws Exception {
        testHasherSelfGenerated(Hasher.NOOP);
    }

    public void testResolve() throws Exception {
        assertThat(Hasher.resolve("bcrypt"), sameInstance(Hasher.BCRYPT));
        assertThat(Hasher.resolve("bcrypt4"), sameInstance(Hasher.BCRYPT4));
        assertThat(Hasher.resolve("bcrypt5"), sameInstance(Hasher.BCRYPT5));
        assertThat(Hasher.resolve("bcrypt6"), sameInstance(Hasher.BCRYPT6));
        assertThat(Hasher.resolve("bcrypt7"), sameInstance(Hasher.BCRYPT7));
        assertThat(Hasher.resolve("bcrypt8"), sameInstance(Hasher.BCRYPT8));
        assertThat(Hasher.resolve("bcrypt9"), sameInstance(Hasher.BCRYPT9));
        assertThat(Hasher.resolve("bcrypt10"), sameInstance(Hasher.BCRYPT10));
        assertThat(Hasher.resolve("bcrypt11"), sameInstance(Hasher.BCRYPT11));
        assertThat(Hasher.resolve("bcrypt12"), sameInstance(Hasher.BCRYPT12));
        assertThat(Hasher.resolve("bcrypt13"), sameInstance(Hasher.BCRYPT13));
        assertThat(Hasher.resolve("bcrypt14"), sameInstance(Hasher.BCRYPT14));
        assertThat(Hasher.resolve("pbkdf2"), sameInstance(Hasher.PBKDF2));
        assertThat(Hasher.resolve("pbkdf2_1000"), sameInstance(Hasher.PBKDF2_1000));
        assertThat(Hasher.resolve("pbkdf2_10000"), sameInstance(Hasher.PBKDF2_10000));
        assertThat(Hasher.resolve("pbkdf2_50000"), sameInstance(Hasher.PBKDF2_50000));
        assertThat(Hasher.resolve("pbkdf2_100000"), sameInstance(Hasher.PBKDF2_100000));
        assertThat(Hasher.resolve("pbkdf2_500000"), sameInstance(Hasher.PBKDF2_500000));
        assertThat(Hasher.resolve("pbkdf2_1000000"), sameInstance(Hasher.PBKDF2_1000000));
        assertThat(Hasher.resolve("sha1"), sameInstance(Hasher.SHA1));
        assertThat(Hasher.resolve("md5"), sameInstance(Hasher.MD5));
        assertThat(Hasher.resolve("ssha256"), sameInstance(Hasher.SSHA256));
        assertThat(Hasher.resolve("noop"), sameInstance(Hasher.NOOP));
        assertThat(Hasher.resolve("clear_text"), sameInstance(Hasher.NOOP));
        try {
            Hasher.resolve("unknown_hasher");
            fail("expected a settings error when trying to resolve an unknown hasher");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    private static void testHasherSelfGenerated(Hasher hasher) throws Exception {
        SecureString passwd = new SecureString(randomAlphaOfLength(10).toCharArray());
        char[] hash = hasher.hash(passwd);
        assertTrue(hasher.verify(passwd, hash));
    }
}
