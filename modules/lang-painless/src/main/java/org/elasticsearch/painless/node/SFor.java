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
import org.elasticsearch.painless.ir.BlockNode;
import org.elasticsearch.painless.ir.ClassNode;
import org.elasticsearch.painless.ir.ForLoopNode;
import org.elasticsearch.painless.lookup.PainlessCast;
import org.elasticsearch.painless.symbol.Decorations.AllEscape;
import org.elasticsearch.painless.symbol.Decorations.AnyBreak;
import org.elasticsearch.painless.symbol.Decorations.AnyContinue;
import org.elasticsearch.painless.symbol.Decorations.BeginLoop;
import org.elasticsearch.painless.symbol.Decorations.InLoop;
import org.elasticsearch.painless.symbol.Decorations.LoopEscape;
import org.elasticsearch.painless.symbol.Decorations.MethodEscape;
import org.elasticsearch.painless.symbol.Decorations.Read;
import org.elasticsearch.painless.symbol.Decorations.TargetType;
import org.elasticsearch.painless.symbol.SemanticScope;

/**
 * Represents a for loop.
 */
public class SFor extends AStatement {

    private final ANode initializerNode;
    private final AExpression conditionNode;
    private final AExpression afterthoughtNode;
    private final SBlock blockNode;

    public SFor(int identifier, Location location,
            ANode initializerNode, AExpression conditionNode, AExpression afterthoughtNode, SBlock blockNode) {

        super(identifier, location);

        this.initializerNode = initializerNode;
        this.conditionNode = conditionNode;
        this.afterthoughtNode = afterthoughtNode;
        this.blockNode = blockNode;
    }

    public ANode getInitializerNode() {
        return initializerNode;
    }

    public AExpression getConditionNode() {
        return conditionNode;
    }

    public AExpression getAfterthoughtNode() {
        return afterthoughtNode;
    }

    public SBlock getBlockNode() {
        return blockNode;
    }

    @Override
    Output analyze(ClassNode classNode, SemanticScope semanticScope) {
        semanticScope = semanticScope.newLocalScope();

        Output initializerStatementOutput = null;
        AExpression.Output initializerExpressionOutput = null;

        if (initializerNode != null) {
            if (initializerNode instanceof SDeclBlock) {
                initializerStatementOutput = ((SDeclBlock)initializerNode).analyze(classNode, semanticScope);
            } else if (initializerNode instanceof AExpression) {
                AExpression initializer = (AExpression)this.initializerNode;
                initializerExpressionOutput = AExpression.analyze(initializer, classNode, semanticScope);
            } else {
                throw createError(new IllegalStateException("Illegal tree structure."));
            }
        }

        boolean continuous = false;

        AExpression.Output conditionOutput = null;
        PainlessCast conditionCast = null;

        if (conditionNode != null) {
            semanticScope.setCondition(conditionNode, Read.class);
            semanticScope.putDecoration(conditionNode, new TargetType(boolean.class));
            conditionOutput = AExpression.analyze(conditionNode, classNode, semanticScope);
            conditionCast = conditionNode.cast(semanticScope);

            if (conditionNode instanceof EBoolean) {
                continuous = ((EBoolean)conditionNode).getBool();

                if (!continuous) {
                    throw createError(new IllegalArgumentException("Extraneous for loop."));
                }

                if (blockNode == null) {
                    throw createError(new IllegalArgumentException("For loop has no escape."));
                }
            }
        } else {
            continuous = true;
        }

        AExpression.Output afterthoughtOutput = null;

        if (afterthoughtNode != null) {
            afterthoughtOutput = AExpression.analyze(afterthoughtNode, classNode, semanticScope);
        }

        Output output = new Output();
        Output blockOutput = null;

        if (blockNode != null) {
            semanticScope.setCondition(blockNode, BeginLoop.class);
            semanticScope.setCondition(blockNode, InLoop.class);
            blockOutput = blockNode.analyze(classNode, semanticScope);

            if (semanticScope.getCondition(blockNode, LoopEscape.class) &&
                    semanticScope.getCondition(blockNode, AnyContinue.class) == false) {
                throw createError(new IllegalArgumentException("Extraneous for loop."));
            }

            if (continuous && semanticScope.getCondition(blockNode, AnyBreak.class) == false) {
                semanticScope.setCondition(this, MethodEscape.class);
                semanticScope.setCondition(this, AllEscape.class);
            }
        }

        ForLoopNode forLoopNode = new ForLoopNode();
        forLoopNode.setInitialzerNode(initializerNode == null ? null : initializerNode instanceof AExpression ?
                initializerExpressionOutput.expressionNode : initializerStatementOutput.statementNode);
        forLoopNode.setConditionNode(conditionOutput == null ?
                null : AExpression.cast(conditionOutput.expressionNode, conditionCast));
        forLoopNode.setAfterthoughtNode(afterthoughtOutput == null ? null : afterthoughtOutput.expressionNode);
        forLoopNode.setBlockNode(blockOutput == null ? null : (BlockNode)blockOutput.statementNode);
        forLoopNode.setLocation(getLocation());
        forLoopNode.setContinuous(continuous);

        output.statementNode = forLoopNode;

        return output;
    }
}
