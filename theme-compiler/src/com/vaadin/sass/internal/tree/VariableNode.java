/*
 * Copyright 2000-2013 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.sass.internal.tree;

import java.util.ArrayList;

import org.w3c.css.sac.LexicalUnit;

import com.vaadin.sass.internal.ScssStylesheet;
import com.vaadin.sass.internal.expression.ArithmeticExpressionEvaluator;
import com.vaadin.sass.internal.parser.LexicalUnitImpl;
import com.vaadin.sass.internal.parser.ReturnNodeException;
import com.vaadin.sass.internal.visitor.VariableNodeHandler;

public class VariableNode extends Node implements IVariableNode {
    private static final long serialVersionUID = 7003372557547748734L;

    private String name;
    protected LexicalUnitImpl expr;
    private boolean guarded;

    public VariableNode(String name, LexicalUnitImpl expr, boolean guarded) {
        super();
        this.name = name;
        this.expr = expr;
        this.guarded = guarded;
    }

    public LexicalUnitImpl getExpr() {
        return expr;
    }

    public void setExpr(LexicalUnitImpl expr) {
        this.expr = expr;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isGuarded() {
        return guarded;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("$");
        builder.append(name).append(": ").append(expr);
        return builder.toString();
    }

    public void setGuarded(boolean guarded) {
        this.guarded = guarded;
    }

    @Override
    public void replaceVariables(ArrayList<VariableNode> variables) {
        for (final VariableNode node : variables) {
            if (!equals(node)) {

                // We don't want to call "toString()" on a function until it's
                // evaluated. Also, we need to iterate through the chain of
                // "next" parameters in certain cases even when there isn't a
                // function. So, it seems perhaps best to simply iterate through
                // the entire list.
                LexicalUnitImpl iter = expr;
                while (iter != null) {
                    if (expr.getParameters() != null) {
                        replaceValues(iter.getParameters(), node);
                    } else if (iter.getLexicalUnitType() == LexicalUnitImpl.SCSS_VARIABLE) {
                        replaceValues(iter, node);
                    }
                    iter = iter.getNextLexicalUnit();
                }
            }
        }
    }

    private void replaceValues(LexicalUnitImpl unit, VariableNode node) {
        while (unit != null) {
            switch (unit.getLexicalUnitType()) {
            case LexicalUnitImpl.SCSS_VARIABLE:
                if (unit.getValue().toString().equals(node.getName())
                        || unit.getStringValue().equals(node.getName())) {
                    LexicalUnitImpl.replaceValues(unit, node.getExpr());
                }
                break;
            case LexicalUnit.SAC_FUNCTION:
                replaceValues(unit.getParameters(), node);
                break;
            default:
                break;
            }

            unit = unit.getNextLexicalUnit();
        }
    }

    @Override
    public void traverse() throws ReturnNodeException {
        /*
         * "replaceVariables(ScssStylesheet.getVariables());" seems duplicated
         * and can be extracted out of if, but it is not.
         * containsArithmeticalOperator must be called before replaceVariables.
         * Because for the "/" operator, it needs to see if its predecessor or
         * successor is a Variable or not, to determine it is an arithmetic
         * operator.
         */
        if (ArithmeticExpressionEvaluator.get().containsArithmeticalOperator(
                expr)) {
            replaceVariables(ScssStylesheet.getVariables());
            expr = ArithmeticExpressionEvaluator.get().evaluate(expr);
        } else {
            replaceVariables(ScssStylesheet.getVariables());
        }
        VariableNodeHandler.traverse(this);
    }
}
