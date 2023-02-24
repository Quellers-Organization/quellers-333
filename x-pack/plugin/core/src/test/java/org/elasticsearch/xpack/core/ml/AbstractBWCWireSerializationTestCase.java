/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ml;

import org.elasticsearch.KnownTransportVersions;
import org.elasticsearch.TransportVersion;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.test.AbstractWireSerializingTestCase;
import org.elasticsearch.test.TransportVersionUtils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractBWCWireSerializationTestCase<T extends Writeable> extends AbstractWireSerializingTestCase<T> {

    static final List<TransportVersion> ALL_VERSIONS = KnownTransportVersions.ALL_VERSIONS;

    public static List<TransportVersion> getAllBWCVersions(TransportVersion version) {
        return ALL_VERSIONS.stream()
            .filter(v -> v.before(version) && TransportVersionUtils.isCompatible(version, v))
            .collect(Collectors.toList());
    }

    static final List<TransportVersion> DEFAULT_BWC_VERSIONS = getAllBWCVersions(TransportVersion.CURRENT);

    /**
     * Returns the expected instance if serialized from the given version.
     */
    protected abstract T mutateInstanceForVersion(T instance, TransportVersion version);

    /**
     * The bwc versions to test serialization against
     */
    protected List<TransportVersion> bwcVersions() {
        return DEFAULT_BWC_VERSIONS;
    }

    /**
     * Test serialization and deserialization of the test instance across versions
     */
    public final void testBwcSerialization() throws IOException {
        for (int runs = 0; runs < NUMBER_OF_TEST_RUNS; runs++) {
            T testInstance = createTestInstance();
            for (TransportVersion bwcVersion : bwcVersions()) {
                assertBwcSerialization(testInstance, bwcVersion);
            }
        }
    }

    /**
     * Assert that instances copied at a particular version are equal. The version is useful
     * for sanity checking the backwards compatibility of the wire. It isn't a substitute for
     * real backwards compatibility tests but it is *so* much faster.
     */
    protected final void assertBwcSerialization(T testInstance, TransportVersion version) throws IOException {
        T deserializedInstance = copyWriteable(testInstance, getNamedWriteableRegistry(), instanceReader(), version);
        assertOnBWCObject(deserializedInstance, mutateInstanceForVersion(testInstance, version), version);
    }

    /**
     * @param bwcSerializedObject The object deserialized from the previous version
     * @param testInstance The original test instance
     * @param version The version which serialized
     */
    protected void assertOnBWCObject(T bwcSerializedObject, T testInstance, TransportVersion version) {
        assertNotSame(version.toString(), bwcSerializedObject, testInstance);
        assertEquals(version.toString(), bwcSerializedObject, testInstance);
        assertEquals(version.toString(), bwcSerializedObject.hashCode(), testInstance.hashCode());
    }
}
