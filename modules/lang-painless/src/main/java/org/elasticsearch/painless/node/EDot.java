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

import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.symbol.ScriptScope;
import org.elasticsearch.painless.symbol.Decorator;
import org.elasticsearch.painless.symbol.SemanticScope;
import org.elasticsearch.painless.ir.ClassNode;
import org.elasticsearch.painless.ir.ConstantNode;
import org.elasticsearch.painless.ir.DotNode;
import org.elasticsearch.painless.ir.DotSubArrayLengthNode;
import org.elasticsearch.painless.ir.DotSubDefNode;
import org.elasticsearch.painless.ir.DotSubNode;
import org.elasticsearch.painless.ir.DotSubShortcutNode;
import org.elasticsearch.painless.ir.ExpressionNode;
import org.elasticsearch.painless.ir.ListSubShortcutNode;
import org.elasticsearch.painless.ir.MapSubShortcutNode;
import org.elasticsearch.painless.ir.NullSafeSubNode;
import org.elasticsearch.painless.ir.StaticNode;
import org.elasticsearch.painless.lookup.PainlessField;
import org.elasticsearch.painless.lookup.PainlessLookupUtility;
import org.elasticsearch.painless.lookup.PainlessMethod;
import org.elasticsearch.painless.lookup.def;

import java.lang.reflect.Modifier;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.painless.lookup.PainlessLookupUtility.typeToCanonicalTypeName;

/**
 * Represents a field load/store and defers to a child subnode.
 */
public class EDot extends AExpression {

    private final AExpression prefixNode;
    private final String index;
    private final boolean isNullSafe;

    public EDot(int identifier, Location location, AExpression prefixNode, String index, boolean isNullSafe) {
        super(identifier, location);

        this.prefixNode = Objects.requireNonNull(prefixNode);
        this.index = Objects.requireNonNull(index);
        this.isNullSafe = isNullSafe;
    }

    public AExpression getPrefixNode() {
        return prefixNode;
    }

    public String getIndex() {
        return index;
    }

    public boolean isNullSafe() {
        return isNullSafe;
    }

    @Override
    Output analyze(ClassNode classNode, SemanticScope semanticScope, Input input) {
        boolean write = semanticScope.getCondition(this, Decorator.Write.class);

        if (input.read == false && write == false) {
            throw createError(new IllegalArgumentException("not a statement: result of dot operator [.] not used"));
        }

        ScriptScope scriptScope = semanticScope.getScriptScope();

        Output output = new Output();
        Class<?> valueType = null;
        
        Output prefixOutput = prefixNode.analyze(classNode, semanticScope, new Input());


        if (prefixOutput.partialCanonicalTypeName != null) {
            if (prefixOutput.isStaticType) {
                throw createError(new IllegalArgumentException("value required: instead found unexpected type " +
                        "[" + semanticScope.getDecoration(prefixNode, Decorator.ValueType.class).getCanonicalTypeName() + "]"));
            }

            String canonicalTypeName = prefixOutput.partialCanonicalTypeName + "." + index;
            Class<?> type = scriptScope.getPainlessLookup().canonicalTypeNameToType(canonicalTypeName);

            if (type == null) {
                output.partialCanonicalTypeName = canonicalTypeName;
            } else {
                if (write) {
                    throw createError(new IllegalArgumentException("invalid assignment: " +
                            "cannot write a value to a static type [" + PainlessLookupUtility.typeToCanonicalTypeName(type) + "]"));
                }

                if (input.read == false) {
                    throw createError(new IllegalArgumentException(
                            "not a statement: static type [" + PainlessLookupUtility.typeToCanonicalTypeName(type) + "] not used"));
                }

                valueType = type;
                output.isStaticType = true;

                StaticNode staticNode = new StaticNode();

                staticNode.setLocation(getLocation());
                staticNode.setExpressionType(valueType);

                output.expressionNode = staticNode;
            }
        } else {
            Class<?> prefixValueType = semanticScope.getDecoration(prefixNode, Decorator.ValueType.class).getValueType();
            String targetCanonicalTypeName = PainlessLookupUtility.typeToCanonicalTypeName(prefixValueType);

            ExpressionNode expressionNode = null;

            if (prefixValueType.isArray()) {
                if (prefixOutput.isStaticType) {
                    throw createError(new IllegalArgumentException("value required: " +
                            "instead found unexpected type [" + PainlessLookupUtility.typeToCanonicalTypeName(prefixValueType) + "]"));
                }

                if ("length".equals(index)) {
                    if (write) {
                        throw createError(new IllegalArgumentException(
                                "invalid assignment: cannot assign a value write to read-only field [length] for an array."));
                    }

                    valueType = int.class;
                } else {
                    throw createError(new IllegalArgumentException(
                            "Field [" + index + "] does not exist for type [" + targetCanonicalTypeName + "]."));
                }

                DotSubArrayLengthNode dotSubArrayLengthNode = new DotSubArrayLengthNode();
                dotSubArrayLengthNode.setLocation(getLocation());
                dotSubArrayLengthNode.setExpressionType(valueType);
                expressionNode = dotSubArrayLengthNode;
            } else if (prefixValueType == def.class) {
                if (prefixOutput.isStaticType) {
                    throw createError(new IllegalArgumentException("value required: " +
                            "instead found unexpected type [" + PainlessLookupUtility.typeToCanonicalTypeName(prefixValueType) + "]"));
                }

                // TODO: remove ZonedDateTime exception when JodaCompatibleDateTime is removed
                valueType = input.expected == null || input.expected == ZonedDateTime.class || input.explicit ? def.class : input.expected;
                output.isDefOptimized = true;

                DotSubDefNode dotSubDefNode = new DotSubDefNode();
                dotSubDefNode.setLocation(getLocation());
                dotSubDefNode.setExpressionType(valueType);
                dotSubDefNode.setValue(index);
                expressionNode = dotSubDefNode;
            } else {
                PainlessField field =
                        scriptScope.getPainlessLookup().lookupPainlessField(prefixValueType, prefixOutput.isStaticType, index);

                if (field == null) {
                    PainlessMethod getter;
                    PainlessMethod setter;

                    getter = scriptScope.getPainlessLookup().lookupPainlessMethod(prefixValueType, false,
                            "get" + Character.toUpperCase(index.charAt(0)) + index.substring(1), 0);

                    if (getter == null) {
                        getter = scriptScope.getPainlessLookup().lookupPainlessMethod(prefixValueType, false,
                                "is" + Character.toUpperCase(index.charAt(0)) + index.substring(1), 0);
                    }

                    setter = scriptScope.getPainlessLookup().lookupPainlessMethod(prefixValueType, false,
                            "set" + Character.toUpperCase(index.charAt(0)) + index.substring(1), 0);

                    if (getter != null || setter != null) {
                        if (getter != null && (getter.returnType == void.class || !getter.typeParameters.isEmpty())) {
                            throw createError(new IllegalArgumentException(
                                    "Illegal get shortcut on field [" + index + "] for type [" + targetCanonicalTypeName + "]."));
                        }

                        if (setter != null && (setter.returnType != void.class || setter.typeParameters.size() != 1)) {
                            throw createError(new IllegalArgumentException(
                                    "Illegal set shortcut on field [" + index + "] for type [" + targetCanonicalTypeName + "]."));
                        }

                        if (getter != null && setter != null && setter.typeParameters.get(0) != getter.returnType) {
                            throw createError(new IllegalArgumentException("Shortcut argument types must match."));
                        }

                        if ((input.read == false || getter != null) && (write == false || setter != null)) {
                            valueType = setter != null ? setter.typeParameters.get(0) : getter.returnType;
                        } else {
                            throw createError(new IllegalArgumentException(
                                    "Illegal shortcut on field [" + index + "] for type [" + targetCanonicalTypeName + "]."));
                        }

                        DotSubShortcutNode dotSubShortcutNode = new DotSubShortcutNode();
                        dotSubShortcutNode.setLocation(getLocation());
                        dotSubShortcutNode.setExpressionType(valueType);
                        dotSubShortcutNode.setGetter(getter);
                        dotSubShortcutNode.setSetter(setter);
                        expressionNode = dotSubShortcutNode;
                    } else {
                        if (Map.class.isAssignableFrom(prefixValueType)) {
                            getter = scriptScope.getPainlessLookup().lookupPainlessMethod(prefixValueType, false, "get", 1);
                            setter = scriptScope.getPainlessLookup().lookupPainlessMethod(prefixValueType, false, "put", 2);

                            if (getter != null && (getter.returnType == void.class || getter.typeParameters.size() != 1)) {
                                throw createError(new IllegalArgumentException(
                                        "Illegal map get shortcut for type [" + targetCanonicalTypeName + "]."));
                            }

                            if (setter != null && setter.typeParameters.size() != 2) {
                                throw createError(new IllegalArgumentException(
                                        "Illegal map set shortcut for type [" + targetCanonicalTypeName + "]."));
                            }

                            if (getter != null && setter != null && (!getter.typeParameters.get(0).equals(setter.typeParameters.get(0)) ||
                                    !getter.returnType.equals(setter.typeParameters.get(1)))) {
                                throw createError(new IllegalArgumentException("Shortcut argument types must match."));
                            }

                            if ((input.read || write)
                                    && (input.read == false || getter != null) && (write == false || setter != null)) {
                                valueType = setter != null ? setter.typeParameters.get(1) : getter.returnType;
                            } else {
                                throw createError(new IllegalArgumentException(
                                        "Illegal map shortcut for type [" + targetCanonicalTypeName + "]."));
                            }

                            ConstantNode constantNode = new ConstantNode();
                            constantNode.setLocation(getLocation());
                            constantNode.setExpressionType(String.class);
                            constantNode.setConstant(index);

                            MapSubShortcutNode mapSubShortcutNode = new MapSubShortcutNode();
                            mapSubShortcutNode.setChildNode(constantNode);
                            mapSubShortcutNode.setLocation(getLocation());
                            mapSubShortcutNode.setExpressionType(valueType);
                            mapSubShortcutNode.setGetter(getter);
                            mapSubShortcutNode.setSetter(setter);
                            expressionNode = mapSubShortcutNode;
                        }

                        if (List.class.isAssignableFrom(prefixValueType)) {
                            int index;

                            try {
                                index = Integer.parseInt(this.index);
                            } catch (NumberFormatException nfe) {
                                throw createError(new IllegalArgumentException("invalid list index [" + this.index + "]"));
                            }

                            getter = scriptScope.getPainlessLookup().lookupPainlessMethod(prefixValueType, false, "get", 1);
                            setter = scriptScope.getPainlessLookup().lookupPainlessMethod(prefixValueType, false, "set", 2);

                            if (getter != null && (getter.returnType == void.class || getter.typeParameters.size() != 1 ||
                                    getter.typeParameters.get(0) != int.class)) {
                                throw createError(new IllegalArgumentException(
                                        "Illegal list get shortcut for type [" + targetCanonicalTypeName + "]."));
                            }

                            if (setter != null && (setter.typeParameters.size() != 2 || setter.typeParameters.get(0) != int.class)) {
                                throw createError(new IllegalArgumentException(
                                        "Illegal list set shortcut for type [" + targetCanonicalTypeName + "]."));
                            }

                            if (getter != null && setter != null && (!getter.typeParameters.get(0).equals(setter.typeParameters.get(0))
                                    || !getter.returnType.equals(setter.typeParameters.get(1)))) {
                                throw createError(new IllegalArgumentException("Shortcut argument types must match."));
                            }

                            if ((input.read || write)
                                    && (input.read == false || getter != null) && (write == false || setter != null)) {
                                valueType = setter != null ? setter.typeParameters.get(1) : getter.returnType;
                            } else {
                                throw createError(new IllegalArgumentException(
                                        "Illegal list shortcut for type [" + targetCanonicalTypeName + "]."));
                            }

                            ConstantNode constantNode = new ConstantNode();
                            constantNode.setLocation(getLocation());
                            constantNode.setExpressionType(int.class);
                            constantNode.setConstant(index);

                            ListSubShortcutNode listSubShortcutNode = new ListSubShortcutNode();
                            listSubShortcutNode.setChildNode(constantNode);
                            listSubShortcutNode.setLocation(getLocation());
                            listSubShortcutNode.setExpressionType(valueType);
                            listSubShortcutNode.setGetter(getter);
                            listSubShortcutNode.setSetter(setter);
                            expressionNode = listSubShortcutNode;
                        }
                    }

                    if (expressionNode == null) {
                        throw createError(new IllegalArgumentException(
                                "field [" + typeToCanonicalTypeName(prefixValueType) + ", " + index + "] not found"));
                    }
                } else {
                    if (write && Modifier.isFinal(field.javaField.getModifiers())) {
                        throw createError(new IllegalArgumentException(
                                "invalid assignment: cannot assign a value to read-only field [" + field.javaField.getName() + "]"));
                    }

                    valueType = field.typeParameter;

                    DotSubNode dotSubNode = new DotSubNode();
                    dotSubNode.setLocation(getLocation());
                    dotSubNode.setExpressionType(valueType);
                    dotSubNode.setField(field);
                    expressionNode = dotSubNode;
                }
            }

            if (isNullSafe) {
                if (write) {
                    throw createError(new IllegalArgumentException(
                            "invalid assignment: cannot assign a value to a null safe operation [?.]"));
                }

                if (valueType.isPrimitive()) {
                    throw new IllegalArgumentException("Result of null safe operator must be nullable");
                }

                NullSafeSubNode nullSafeSubNode = new NullSafeSubNode();
                nullSafeSubNode.setChildNode(expressionNode);
                nullSafeSubNode.setLocation(getLocation());
                nullSafeSubNode.setExpressionType(valueType);
                expressionNode = nullSafeSubNode;
            }

            DotNode dotNode = new DotNode();
            dotNode.setLeftNode(prefixOutput.expressionNode);
            dotNode.setRightNode(expressionNode);
            dotNode.setLocation(getLocation());
            dotNode.setExpressionType(valueType);
            output.expressionNode = dotNode;
        }

        if (valueType != null) {
            semanticScope.addDecoration(this, new Decorator.ValueType(valueType));
        }

        return output;
    }
}
