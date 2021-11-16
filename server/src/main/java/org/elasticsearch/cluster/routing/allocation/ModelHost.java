/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.routing.allocation;

import org.elasticsearch.cluster.routing.RoutingNode;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A model host hold all the node on it.
 *
 */
public final class ModelHost implements Iterable<RoutingNode> {
    private final List<RoutingNode> nodes = new LinkedList<>();

    public void addNode(RoutingNode node) {
        nodes.add(node);
    }

    public Iterator<RoutingNode> iterator() {
        return nodes.iterator();
    }
}
