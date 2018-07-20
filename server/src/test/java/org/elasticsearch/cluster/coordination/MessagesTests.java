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
package org.elasticsearch.cluster.coordination;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.EqualsHashCodeTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class MessagesTests extends ESTestCase {

    private DiscoveryNode createNode(String id) {
        return new DiscoveryNode(id, buildNewFakeTransportAddress(), Version.CURRENT);
    }

    public void testJoinEqualsHashCodeSerialization() {
        Join initialJoin = new Join(createNode(randomAlphaOfLength(10)), createNode(randomAlphaOfLength(10)), randomNonNegativeLong(),
            randomNonNegativeLong(),
            randomNonNegativeLong());
        EqualsHashCodeTestUtils.checkEqualsAndHashCode(initialJoin,
            join -> copyWriteable(join, writableRegistry(), Join::new),
            join -> {
                switch (randomInt(4)) {
                    case 0:
                        // change sourceNode
                        return new Join(createNode(randomAlphaOfLength(20)), join.getTargetNode(), join.getTerm(),
                            join.getLastAcceptedTerm(), join.getLastAcceptedVersion());
                    case 1:
                        // change targetNode
                        return new Join(join.getSourceNode(), createNode(randomAlphaOfLength(20)), join.getTerm(),
                            join.getLastAcceptedTerm(), join.getLastAcceptedVersion());
                    case 2:
                        // change term
                        return new Join(join.getSourceNode(), join.getTargetNode(),
                            randomValueOtherThan(join.getTerm(), ESTestCase::randomNonNegativeLong), join.getLastAcceptedTerm(),
                            join.getLastAcceptedVersion());
                    case 3:
                        // change last accepted term
                        return new Join(join.getSourceNode(), join.getTargetNode(), join.getTerm(),
                            randomValueOtherThan(join.getLastAcceptedTerm(), ESTestCase::randomNonNegativeLong),
                            join.getLastAcceptedVersion());
                    case 4:
                        // change version
                        return new Join(join.getSourceNode(), join.getTargetNode(),
                            join.getTerm(), join.getLastAcceptedTerm(),
                            randomValueOtherThan(join.getLastAcceptedVersion(), ESTestCase::randomNonNegativeLong));
                    default:
                        throw new AssertionError();
                }
            });
    }

    public void testPublishRequestEqualsHashCodeSerialization() {
        PublishRequest initialPublishRequest = new PublishRequest(randomClusterState());
        EqualsHashCodeTestUtils.checkEqualsAndHashCode(initialPublishRequest,
            publishRequest -> copyWriteable(publishRequest, writableRegistry(),
                in -> new PublishRequest(in, publishRequest.getAcceptedState().nodes().getLocalNode())),
            in -> new PublishRequest(randomClusterState()));
    }

    public void testPublishResponseEqualsHashCodeSerialization() {
        PublishResponse initialPublishResponse = new PublishResponse(randomNonNegativeLong(), randomNonNegativeLong());
        EqualsHashCodeTestUtils.checkEqualsAndHashCode(initialPublishResponse,
            publishResponse -> copyWriteable(publishResponse, writableRegistry(), PublishResponse::new),
            publishResponse -> {
                switch (randomInt(1)) {
                    case 0:
                        // change term
                        return new PublishResponse(randomValueOtherThan(publishResponse.getTerm(), ESTestCase::randomNonNegativeLong),
                            publishResponse.getVersion());
                    case 1:
                        // change version
                        return new PublishResponse(publishResponse.getTerm(),
                            randomValueOtherThan(publishResponse.getVersion(), ESTestCase::randomNonNegativeLong));
                    default:
                        throw new AssertionError();
                }
            });
    }

    public void testStartJoinRequestEqualsHashCodeSerialization() {
        StartJoinRequest initialStartJoinRequest = new StartJoinRequest(createNode(randomAlphaOfLength(10)), randomNonNegativeLong());
        EqualsHashCodeTestUtils.checkEqualsAndHashCode(initialStartJoinRequest,
            startJoinRequest -> copyWriteable(startJoinRequest, writableRegistry(), StartJoinRequest::new),
            startJoinRequest -> {
                switch (randomInt(1)) {
                    case 0:
                        // change sourceNode
                        return new StartJoinRequest(createNode(randomAlphaOfLength(20)), startJoinRequest.getTerm());
                    case 1:
                        // change term
                        return new StartJoinRequest(startJoinRequest.getSourceNode(),
                            randomValueOtherThan(startJoinRequest.getTerm(), ESTestCase::randomNonNegativeLong));
                    default:
                        throw new AssertionError();
                }
            });
    }

    public void testApplyCommitEqualsHashCodeSerialization() {
        ApplyCommitRequest initialApplyCommit = new ApplyCommitRequest(createNode(randomAlphaOfLength(10)), randomNonNegativeLong(),
            randomNonNegativeLong());
        EqualsHashCodeTestUtils.checkEqualsAndHashCode(initialApplyCommit,
            applyCommit -> copyWriteable(applyCommit, writableRegistry(), ApplyCommitRequest::new),
            applyCommit -> {
                switch (randomInt(2)) {
                    case 0:
                        // change sourceNode
                        return new ApplyCommitRequest(createNode(randomAlphaOfLength(20)), applyCommit.getTerm(), applyCommit.getVersion());
                    case 1:
                        // change term
                        return new ApplyCommitRequest(applyCommit.getSourceNode(),
                            randomValueOtherThan(applyCommit.getTerm(), ESTestCase::randomNonNegativeLong), applyCommit.getVersion());
                    case 2:
                        // change version
                        return new ApplyCommitRequest(applyCommit.getSourceNode(), applyCommit.getTerm(),
                            randomValueOtherThan(applyCommit.getVersion(), ESTestCase::randomNonNegativeLong));
                    default:
                        throw new AssertionError();
                }
            });
    }

    public ClusterState randomClusterState() {
        return CoordinationStateTests.clusterState(randomNonNegativeLong(), randomNonNegativeLong(), createNode(randomAlphaOfLength(10)),
            new ClusterState.VotingConfiguration(Sets.newHashSet(generateRandomStringArray(10, 10, false))),
            new ClusterState.VotingConfiguration(Sets.newHashSet(generateRandomStringArray(10, 10, false))),
            randomLong());
    }

    private List<DiscoveryNode> modifyDiscoveryNodesList(Collection<DiscoveryNode> originalNodes, boolean allowEmpty) {
        final List<DiscoveryNode> discoveryNodes = new ArrayList<>(originalNodes);
        if (discoveryNodes.isEmpty() == false && randomBoolean() && (allowEmpty || discoveryNodes.size() > 1)) {
            discoveryNodes.remove(randomIntBetween(0, discoveryNodes.size() - 1));
        } else if (discoveryNodes.isEmpty() == false && randomBoolean()) {
            discoveryNodes.set(randomIntBetween(0, discoveryNodes.size() - 1), createNode(randomAlphaOfLength(10)));
        } else {
            discoveryNodes.add(createNode(randomAlphaOfLength(10)));
        }
        return discoveryNodes;
    }

    public void testPeersRequestEqualsHashCodeSerialization() {
        final PeersRequest initialPeersRequest = new PeersRequest(createNode(randomAlphaOfLength(10)),
            Arrays.stream(generateRandomStringArray(10, 10, false)).map(this::createNode).collect(Collectors.toList()));

        EqualsHashCodeTestUtils.checkEqualsAndHashCode(initialPeersRequest,
            publishRequest -> copyWriteable(publishRequest, writableRegistry(), PeersRequest::new),
            in -> {
                final List<DiscoveryNode> discoveryNodes = new ArrayList<>(in.getDiscoveryNodes());
                if (randomBoolean()) {
                    return new PeersRequest(createNode(randomAlphaOfLength(10)), discoveryNodes);
                } else {
                    return new PeersRequest(in.getSourceNode(), modifyDiscoveryNodesList(in.getDiscoveryNodes(), true));
                }
            });
    }

    public void testPeersResponseEqualsHashCodeSerialization() {
        final long initialTerm = randomNonNegativeLong();
        final PeersResponse initialPeersResponse;

        if (randomBoolean()) {
            initialPeersResponse = new PeersResponse(Optional.of(createNode(randomAlphaOfLength(10))), emptyList(), initialTerm);
        } else {
            initialPeersResponse = new PeersResponse(Optional.empty(),
                Arrays.stream(generateRandomStringArray(10, 10, false, false)).map(this::createNode).collect(Collectors.toList()),
                initialTerm);
        }

        EqualsHashCodeTestUtils.checkEqualsAndHashCode(initialPeersResponse,
            publishResponse -> copyWriteable(publishResponse, writableRegistry(), PeersResponse::new),
            in -> {
                final long term = in.getTerm();
                if (randomBoolean()) {
                    return new PeersResponse(in.getMasterNode(), in.getDiscoveryNodes(),
                        randomValueOtherThan(term, ESTestCase::randomNonNegativeLong));
                } else {
                    if (in.getMasterNode().isPresent()) {
                        if (randomBoolean()) {
                            return new PeersResponse(Optional.of(createNode(randomAlphaOfLength(10))), in.getDiscoveryNodes(), term);
                        } else {
                            return new PeersResponse(Optional.empty(), singletonList(createNode(randomAlphaOfLength(10))), term);
                        }
                    } else {
                        if (randomBoolean()) {
                            return new PeersResponse(Optional.of(createNode(randomAlphaOfLength(10))), emptyList(), term);
                        } else {
                            return new PeersResponse(in.getMasterNode(), modifyDiscoveryNodesList(in.getDiscoveryNodes(), false), term);
                        }
                    }
                }
            });
    }
}
