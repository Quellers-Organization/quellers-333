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

import org.elasticsearch.painless.AnalyzerCaster;
import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.symbol.Decorator;
import org.elasticsearch.painless.symbol.SemanticScope;
import org.elasticsearch.painless.ir.ClassNode;
import org.elasticsearch.painless.ir.ConditionalNode;
import org.elasticsearch.painless.lookup.PainlessCast;
import org.elasticsearch.painless.lookup.PainlessLookupUtility;

import java.util.Objects;

/**
 * Represents a conditional expression.
 */
public class EConditional extends AExpression {

    private final AExpression conditionNode;
    private final AExpression leftNode;
    private final AExpression rightNode;

    public EConditional(int identifier, Location location, AExpression conditionNode, AExpression leftNode, AExpression rightNode) {
        super(identifier, location);

        this.conditionNode = Objects.requireNonNull(conditionNode);
        this.leftNode = Objects.requireNonNull(leftNode);
        this.rightNode = Objects.requireNonNull(rightNode);
    }

    public AExpression getConditionNode() {
        return conditionNode;
    }

    public AExpression getLeftNode() {
        return leftNode;
    }

    public AExpression getRightNode() {
        return rightNode;
    }

    @Override
    Output analyze(ClassNode classNode, SemanticScope semanticScope, Input input) {
        if (semanticScope.getCondition(this, Decorator.Write.class)) {
            throw createError(new IllegalArgumentException("invalid assignment: cannot assign a value to conditional operation [?:]"));
        }

        if (input.read == false) {
            throw createError(new IllegalArgumentException("not a statement: result not used from conditional operation [?:]"));
        }

        Output output = new Output();

        Input conditionInput = new Input();
        conditionInput.expected = boolean.class;
        Output conditionOutput = analyze(conditionNode, classNode, semanticScope, conditionInput);
        Class<?> conditionValueType = semanticScope.getDecoration(conditionNode, Decorator.ValueType.class).getValueType();
        PainlessCast conditionCast = AnalyzerCaster.getLegalCast(classNode.getLocation(),
                conditionValueType, conditionInput.expected, conditionInput.explicit, conditionInput.internal);

        Input leftInput = new Input();
        leftInput.expected = input.expected;
        leftInput.explicit = input.explicit;
        leftInput.internal = input.internal;
        Output leftOutput = analyze(leftNode, classNode, semanticScope, leftInput);
        Class<?> leftValueType = semanticScope.getDecoration(leftNode, Decorator.ValueType.class).getValueType();

        Input rightInput = new Input();
        rightInput.expected = input.expected;
        rightInput.explicit = input.explicit;
        rightInput.internal = input.internal;
        Output rightOutput = analyze(rightNode, classNode, semanticScope, rightInput);
        Class<?> rightValueType = semanticScope.getDecoration(rightNode, Decorator.ValueType.class).getValueType();

        Class<?> valueType = input.expected;

        if (input.expected == null) {
            Class<?> promote = AnalyzerCaster.promoteConditional(leftValueType, rightValueType);

            if (promote == null) {
                throw createError(new ClassCastException("cannot apply the conditional operator [?:] to the types " +
                        "[" + PainlessLookupUtility.typeToCanonicalTypeName(leftValueType) + "] and " +
                        "[" + PainlessLookupUtility.typeToCanonicalTypeName(rightValueType) + "]"));
            }

            leftInput.expected = promote;
            rightInput.expected = promote;
            valueType = promote;
        }

        PainlessCast leftCast = AnalyzerCaster.getLegalCast(leftNode.getLocation(),
                leftValueType, leftInput.expected, leftInput.explicit, leftInput.internal);
        PainlessCast rightCast = AnalyzerCaster.getLegalCast(rightNode.getLocation(),
                rightValueType, rightInput.expected, rightInput.explicit, rightInput.internal);

        semanticScope.addDecoration(this, new Decorator.ValueType(valueType));

        ConditionalNode conditionalNode = new ConditionalNode();

        conditionalNode.setLeftNode(cast(leftOutput.expressionNode, leftCast));
        conditionalNode.setRightNode(cast(rightOutput.expressionNode, rightCast));
        conditionalNode.setConditionNode(cast(conditionOutput.expressionNode, conditionCast));

        conditionalNode.setLocation(getLocation());
        conditionalNode.setExpressionType(valueType);

        output.expressionNode = conditionalNode;

        return output;
    }
}
