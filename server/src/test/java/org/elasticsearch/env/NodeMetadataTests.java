/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.env;

import org.elasticsearch.Build;
import org.elasticsearch.BuildVersion;
import org.elasticsearch.Version;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.gateway.MetadataStateFormat;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.index.IndexVersions;
import org.elasticsearch.internal.DefaultBuildVersion;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.EqualsHashCodeTestUtils;
import org.elasticsearch.test.VersionUtils;
import org.elasticsearch.test.index.IndexVersionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

public class NodeMetadataTests extends ESTestCase {
    // (Index)VersionUtils.randomVersion() only returns known versions, which are necessarily no later than (Index)Version.CURRENT;
    // however we want to also consider our behaviour with all versions, so occasionally pick up a truly random version.
    private Version randomVersion() {
        return rarely() ? Version.fromId(randomInt()) : VersionUtils.randomVersion(random());
    }

    private IndexVersion randomIndexVersion() {
        return rarely() ? IndexVersion.fromId(randomInt()) : IndexVersionUtils.randomVersion(random());
    }

    public void testEqualsHashcodeSerialization() {
        final Path tempDir = createTempDir();
        EqualsHashCodeTestUtils.checkEqualsAndHashCode(
            // TODO[wrb] random buildversion methods
            new NodeMetadata(randomAlphaOfLength(10), BuildVersion.fromVersion(randomVersion()), randomIndexVersion()),
            nodeMetadata -> {
                final long generation = NodeMetadata.FORMAT.writeAndCleanup(nodeMetadata, tempDir);
                final Tuple<NodeMetadata, Long> nodeMetadataLongTuple = NodeMetadata.FORMAT.loadLatestStateWithGeneration(
                    logger,
                    xContentRegistry(),
                    tempDir
                );
                assertThat(nodeMetadataLongTuple.v2(), equalTo(generation));
                return nodeMetadataLongTuple.v1();
            },
            nodeMetadata -> switch (randomInt(3)) {
                case 0 -> new NodeMetadata(
                    randomAlphaOfLength(21 - nodeMetadata.nodeId().length()),
                    nodeMetadata.nodeVersion(),
                    nodeMetadata.oldestIndexVersion()
                );
                case 1 -> new NodeMetadata(
                    nodeMetadata.nodeId(),
                    // TODO[wrb]: random versions for buildversion
                    BuildVersion.fromVersion(randomValueOtherThan(nodeMetadata.nodeVersion().toVersion(), this::randomVersion)),
                    nodeMetadata.oldestIndexVersion()
                );
                default -> new NodeMetadata(
                    nodeMetadata.nodeId(),
                    nodeMetadata.nodeVersion(),
                    randomValueOtherThan(nodeMetadata.oldestIndexVersion(), this::randomIndexVersion)
                );
            }
        );
    }

    public void testReadsFormatWithoutVersion() throws IOException {
        // the behaviour tested here is only appropriate if the current version is compatible with versions 7 and earlier
        assertTrue(IndexVersions.MINIMUM_COMPATIBLE.onOrBefore(IndexVersions.V_7_0_0));
        // when the current version is incompatible with version 7, the behaviour should change to reject files like the given resource
        // which do not have the version field

        final Path tempDir = createTempDir();
        final Path stateDir = Files.createDirectory(tempDir.resolve(MetadataStateFormat.STATE_DIR_NAME));
        final InputStream resource = this.getClass().getResourceAsStream("testReadsFormatWithoutVersion.binary");
        assertThat(resource, notNullValue());
        Files.copy(resource, stateDir.resolve(NodeMetadata.FORMAT.getStateFileName(between(0, Integer.MAX_VALUE))));
        final NodeMetadata nodeMetadata = NodeMetadata.FORMAT.loadLatestState(logger, xContentRegistry(), tempDir);
        assertThat(nodeMetadata.nodeId(), equalTo("y6VUVMSaStO4Tz-B5BxcOw"));
        // TODO[wrb]: BuildVersion.EMPTY?
        assertThat(nodeMetadata.nodeVersion(), equalTo(DefaultBuildVersion.EMPTY));
    }

    public void testUpgradesLegitimateVersions() {
        final String nodeId = randomAlphaOfLength(10);
        final NodeMetadata nodeMetadata = new NodeMetadata(
            nodeId,
            BuildVersion.fromVersion(
                randomValueOtherThanMany(
                    v -> v.after(Version.CURRENT) || v.before(Version.CURRENT.minimumCompatibilityVersion()),
                    this::randomVersion
                )
            ),
            IndexVersion.current()
        ).upgradeToCurrentVersion();
        assertThat(nodeMetadata.nodeVersion(), equalTo(BuildVersion.current()));
        assertThat(nodeMetadata.nodeId(), equalTo(nodeId));
    }

    public void testUpgradesMissingVersion() {
        final String nodeId = randomAlphaOfLength(10);

        final IllegalStateException illegalStateException = expectThrows(
            IllegalStateException.class,
            () -> new NodeMetadata(nodeId, BuildVersion.fromVersion(Version.V_EMPTY), IndexVersion.current()).upgradeToCurrentVersion()
        );
        assertThat(
            illegalStateException.getMessage(),
            startsWith(
                "cannot upgrade a node from version [" + Version.V_EMPTY + "] directly to version [" + Build.current().version() + "]"
            )
        );
    }

    public void testDoesNotUpgradeFutureVersion() {
        final IllegalStateException illegalStateException = expectThrows(
            IllegalStateException.class,
            () -> new NodeMetadata(randomAlphaOfLength(10), BuildVersion.fromVersion(tooNewVersion()), IndexVersion.current())
                .upgradeToCurrentVersion()
        );
        assertThat(
            illegalStateException.getMessage(),
            allOf(startsWith("cannot downgrade a node from version ["), endsWith("] to version [" + Build.current().version() + "]"))
        );
    }

    public void testDoesNotUpgradeAncientVersion() {
        final IllegalStateException illegalStateException = expectThrows(
            IllegalStateException.class,
            () -> new NodeMetadata(randomAlphaOfLength(10), BuildVersion.fromVersion(tooOldVersion()), IndexVersion.current())
                .upgradeToCurrentVersion()
        );
        assertThat(
            illegalStateException.getMessage(),
            allOf(
                startsWith("cannot upgrade a node from version ["),
                endsWith(
                    "] directly to version ["
                        + Build.current().version()
                        + "], upgrade to version ["
                        + Build.current().minWireCompatVersion()
                        + "] first."
                )
            )
        );
    }

    public void testUpgradeMarksPreviousVersion() {
        final String nodeId = randomAlphaOfLength(10);
        final Version version = VersionUtils.randomVersionBetween(random(), Version.CURRENT.minimumCompatibilityVersion(), Version.V_8_0_0);

        final NodeMetadata nodeMetadata = new NodeMetadata(nodeId, BuildVersion.fromVersion(version), IndexVersion.current())
            .upgradeToCurrentVersion();
        assertThat(nodeMetadata.nodeVersion(), equalTo(BuildVersion.current()));
        assertThat(nodeMetadata.previousNodeVersion().toVersion(), equalTo(version));
    }

    public static Version tooNewVersion() {
        return Version.fromId(between(Version.CURRENT.id + 1, 99999999));
    }

    public static IndexVersion tooNewIndexVersion() {
        return IndexVersion.fromId(between(IndexVersion.current().id() + 1, 99999999));
    }

    public static Version tooOldVersion() {
        return Version.fromId(between(1, Version.CURRENT.minimumCompatibilityVersion().id - 1));
    }
}
