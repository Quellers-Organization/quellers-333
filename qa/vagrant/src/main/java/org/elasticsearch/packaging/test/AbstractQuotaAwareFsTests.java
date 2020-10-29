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

package org.elasticsearch.packaging.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.fluent.Request;
import org.elasticsearch.packaging.util.Archives;
import org.elasticsearch.packaging.util.Distribution;
import org.elasticsearch.packaging.util.Installation;
import org.elasticsearch.packaging.util.ServerUtils;
import org.elasticsearch.packaging.util.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.elasticsearch.packaging.util.Archives.ARCHIVE_OWNER;
import static org.elasticsearch.packaging.util.Archives.installArchive;
import static org.elasticsearch.packaging.util.Cleanup.cleanEverything;
import static org.elasticsearch.packaging.util.FileUtils.getTempDir;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * Check that the quota-aware filesystem plugin can be installed, and that it operates as expected.
 */
public abstract class AbstractQuotaAwareFsTests extends PackagingTestCase {

    // private static final String QUOTA_AWARE_FS_PLUGIN_NAME = "quota-aware-fs";
    private static final Path QUOTA_AWARE_FS_PLUGIN;
    static {
        // Re-read before each test so the plugin path can be manipulated within tests.
        // Corresponds to DistroTestPlugin#QUOTA_AWARE_FS_PLUGIN_SYSPROP
        QUOTA_AWARE_FS_PLUGIN = Paths.get(System.getProperty("tests.quota-aware-fs-plugin"));
    }

    private Shell sh;

    @BeforeClass
    public static void setupSuite() throws Exception {
        cleanEverything();
    }

    @Before
    public void setupTest() {
        assumeTrue("only compatible distributions", distribution().packaging.compatible);
        sh = new Shell();
    }

    @After
    public void teardown() throws Exception {
        cleanEverything();
    }

    /** The {@link Distribution} that should be tested in this case */
    protected abstract Distribution distribution();

    /**
     * Check that when the plugin is installed but the system property for passing the location of the related
     * properties file is omitted, then Elasticsearch exits with the expected error message.
     */
    public void test10ElasticsearchRequiresSystemPropertyToBeSet() throws Exception {
        Installation installation = installArchive(distribution());
        final Installation.Executables bin = installation.executables();

        sh.run(bin.elasticsearchPlugin + " install --batch \"" + QUOTA_AWARE_FS_PLUGIN.toUri() + "\"");

        // Without setting the `es.fs.quota.file` property, ES should exit with a failure code.
        final Shell.Result result = sh.run("sudo -E -u " + ARCHIVE_OWNER + " " + bin.elasticsearch);

        assertThat("Elasticsearch should have terminated unsuccessfully", result.isSuccess(), equalTo(false));
        assertThat(
            result.stderr,
            containsString("Property es.fs.quota.file must be set to a URI in order to use the quota filesystem provider")
        );
    }

    /**
     * Check that when the plugin is installed but the system property for passing the location of the related
     * properties file contains a non-existent URI, then Elasticsearch exits with the expected error message.
     */
    public void test20ElasticsearchRejectsNonExistentPropertiesLocation() throws Exception {
        Installation installation = installArchive(distribution());
        final Installation.Executables bin = installation.executables();

        sh.run(bin.elasticsearchPlugin + " install --batch \"" + QUOTA_AWARE_FS_PLUGIN.toUri() + "\"");

        sh.getEnv().put("ES_JAVA_OPTS", "-Des.fs.quota.file=file:///this/does/not/exist.properties");

        final Shell.Result result = sh.run("sudo -E -u " + ARCHIVE_OWNER + " " + bin.elasticsearch);

        // Generate a Path for this location so that the platform-specific line-endings will be used.
        final String platformPath = Paths.get("/this/does/not/exist.properties").toString();

        assertThat("Elasticsearch should have terminated unsuccessfully", result.isSuccess(), equalTo(false));
        assertThat(result.stderr, containsString("NoSuchFileException: " + platformPath));
    }

    /**
     * Check that Elasticsearch can load the plugin and apply the quota limits in the properties file. Also check that
     * Elasticsearch polls the file for changes.
     */
    public void test30ElasticsearchStartsWhenSystemPropertySet() throws Exception {
        Installation installation = installArchive(distribution());
        final Installation.Executables bin = installation.executables();

        sh.run(bin.elasticsearchPlugin + " install --batch \"" + QUOTA_AWARE_FS_PLUGIN.toUri() + "\"");

        int total = 20 * 1024 * 1024;
        int available = 10 * 1024 * 1024;

        final Path quotaPath = getTempDir().resolve("quota.properties");
        Files.write(quotaPath, String.format(Locale.ROOT, "total=%d\nremaining=%d\n", total, available).getBytes(StandardCharsets.UTF_8));

        sh.getEnv().put("ES_JAVA_OPTS", "-Des.fs.quota.file=" + quotaPath.toUri());

        try {
            Archives.runElasticsearch(installation);

            final Totals actualTotals = fetchFilesystemTotals();

            assertThat(actualTotals.totalInBytes, equalTo(total));
            assertThat(actualTotals.availableInBytes, equalTo(available));

            int updatedTotal = total * 3;
            int updatedAvailable = available * 3;

            // Check that ES is polling the properties file for changes by modifying the properties file
            // and waiting for ES to pick up the changes.
            Files.write(quotaPath,
                String.format(Locale.ROOT, "total=%d\nremaining=%d\n", updatedTotal, updatedAvailable).getBytes(StandardCharsets.UTF_8));

            // The check interval is 1000ms, but give ourselves some leeway.
            Thread.sleep(2000);

            final Totals updatedActualTotals = fetchFilesystemTotals();

            assertThat(updatedActualTotals.totalInBytes, equalTo(updatedTotal));
            assertThat(updatedActualTotals.availableInBytes, equalTo(updatedAvailable));
        } finally {
            Archives.stopElasticsearch(installation);
            Files.deleteIfExists(quotaPath);
        }
    }

    private static class Totals {
        int totalInBytes;
        int availableInBytes;

        Totals(int totalInBytes, int availableInBytes) {
            this.totalInBytes = totalInBytes;
            this.availableInBytes = availableInBytes;
        }
    }

    private Totals fetchFilesystemTotals() throws Exception {
        final String response = ServerUtils.makeRequest(Request.Get("http://localhost:9200/_nodes/stats"));

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode rootNode = mapper.readTree(response);

        assertThat("Some nodes failed", rootNode.at("/_nodes/failed").intValue(), equalTo(0));

        final String nodeId = rootNode.get("nodes").fieldNames().next();

        final JsonNode fsNode = rootNode.at("/nodes/" + nodeId + "/fs/total");

        return new Totals(fsNode.get("total_in_bytes").intValue(), fsNode.get("available_in_bytes").intValue());
    }
}
