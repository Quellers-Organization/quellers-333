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

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.Definition;
import org.elasticsearch.painless.Definition.Method;
import org.elasticsearch.painless.Definition.Sort;
import org.elasticsearch.painless.Definition.Struct;
import org.elasticsearch.painless.Globals;
import org.elasticsearch.painless.Locals;
import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.MethodWriter;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a field load/store shortcut.  (Internal only.)
 */
final class LShortcut extends ALink {

    final String value;

    Method getter = null;
    Method setter = null;

    LShortcut(Location location, String value) {
        super(location, 1);

        this.value = Objects.requireNonNull(value);
    }

    @Override
    void extractVariables(Set<String> variables) {}

    @Override
    ALink analyze(Locals locals) {
        Struct struct = before.struct;

        getter = struct.methods.get(new Definition.MethodKey("get" + Character.toUpperCase(value.charAt(0)) + value.substring(1), 0));

        if (getter == null) {
            getter = struct.methods.get(new Definition.MethodKey("is" + Character.toUpperCase(value.charAt(0)) + value.substring(1), 0));
        }

        setter = struct.methods.get(new Definition.MethodKey("set" + Character.toUpperCase(value.charAt(0)) + value.substring(1), 1));

        if (getter != null && (getter.rtn.sort == Sort.VOID || !getter.arguments.isEmpty())) {
            throw createError(new IllegalArgumentException(
                "Illegal get shortcut on field [" + value + "] for type [" + struct.name + "]."));
        }

        if (setter != null && (setter.rtn.sort != Sort.VOID || setter.arguments.size() != 1)) {
            throw createError(new IllegalArgumentException(
                "Illegal set shortcut on field [" + value + "] for type [" + struct.name + "]."));
        }

        if (getter != null && setter != null && setter.arguments.get(0) != getter.rtn) {
            throw createError(new IllegalArgumentException("Shortcut argument types must match."));
        }

        if ((getter != null || setter != null) && (!load || getter != null) && (!store || setter != null)) {
            after = setter != null ? setter.arguments.get(0) : getter.rtn;
        } else {
            throw createError(new IllegalArgumentException("Illegal shortcut on field [" + value + "] for type [" + struct.name + "]."));
        }

        return this;
    }

    @Override
    void write(MethodWriter writer, Globals globals) {
        // Do nothing.
    }

    @Override
    void load(MethodWriter writer, Globals globals) {
        writer.writeDebugInfo(location);

        getter.write(writer);

        if (!getter.rtn.clazz.equals(getter.handle.type().returnType())) {
            writer.checkCast(getter.rtn.type);
        }
    }

    @Override
    void store(MethodWriter writer, Globals globals) {
        writer.writeDebugInfo(location);

        setter.write(writer);

        writer.writePop(setter.rtn.sort.size);
    }
}
