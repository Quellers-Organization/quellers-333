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

package org.elasticsearch.painless.tree.walker.transformer;

import org.elasticsearch.painless.tree.node.Node;
import org.elasticsearch.painless.tree.utility.Variables.Variable;
import org.objectweb.asm.Label;

import static org.elasticsearch.painless.tree.node.Type.ACONSTANT;
import static org.elasticsearch.painless.tree.node.Type.TGOTO;
import static org.elasticsearch.painless.tree.node.Type.TLOOPCOUNT;
import static org.elasticsearch.painless.tree.node.Type.TMARK;
import static org.elasticsearch.painless.tree.node.Type.TPOP;
import static org.elasticsearch.painless.tree.node.Type.TTRAP;
import static org.elasticsearch.painless.tree.node.Type.TVARSTORE;

class TransformerUtility {
    Node jump(final String location, final Label label) {
        final Node jump = new Node(location, TGOTO);
        jump.data.put("label", label);

        return jump;
    }

    Node mark(final String location, final Label label) {
        final Node mark = new Node(location, TMARK);
        mark.data.put("label", label);

        return mark;
    }

    Node loopcount(final String location, final int slot, final int count) {
        final Node loopcount = new Node(location, TLOOPCOUNT);
        loopcount.data.put("slot", slot);
        loopcount.data.put("count", count);

        return loopcount;
    }

    Node trap(final String location, final Variable variable, final Label begin, final Label end, final Label jump) {
        final Node trap = new Node(location, TTRAP);
        trap.data.put("type", variable.type);
        trap.data.put("begin", begin);
        trap.data.put("end", end);
        trap.data.put("jump", jump);

        return trap;
    }

    Node constant(final String location, final Object value) {
        final Node constant = new Node(location, ACONSTANT);
        constant.data.put("constant", value);

        return constant;
    }

    Node varstore(final String location, final Variable variable) {
        final Node store = new Node(location, TVARSTORE);
        store.data.put("type", variable.type);
        store.data.put("slot", variable.slot);

        return store;
    }

    Node pop(final String location, final int size) {
        final Node pop = new Node(location, TPOP);
        pop.data.put("size", size);

        return pop;
    }
}
