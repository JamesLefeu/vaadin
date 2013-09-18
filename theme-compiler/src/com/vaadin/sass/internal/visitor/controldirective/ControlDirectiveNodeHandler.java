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
package com.vaadin.sass.internal.visitor.controldirective;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.JexlException;
import org.w3c.flute.parser.ParseException;

import com.vaadin.sass.internal.ScssStylesheet;
import com.vaadin.sass.internal.parser.LexicalUnitImpl;
import com.vaadin.sass.internal.tree.IVariableNode;
import com.vaadin.sass.internal.tree.Node;
import com.vaadin.sass.internal.tree.VariableNode;
import com.vaadin.sass.internal.util.DeepCopy;

public class ControlDirectiveNodeHandler {

    protected static final JexlEngine evaluator = new JexlEngine();
    protected static final Pattern pattern = Pattern
            .compile("[a-zA-Z0-9]*[a-zA-Z]+[a-zA-Z0-9]*");

    protected static boolean evaluateExpression(String expression) {
        try {
            // We need to add ' ' for strings in the expression for
            // jexl to understand that is should do a string
            // comparison
            expression = replaceStrings(expression);
            Expression e = evaluator.createExpression(expression);
            try {
                Object eval = e.evaluate(null);

                Boolean result = false;
                if (eval instanceof Boolean) {
                    result = (Boolean) eval;
                } else if (eval instanceof String) {
                    result = Boolean.valueOf((String) eval);
                }

                return result;
            } catch (ClassCastException ex) {
                throw new ParseException(
                        "Invalid Control Directive in scss file, not a boolean expression : "
                                + expression);
            } catch (NullPointerException ex) {
                throw new ParseException(
                        "Invalid Control Directive in scss file, not a boolean expression : "
                                + expression);
            }
        } catch (JexlException e) {
            throw new ParseException(
                    "Invalid Control Directive in scss file for: " + expression);
        }
    }

    protected static Node loop(String name, LexicalUnitImpl value, Node last,
            Node controlItem) {
        VariableNode varNode = new VariableNode(name.substring(1), value, false);

        ArrayList<VariableNode> variables = new ArrayList<VariableNode>(
                ScssStylesheet.getVariables());
        variables.add(0, varNode);

        for (final Node child : controlItem.getChildren()) {

            Node copy = (Node) DeepCopy.copy(child);

            replaceInterpolation(copy, variables);

            controlItem.getParentNode().appendChild(copy, last);
            last = copy;
        }

        return last;
    }

    protected static void replaceInterpolation(Node copy,
            ArrayList<VariableNode> variables) {
        if (copy instanceof IVariableNode) {
            IVariableNode n = (IVariableNode) copy;
            n.replaceVariables(variables);
        }

        for (Node c : copy.getChildren()) {
            replaceInterpolation(c, variables);
        }
    }

    protected static String replaceStrings(String expression) {
        expression = expression.replaceAll("\"", "");
        Matcher m = pattern.matcher(expression);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String group = m.group();
            m.appendReplacement(b, "'" + group + "'");
        }
        m.appendTail(b);
        if (b.length() != 0) {
            return b.toString();
        }
        return expression;
    }

}
