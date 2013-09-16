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

import com.vaadin.sass.internal.ScssStylesheet;
import com.vaadin.sass.internal.expression.ArithmeticExpressionEvaluator;
import com.vaadin.sass.internal.parser.LexicalUnitImpl;

/**
 * @version $Revision: 1.0 $
 * @author James Lefeu @ Liferay, Inc.
 */
public class ReturnNode extends VariableNode implements IVariableNode {
    private static final long serialVersionUID = 3301805078983796870L;

    public ReturnNode(LexicalUnitImpl value) {
        super("", value, false);
    }

    @Override
    public void traverse() {
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
    }
}
