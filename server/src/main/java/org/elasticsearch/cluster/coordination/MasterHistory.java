/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.coordination;

import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class represents a node's view of the history of which nodes have been elected master over the last 30 minutes. It is kept in
 * memory, so when a node comes up it does not have any knowledge of previous master history before that point. This object is updated
 * if and when the cluster state changes with a new master node.
 */
public class MasterHistory implements ClusterStateListener {
    private volatile List<TimeAndMaster> masterHistory;
    Supplier<Long> nowSupplier; // Can be changed for testing

    public MasterHistory(ThreadPool threadPool, ClusterService clusterService) {
        this.masterHistory = new ArrayList<>();
        this.nowSupplier = threadPool::relativeTimeInMillis;
        clusterService.addListener(this);
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        DiscoveryNode currentMaster = event.state().nodes().getMasterNode();
        DiscoveryNode previousMaster = event.previousState().nodes().getMasterNode();
        if (currentMaster == null || currentMaster.equals(previousMaster) == false || masterHistory.isEmpty()) {
            List<TimeAndMaster> newMasterHistory = new ArrayList<>(masterHistory);
            newMasterHistory.add(new TimeAndMaster(nowSupplier.get(), currentMaster));
            masterHistory = removeOldMasterHistory(newMasterHistory);
        }
    }

    /**
     * Returns the node that has been most recently seen as the master
     * @return The node that has been most recently seen as the master, which could be null if no master exists
     */
    public @Nullable DiscoveryNode getMostRecentMaster() {
        List<TimeAndMaster> masterHistoryCopy = getMasterHistoryForLast30Minutes(masterHistory);
        return masterHistoryCopy.isEmpty() ? null : masterHistoryCopy.get(masterHistoryCopy.size() - 1).master;
    }

    /**
     * Returns the most recent non-null master seen, or null if there has been no master seen. Only 30 minutes of history is kept. If the
     * most recent master change is more than 30 minutes old and that change was to set the master to null, then null will be returned.
     * @return The most recent non-null master seen, or null if there has been no master seen.
     */
    public @Nullable DiscoveryNode getMostRecentNonNullMaster() {
        List<TimeAndMaster> masterHistoryCopy = getMasterHistoryForLast30Minutes(masterHistory);
        Collections.reverse(masterHistoryCopy);
        for (TimeAndMaster timeAndMaster : masterHistoryCopy) {
            if (timeAndMaster.master != null) {
                return timeAndMaster.master;
            }
        }
        return null;
    }

    /**
     * Returns true if for the life of this MasterHistory (30 minutes) only one non-null node has been master, and the master has switched
     * from that node to null n times.
     * @param n The number of times the non-null master must have switched to null
     * @return True if there has been a single non-null master and it has switched to null n or more times.
     */
    public boolean hasSameMasterGoneNullNTimes(int n) {
        List<TimeAndMaster> masterHistoryCopy = getMasterHistoryForLast30Minutes(masterHistory);
        return hasSameMasterGoneNullNTimes(masterHistoryCopy.stream().map(TimeAndMaster::master).toList(), n);
    }

    /**
     * Returns true if for List of master nodes passed in only one non-null node has been master, and the master has switched
     * from that node to null n times.
     * @param masters The List of masters to use
     * @param n The number of times the non-null master must have switched to null
     * @return True if there has been a single non-null master and it has switched to null n or more times in the given list of masters.
     */
    public static boolean hasSameMasterGoneNullNTimes(List<DiscoveryNode> masters, int n) {
        if (getDistinctMastersSeen(masters).size() != 1) {
            return false;
        }
        boolean seenNonNull = false;
        int timesMasterHasGoneNull = 0;
        for (DiscoveryNode master : masters) {
            if (master != null) {
                seenNonNull = true;
            } else if (seenNonNull) {
                timesMasterHasGoneNull++;
            }
        }
        return timesMasterHasGoneNull >= n;
    }

    private static Set<DiscoveryNode> getDistinctMastersSeen(List<DiscoveryNode> masters) {
        return masters.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the set of distinct non-null master nodes seen in this history.
     * @return The set of all non-null master nodes seen. Could be empty
     */
    public Set<DiscoveryNode> getDistinctMastersSeen() {
        List<TimeAndMaster> masterHistoryCopy = getMasterHistoryForLast30Minutes(masterHistory);
        return masterHistoryCopy.stream().map(TimeAndMaster::master).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns true if a non-null master was seen at any point in the last n seconds, or if the last-seen master was more than n seconds
     * ago and non-null.
     * @param n The number of seconds to look back
     * @return true if the current master is non-null or if a non-null master was seen in the last n seconds
     */
    public boolean hasSeenMasterInLastNSeconds(int n) {
        List<TimeAndMaster> masterHistoryCopy = getMasterHistoryForLast30Minutes(masterHistory);
        long now = nowSupplier.get();
        TimeValue nSeconds = new TimeValue(n, TimeUnit.SECONDS);
        long nSecondsAgo = now - nSeconds.getMillis();
        return getMostRecentMaster() != null
            || masterHistoryCopy.stream()
                .filter(timeAndMaster -> timeAndMaster.master != null)
                .anyMatch(timeAndMaster -> timeAndMaster.time > nSecondsAgo);
    }

    /*
     * This method creates a copy of masterHistory that only has entries from more than 30 minutes before now (but leaves the newest
     * entry in even if it is more than 30 minutes old).
     */
    private List<TimeAndMaster> getMasterHistoryForLast30Minutes(List<TimeAndMaster> history) {
        if (history.size() < 2) {
            return history;
        }
        long now = nowSupplier.get();
        TimeValue thirtyMinutes = new TimeValue(30, TimeUnit.MINUTES);
        long thirtyMinutesAgo = now - thirtyMinutes.getMillis();
        TimeAndMaster mostRecent = history.isEmpty() ? null : history.get(history.size() - 1);
        List<TimeAndMaster> filteredHistory = history.stream()
            .filter(timeAndMaster -> timeAndMaster.time > thirtyMinutesAgo)
            .collect(Collectors.toList());
        if (filteredHistory.isEmpty() && mostRecent != null) { // The most recent entry was more than 30 minutes ago
            filteredHistory.add(mostRecent);
        }
        return filteredHistory;
    }

    /**
     * Returns a new MasterHistory with nothing from more than 30 minutes before now (but leaves the newest entry in even if it is
     * more than 30 minutes old).
     */
    private List<TimeAndMaster> removeOldMasterHistory(List<TimeAndMaster> possiblyOldMasterHistory) {
        if (possiblyOldMasterHistory.size() < 2) {
            return new ArrayList<>(possiblyOldMasterHistory);
        }
        long now = nowSupplier.get();
        TimeValue thirtyMinutes = new TimeValue(30, TimeUnit.MINUTES);
        long thirtyMinutesAgo = now - thirtyMinutes.getMillis();
        TimeAndMaster mostRecent = possiblyOldMasterHistory.isEmpty()
            ? null
            : possiblyOldMasterHistory.get(possiblyOldMasterHistory.size() - 1);
        List<TimeAndMaster> newMasterHistory = possiblyOldMasterHistory.stream()
            .filter(timeAndMaster -> timeAndMaster.time >= thirtyMinutesAgo)
            .collect(Collectors.toList());
        if (newMasterHistory.isEmpty() && mostRecent != null) { // The most recent entry was more than 30 minutes ago
            newMasterHistory.add(mostRecent);
        }
        return newMasterHistory;
    }

    /**
     * This method returns an immutable view of this master history, typically for sending over the wire to another node.
     * @return An immutable view of this master history
     */
    public List<DiscoveryNode> getImmutableView() {
        List<TimeAndMaster> masterHistoryCopy = getMasterHistoryForLast30Minutes(masterHistory);
        return masterHistoryCopy.stream().map(TimeAndMaster::master).toList();
    }

    private record TimeAndMaster(long time, DiscoveryNode master) {}
}
