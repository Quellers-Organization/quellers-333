/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.coordination;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.EqualsHashCodeTestUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JoinStatusTests extends ESTestCase {

    public void testSerialization() throws Exception {
        JoinStatus joinStatus = new JoinStatus(
            new DiscoveryNode(UUID.randomUUID().toString(), buildNewFakeTransportAddress(), Version.CURRENT),
            randomLongBetween(0, 1000),
            randomAlphaOfLengthBetween(0, 100),
            randomNonNegativeTimeValue()
        );
        EqualsHashCodeTestUtils.checkEqualsAndHashCode(
            joinStatus,
            history -> copyWriteable(joinStatus, writableRegistry(), JoinStatus::new),
            this::mutateJoinStatus
        );
    }

    private JoinStatus mutateJoinStatus(JoinStatus originalJoinStatus) {
        switch (randomIntBetween(1, 4)) {
            case 1 -> {
                return new JoinStatus(
                    originalJoinStatus.remoteNode(),
                    originalJoinStatus.term() + 1,
                    originalJoinStatus.message(),
                    originalJoinStatus.age()
                );
            }
            case 2 -> {
                return new JoinStatus(
                    originalJoinStatus.remoteNode(),
                    originalJoinStatus.term(),
                    randomAlphaOfLengthBetween(0, 30),
                    originalJoinStatus.age()
                );
            }
            case 3 -> {
                return new JoinStatus(
                    originalJoinStatus.remoteNode(),
                    originalJoinStatus.term(),
                    randomAlphaOfLength(30),
                    randomNonNegativeTimeValue()
                );
            }
            case 4 -> {
                DiscoveryNode newNode = new DiscoveryNode(UUID.randomUUID().toString(), buildNewFakeTransportAddress(), Version.CURRENT);
                return new JoinStatus(newNode, originalJoinStatus.term(), originalJoinStatus.message(), originalJoinStatus.age());
            }
            default -> throw new IllegalStateException();
        }
    }

    private TimeValue randomNonNegativeTimeValue() {
        return new TimeValue(randomIntBetween(0, 1000), randomFrom(TimeUnit.values()));
    }
}
