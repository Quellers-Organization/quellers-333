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
import org.elasticsearch.painless.MethodWriter;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

public class LambdaNode extends ExpressionNode {

    /* ---- begin node data ---- */

    private final List<Variable> captures = new ArrayList<>();
    private FunctionRef funcRef;

    public void addCapture(Variable capture) {
        captures.add(capture);
    }

    public List<Variable> getCaptures() {
        return captures;
    }

    public void setFuncRef(FunctionRef funcRef) {
        this.funcRef = funcRef;
    }

    public FunctionRef getFuncRef() {
        return funcRef;
    }

    /* ---- end node data ---- */

    public LambdaNode() {
        // do nothing
    }

    @Override
    protected void write(ClassWriter classWriter, MethodWriter methodWriter, Globals globals) {
        methodWriter.writeDebugInfo(location);

        if (funcRef != null) {
            methodWriter.writeDebugInfo(location);
            // load captures
            for (Variable capture : captures) {
                methodWriter.visitVarInsn(MethodWriter.getType(capture.clazz).getOpcode(Opcodes.ILOAD), capture.getSlot());
            }

            methodWriter.invokeLambdaCall(funcRef);
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
