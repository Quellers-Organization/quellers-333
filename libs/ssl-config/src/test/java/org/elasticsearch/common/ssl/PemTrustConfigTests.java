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

package org.elasticsearch.common.ssl;

import org.elasticsearch.test.ESTestCase;
import org.hamcrest.Matchers;

import javax.net.ssl.X509ExtendedTrustManager;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PemTrustConfigTests extends ESTestCase {

    public void testBuildTrustConfigFromSinglePemFile() throws Exception {
        final Path cert = getDataPath("/certs/ca1/ca.crt");
        final PemTrustConfig trustConfig = new PemTrustConfig(Collections.singletonList(cert));
        assertThat(trustConfig.getDependentFiles(), Matchers.containsInAnyOrder(cert));
        assertCertificateChain(trustConfig, "CN=Test CA 1");
    }

    public void testBuildTrustConfigFromMultiplePemFiles() throws Exception {
        final Path cert1 = getDataPath("/certs/ca1/ca.crt");
        final Path cert2 = getDataPath("/certs/ca2/ca.crt");
        final Path cert3 = getDataPath("/certs/ca3/ca.crt");
        final PemTrustConfig trustConfig = new PemTrustConfig(Arrays.asList(cert1, cert2, cert3));
        assertThat(trustConfig.getDependentFiles(), Matchers.containsInAnyOrder(cert1, cert2, cert3));
        assertCertificateChain(trustConfig, "CN=Test CA 1", "CN=Test CA 2", "CN=Test CA 3");
    }

    public void testBadFileFormatFails() throws Exception {
        final Path ca = createTempFile("ca", ".crt");
        Files.write(ca, randomByteArrayOfLength(128), StandardOpenOption.APPEND);
        final PemTrustConfig trustConfig = new PemTrustConfig(Collections.singletonList(ca));
        assertThat(trustConfig.getDependentFiles(), Matchers.containsInAnyOrder(ca));
        assertInvalidFileFormat(trustConfig, ca);
    }

    public void testEmptyFileFails() throws Exception {
        final Path ca = createTempFile("ca", ".crt");
        final PemTrustConfig trustConfig = new PemTrustConfig(Collections.singletonList(ca));
        assertThat(trustConfig.getDependentFiles(), Matchers.containsInAnyOrder(ca));
        assertEmptyFile(trustConfig, ca);
    }

    public void testMissingFileFailsWithMeaningfulMessage() throws Exception {
        final Path cert = getDataPath("/certs/ca1/ca.crt").getParent().resolve("dne.crt");
        final PemTrustConfig trustConfig = new PemTrustConfig(Collections.singletonList(cert));
        assertThat(trustConfig.getDependentFiles(), Matchers.containsInAnyOrder(cert));
        assertFileNotFound(trustConfig, cert);
    }

    public void testOneMissingFileFailsWithMeaningfulMessageEvenIfOtherFileExist() throws Exception {
        final Path cert1 = getDataPath("/certs/ca1/ca.crt");
        final Path cert2 = getDataPath("/certs/ca2/ca.crt").getParent().resolve("dne.crt");
        final Path cert3 = getDataPath("/certs/ca3/ca.crt");
        final PemTrustConfig trustConfig = new PemTrustConfig(Arrays.asList(cert1, cert2, cert3));
        assertThat(trustConfig.getDependentFiles(), Matchers.containsInAnyOrder(cert1, cert2, cert3));
        assertFileNotFound(trustConfig, cert2);
    }

    public void testTrustConfigReloadsFileContents() throws Exception {
        final Path cert1 = getDataPath("/certs/ca1/ca.crt");
        final Path cert2 = getDataPath("/certs/ca2/ca.crt");
        final Path cert3 = getDataPath("/certs/ca3/ca.crt");

        final Path ca1 = createTempFile("ca1", ".crt");
        final Path ca2 = createTempFile("ca2", ".crt");

        final PemTrustConfig trustConfig = new PemTrustConfig(Arrays.asList(ca1, ca2));

        Files.copy(cert1, ca1, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(cert2, ca2, StandardCopyOption.REPLACE_EXISTING);
        assertCertificateChain(trustConfig, "CN=Test CA 1", "CN=Test CA 2");

        Files.copy(cert3, ca2, StandardCopyOption.REPLACE_EXISTING);
        assertCertificateChain(trustConfig, "CN=Test CA 1", "CN=Test CA 3");

        Files.delete(ca1);
        assertFileNotFound(trustConfig, ca1);

        Files.write(ca1, randomByteArrayOfLength(128), StandardOpenOption.CREATE);
        assertInvalidFileFormat(trustConfig, ca1);
    }

    private void assertCertificateChain(PemTrustConfig trustConfig, String... caNames) {
        final X509ExtendedTrustManager trustManager = trustConfig.createTrustManager();
        final X509Certificate[] issuers = trustManager.getAcceptedIssuers();
        final Set<String> issuerNames = Stream.of(issuers)
            .map(X509Certificate::getSubjectDN)
            .map(Principal::getName)
            .collect(Collectors.toSet());

        assertThat(issuerNames, Matchers.containsInAnyOrder(caNames));
    }

    private void assertEmptyFile(PemTrustConfig trustConfig, Path file) {
        final SslConfigException exception = expectThrows(SslConfigException.class, trustConfig::createTrustManager);
        assertThat(exception.getMessage(), Matchers.containsString(file.toAbsolutePath().toString()));
        assertThat(exception.getMessage(), Matchers.containsString("failed to parse any certificates"));
    }

    private void assertInvalidFileFormat(PemTrustConfig trustConfig, Path file) {
        if (inFipsJvm()) {
            // When running on BC-FIPS, an invalid file format behaves like an empty file
            assertEmptyFile(trustConfig, file);
            return;
        }
        final SslConfigException exception = expectThrows(SslConfigException.class, trustConfig::createTrustManager);
        assertThat(exception.getMessage(), Matchers.containsString(file.toAbsolutePath().toString()));
        assertThat(exception.getMessage(), Matchers.containsString("cannot create trust"));
        assertThat(exception.getMessage(), Matchers.containsString("PEM"));
        assertThat(exception.getCause(), Matchers.instanceOf(GeneralSecurityException.class));
    }

    private void assertFileNotFound(PemTrustConfig trustConfig, Path file) {
        final SslConfigException exception = expectThrows(SslConfigException.class, trustConfig::createTrustManager);
        assertThat(exception.getMessage(), Matchers.containsString("files do not exist"));
        assertThat(exception.getMessage(), Matchers.containsString("PEM"));
        assertThat(exception.getMessage(), Matchers.containsString(file.toAbsolutePath().toString()));
        assertThat(exception.getCause(), Matchers.instanceOf(NoSuchFileException.class));
    }
}
