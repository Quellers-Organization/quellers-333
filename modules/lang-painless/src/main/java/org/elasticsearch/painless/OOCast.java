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

package org.elasticsearch.painless;

import org.elasticsearch.painless.Definition.Type;

public interface OOCast { // NOCOMMIT rename
    void write(MethodWriter writer);
    Object castConstant(Location location, Object constant);

    /**
     * Cast that doesn't do anything. Used when you don't need to cast at all.
     */
    OOCast NOOP = new OOCast() {
        @Override
        public void write(MethodWriter writer) {
        }

        @Override
        public Object castConstant(Location location, Object constant) {
            return constant;
        }

        @Override
        public String toString() {
            return "noop";
        }
    };

    class Box implements OOCast {
        private final Type from;

        public Box(Type from) {
            this.from = from;
        }

        @Override
        public void write(MethodWriter writer) {
            writer.unbox(from.type);
        }

        @Override
        public Object castConstant(Location location, Object constant) {
            return constant; // NOCOMMIT check me
        }
    }
}
