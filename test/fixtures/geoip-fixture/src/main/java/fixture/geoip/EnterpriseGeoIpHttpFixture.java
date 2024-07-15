/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package fixture.geoip;

import com.sun.net.httpserver.HttpServer;

import org.elasticsearch.cli.Terminal;
import org.elasticsearch.common.hash.MessageDigests;
import org.elasticsearch.geoip.GeoIpCli;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

public class EnterpriseGeoIpHttpFixture extends ExternalResource {

    private final Path source;
    private final Path target;
    private final boolean enabled;
    private final String[] databaseTypes;
    private HttpServer server;

    /*
     * The values in databaseTypes must be in DatabaseConfiguration.MAXMIND_NAMES
     */
    public EnterpriseGeoIpHttpFixture(boolean enabled, String... databaseTypes) {
        this.enabled = enabled;
        this.databaseTypes = databaseTypes;
        try {
            this.source = Files.createTempDirectory("source");
            this.target = Files.createTempDirectory("target");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String getAddress() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/";
    }

    @Override
    protected void before() throws Throwable {
        if (enabled) {
            copyFiles();
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            for (String databaseType : databaseTypes) {
                createContextForEnterpriseDatabase(databaseType);
            }
            server.start();
        }
    }

    private void createContextForEnterpriseDatabase(String databaseType) {
        this.server.createContext("/" + databaseType + "/download", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            if (exchange.getRequestURI().toString().contains("sha256")) {
                MessageDigest sha256 = MessageDigests.sha256();
                try (InputStream inputStream = GeoIpHttpFixture.class.getResourceAsStream("/geoip-fixture/" + databaseType + ".tgz")) {
                    sha256.update(inputStream.readAllBytes());
                }
                exchange.getResponseBody()
                    .write(
                        (MessageDigests.toHexString(sha256.digest()) + "  " + databaseType + "_20240709.tar.gz").getBytes(
                            StandardCharsets.UTF_8
                        )
                    );
            } else {
                try (
                    OutputStream outputStream = exchange.getResponseBody();
                    InputStream inputStream = GeoIpHttpFixture.class.getResourceAsStream("/geoip-fixture/" + databaseType + ".tgz")
                ) {
                    inputStream.transferTo(outputStream);
                }
            }
            exchange.getResponseBody().close();
        });
    }

    @Override
    protected void after() {
        if (enabled) {
            server.stop(0);
        }
    }

    private void copyFiles() throws Exception {
        for (String databaseType : databaseTypes) {
            Files.copy(
                GeoIpHttpFixture.class.getResourceAsStream("/geoip-fixture/GeoIP2-City.tgz"),
                source.resolve(databaseType + ".tgz"),
                StandardCopyOption.REPLACE_EXISTING
            );
        }

        new GeoIpCli().main(
            new String[] { "-s", source.toAbsolutePath().toString(), "-t", target.toAbsolutePath().toString() },
            Terminal.DEFAULT,
            null
        );
    }
}
