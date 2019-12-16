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

package org.elasticsearch.painless.ir;

import org.elasticsearch.painless.ClassWriter;
import org.elasticsearch.painless.FunctionRef;
import org.elasticsearch.painless.Globals;
import org.elasticsearch.painless.Locals.Variable;
import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.MethodWriter;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Objects;

public class LambdaNode extends ExpressionNode {

    protected final Location location;
    protected final List<Variable> captures;
    protected final FunctionRef functionRef;

    public LambdaNode(Location location, List<Variable> captures, FunctionRef functionRef) {
        this.location = Objects.requireNonNull(location);
        this.captures = Objects.requireNonNull(captures);
        this.functionRef = Objects.requireNonNull(functionRef);
    }

    @Override
    public void write(ClassWriter classWriter, MethodWriter methodWriter, Globals globals) {
        methodWriter.writeDebugInfo(location);

        if (functionRef != null) {
            methodWriter.writeDebugInfo(location);
            // load captures
            for (Variable capture : captures) {
                methodWriter.visitVarInsn(MethodWriter.getType(capture.clazz).getOpcode(Opcodes.ILOAD), capture.getSlot());
            }

            methodWriter.invokeLambdaCall(functionRef);
        } else {
            // placeholder
            methodWriter.push((String)null);
            // load captures
            for (Variable capture : captures) {
                methodWriter.visitVarInsn(MethodWriter.getType(capture.clazz).getOpcode(Opcodes.ILOAD), capture.getSlot());
            }
        }
    }
}
