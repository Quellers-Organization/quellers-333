/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.snapshotlifecycle;

import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.metadata.RepositoriesMetaData;
import org.elasticsearch.cluster.metadata.RepositoryMetaData;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ClusterServiceUtils;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.core.scheduler.SchedulerEngine;
import org.elasticsearch.xpack.core.snapshotlifecycle.SnapshotLifecycleMetadata;
import org.elasticsearch.xpack.core.snapshotlifecycle.SnapshotLifecyclePolicy;
import org.elasticsearch.xpack.core.snapshotlifecycle.SnapshotLifecyclePolicyMetadata;
import org.elasticsearch.xpack.core.watcher.watch.ClockMock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

public class SnapshotLifecycleServiceTests extends ESTestCase {

    public void testGetJobId() {
        String id = randomAlphaOfLengthBetween(1, 10) + (randomBoolean() ? "" : randomLong());
        SnapshotLifecyclePolicy policy = createPolicy(id);
        long version = randomNonNegativeLong();
        SnapshotLifecyclePolicyMetadata meta = new SnapshotLifecyclePolicyMetadata(policy, Collections.emptyMap(), version, 1);
        assertThat(SnapshotLifecycleService.getJobId(meta), equalTo(id + "-" + version));
    }

    public void testRepositoryExistence() {
        ClusterState state = ClusterState.builder(new ClusterName("cluster")).build();

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
            () -> SnapshotLifecycleService.validateRepositoryExists("repo", state));

        assertThat(e.getMessage(), containsString("no such repository [repo]"));

        boolean shouldExist = randomBoolean();
        RepositoryMetaData repo = new RepositoryMetaData(shouldExist ? "repo" : "other", "fs", Settings.EMPTY);
        RepositoriesMetaData repoMeta = new RepositoriesMetaData(Collections.singletonList(repo));
        ClusterState stateWithRepo = ClusterState.builder(state)
            .metaData(MetaData.builder()
            .putCustom(RepositoriesMetaData.TYPE, repoMeta))
            .build();

        if (shouldExist) {
            SnapshotLifecycleService.validateRepositoryExists("repo", stateWithRepo);
        } else {
            // There is a repo, but not the one we're looking for
            IllegalArgumentException e2 = expectThrows(IllegalArgumentException.class,
                () -> SnapshotLifecycleService.validateRepositoryExists("repo", state));

            assertThat(e2.getMessage(), containsString("no such repository [repo]"));
        }
    }

    /**
     * Test new policies getting scheduled correctly, updated policies also being scheduled,
     * and deleted policies having their schedules cancelled.
     */
    public void testPolicyCRUD() throws Exception {
        ClockMock clock = new ClockMock();
        final AtomicInteger triggerCount = new AtomicInteger(0);
        final AtomicReference<Consumer<SchedulerEngine.Event>> trigger = new AtomicReference<>(e -> triggerCount.incrementAndGet());
        try (ThreadPool threadPool = new TestThreadPool("test");
             ClusterService clusterService = ClusterServiceUtils.createClusterService(threadPool);
             SnapshotLifecycleService sls = new SnapshotLifecycleService(Settings.EMPTY,
                 () -> new FakeSnapshotTask(e -> trigger.get().accept(e)), clusterService, clock)) {

            sls.offMaster();
            SnapshotLifecycleMetadata snapMeta = new SnapshotLifecycleMetadata(Collections.emptyMap());
            ClusterState previousState = createState(snapMeta);
            Map<String, SnapshotLifecyclePolicyMetadata> policies = new HashMap<>();

            SnapshotLifecyclePolicyMetadata policy =
                new SnapshotLifecyclePolicyMetadata(createPolicy("foo", "*/1 * * * * ?"), // trigger every second
                    Collections.emptyMap(), 1, 1);
            policies.put(policy.getPolicy().getId(), policy);
            snapMeta = new SnapshotLifecycleMetadata(policies);
            ClusterState state = createState(snapMeta);
            ClusterChangedEvent event = new ClusterChangedEvent("1", state, previousState);
            trigger.set(e -> {
                fail("trigger should not be invoked");
            });
            sls.clusterChanged(event);

            // Since the service does not think it is master, it should not be triggered or scheduled
            assertThat(sls.getScheduler().scheduledJobIds(), equalTo(Collections.emptySet()));

            // Change the service to think it's on the master node, events should be scheduled now
            sls.onMaster();
            trigger.set(e -> triggerCount.incrementAndGet());
            sls.clusterChanged(event);
            assertThat(sls.getScheduler().scheduledJobIds(), equalTo(Collections.singleton("foo-1")));

            assertBusy(() -> assertThat(triggerCount.get(), greaterThan(0)));

            clock.freeze();
            int currentCount = triggerCount.get();
            previousState = state;
            SnapshotLifecyclePolicyMetadata newPolicy =
                new SnapshotLifecyclePolicyMetadata(createPolicy("foo", "*/1 * * * * ?"), Collections.emptyMap(), 2, 2);
            policies.put(policy.getPolicy().getId(), newPolicy);
            state = createState(new SnapshotLifecycleMetadata(policies));
            event = new ClusterChangedEvent("2", state, previousState);
            sls.clusterChanged(event);
            assertThat(sls.getScheduler().scheduledJobIds(), equalTo(Collections.singleton("foo-2")));

            trigger.set(e -> {
                // Make sure the job got updated
                assertThat(e.getJobName(), equalTo("foo-2"));
                triggerCount.incrementAndGet();
            });
            clock.fastForwardSeconds(1);

            assertBusy(() -> assertThat(triggerCount.get(), greaterThan(currentCount)));

            final int currentCount2 = triggerCount.get();
            previousState = state;
            // Create a state simulating the policy being deleted
            state = createState(new SnapshotLifecycleMetadata(Collections.emptyMap()));
            event = new ClusterChangedEvent("2", state, previousState);
            sls.clusterChanged(event);
            clock.fastForwardSeconds(2);

            // The existing job should be cancelled and no longer trigger
            assertThat(triggerCount.get(), equalTo(currentCount2));
            assertThat(sls.getScheduler().scheduledJobIds(), equalTo(Collections.emptySet()));

            // When the service is no longer master, all jobs should be automatically cancelled
            policy =
                new SnapshotLifecyclePolicyMetadata(createPolicy("foo", "*/1 * * * * ?"), // trigger every second
                    Collections.emptyMap(), 3, 1);
            policies.put(policy.getPolicy().getId(), policy);
            snapMeta = new SnapshotLifecycleMetadata(policies);
            previousState = state;
            state = createState(snapMeta);
            event = new ClusterChangedEvent("1", state, previousState);
            trigger.set(e -> triggerCount.incrementAndGet());
            sls.clusterChanged(event);
            clock.fastForwardSeconds(2);

            // Make sure at least one triggers and the job is scheduled
            assertBusy(() -> assertThat(triggerCount.get(), greaterThan(currentCount2)));
            assertThat(sls.getScheduler().scheduledJobIds(), equalTo(Collections.singleton("foo-3")));

            // Signify becoming non-master, the jobs should all be cancelled
            sls.offMaster();
            assertThat(sls.getScheduler().scheduledJobIds(), equalTo(Collections.emptySet()));

            threadPool.shutdownNow();
        }
    }

    /**
     * Test for policy ids ending in numbers the way generate job ids doesn't cause confusion
     */
    public void testPolicyNamesEndingInNumbers() throws Exception {
        ClockMock clock = new ClockMock();
        final AtomicInteger triggerCount = new AtomicInteger(0);
        final AtomicReference<Consumer<SchedulerEngine.Event>> trigger = new AtomicReference<>(e -> triggerCount.incrementAndGet());
        try (ThreadPool threadPool = new TestThreadPool("test");
             ClusterService clusterService = ClusterServiceUtils.createClusterService(threadPool);
             SnapshotLifecycleService sls = new SnapshotLifecycleService(Settings.EMPTY,
                 () -> new FakeSnapshotTask(e -> trigger.get().accept(e)), clusterService, clock)) {
            sls.onMaster();

            SnapshotLifecycleMetadata snapMeta = new SnapshotLifecycleMetadata(Collections.emptyMap());
            ClusterState previousState = createState(snapMeta);
            Map<String, SnapshotLifecyclePolicyMetadata> policies = new HashMap<>();

            SnapshotLifecyclePolicyMetadata policy =
                new SnapshotLifecyclePolicyMetadata(createPolicy("foo-2", "30 * * * * ?"),
                    Collections.emptyMap(), 1, 1);
            policies.put(policy.getPolicy().getId(), policy);
            snapMeta = new SnapshotLifecycleMetadata(policies);
            ClusterState state = createState(snapMeta);
            ClusterChangedEvent event = new ClusterChangedEvent("1", state, previousState);
            sls.clusterChanged(event);

            assertThat(sls.getScheduler().scheduledJobIds(), equalTo(Collections.singleton("foo-2-1")));

            previousState = state;
            SnapshotLifecyclePolicyMetadata secondPolicy =
                new SnapshotLifecyclePolicyMetadata(createPolicy("foo-1", "45 * * * * ?"),
                    Collections.emptyMap(), 2, 1);
            policies.put(secondPolicy.getPolicy().getId(), secondPolicy);
            snapMeta = new SnapshotLifecycleMetadata(policies);
            state = createState(snapMeta);
            event = new ClusterChangedEvent("2", state, previousState);
            sls.clusterChanged(event);

            assertThat(sls.getScheduler().scheduledJobIds(), containsInAnyOrder("foo-2-1", "foo-1-2"));

            sls.offMaster();
            assertThat(sls.getScheduler().scheduledJobIds(), equalTo(Collections.emptySet()));

            threadPool.shutdownNow();
        }
    }

    class FakeSnapshotTask extends SnapshotLifecycleTask {
        private final Consumer<SchedulerEngine.Event> onTriggered;

        FakeSnapshotTask(Consumer<SchedulerEngine.Event> onTriggered) {
            super(null, null);
            this.onTriggered = onTriggered;
        }

        @Override
        public void triggered(SchedulerEngine.Event event) {
            logger.info("--> fake snapshot task triggered");
            onTriggered.accept(event);
        }
    }

    public ClusterState createState(SnapshotLifecycleMetadata snapMeta) {
        MetaData metaData = MetaData.builder()
            .putCustom(SnapshotLifecycleMetadata.TYPE, snapMeta)
            .build();
        return ClusterState.builder(new ClusterName("cluster"))
            .metaData(metaData)
            .build();
    }

    public static SnapshotLifecyclePolicy createPolicy(String id) {
        return createPolicy(id, randomSchedule());
    }

    public static SnapshotLifecyclePolicy createPolicy(String id, String schedule) {
        Map<String, Object> config = new HashMap<>();
        config.put("ignore_unavailable", randomBoolean());
        List<String> indices = new ArrayList<>();
        indices.add("foo-*");
        indices.add(randomAlphaOfLength(4));
        config.put("indices", indices);
        return new SnapshotLifecyclePolicy(id, randomAlphaOfLength(4), schedule, randomAlphaOfLength(4), config);
    }

    private static String randomSchedule() {
        return randomIntBetween(0, 59) + " " +
            randomIntBetween(0, 59) + " " +
            randomIntBetween(0, 12) + " * * ?";
    }
}
