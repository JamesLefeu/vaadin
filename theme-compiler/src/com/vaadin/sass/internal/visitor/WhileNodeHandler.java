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
package com.vaadin.sass.internal.visitor;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.JexlException;
import org.w3c.flute.parser.ParseException;

import com.vaadin.sass.internal.ScssStylesheet;
import com.vaadin.sass.internal.tree.Node;
import com.vaadin.sass.internal.tree.VariableNode;
import com.vaadin.sass.internal.tree.controldirective.WhileDefNode;
import com.vaadin.sass.internal.tree.controldirective.WhileNode;
import com.vaadin.sass.internal.util.DeepCopy;

/**
 * @version $Revision: 1.0 $
 * @author James Lefeu @ Liferay, Inc.
 */
public class WhileNodeHandler extends EachNodeHandler {

    private static final JexlEngine evaluator = new JexlEngine();
    private static final Pattern pattern = Pattern
            .compile("[a-zA-Z0-9]*[a-zA-Z]+[a-zA-Z0-9]*");

    private static final int EVAL_INDEX = 0;
    private static final int LASTNODE_INDEX = 1;

    public static void traverse(WhileDefNode node) throws Exception {

        Node last = node;

        ArrayList retVal = evaluateExpression(node, last);
        last = (Node) retVal.get(LASTNODE_INDEX);
        while (retVal.get(EVAL_INDEX).equals(Boolean.TRUE)) {
            retVal = evaluateExpression(node, last);
            last = (Node) retVal.get(LASTNODE_INDEX);
        }

        node.setChildren(new ArrayList<Node>());
        node.getParentNode().removeChild(node);
    }

    private static ArrayList evaluateExpression(WhileDefNode node, Node last) {
        Boolean result = false;
        ArrayList retVal = new ArrayList();
        for (final Node child : node.getChildren()) {
            result = false;
            if (child instanceof WhileNode) {
                try {
                    child.traverse();
                    String expression = ((WhileNode) child)
                            .getCurrentExpression();
                    // We need to add ' ' for strings in the expression for
                    // jexl to understand that it should do a string
                    // comparison
                    expression = replaceStrings(expression);
                    Expression e = evaluator.createExpression(expression);
                    try {
                        Object eval = e.evaluate(null);
                        if (eval instanceof Boolean) {
                            result = (Boolean) eval;
                        } else if (eval instanceof String) {
                            result = Boolean.valueOf((String) eval);
                        }

                        if (result) {
                            last = populateWithChildren(child, last, node);
                        }

                        retVal.add(result);
                        retVal.add(last);
                        return retVal;

                    } catch (ClassCastException ex) {
                        throw new ParseException(
                                "Invalid @while in scss file, not a boolean expression : "
                                        + child.toString());
                    } catch (NullPointerException ex) {
                        throw new ParseException(
                                "Invalid @while in scss file, not a boolean expression : "
                                        + child.toString());
                    }
                } catch (JexlException e) {
                    throw new ParseException("Invalid @while in scss file for "
                            + child.toString());
                }
            } else {
                throw new ParseException("Invalid @while in scss file for "
                        + node);
            }
        }
        retVal.add(result);
        retVal.add(last);
        return retVal;
    }

    private static String replaceStrings(String expression) {
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

    private static Node populateWithChildren(Node whileNode, Node last,
            WhileDefNode whileDefnode) {
        ArrayList<VariableNode> variables = new ArrayList<VariableNode>(
                ScssStylesheet.getVariables());

        ArrayList<Node> lastList = new ArrayList<Node>();
        lastList.add(whileDefnode);

        for (final Node child : whileNode.getChildren()) {

            Node copy = (Node) DeepCopy.copy(child);
            replaceInterpolation(copy, variables);
            whileDefnode.getParentNode().appendChild(copy, last);
            lastList.add(copy);
            copy.traverse();
        }

        int i = lastList.size() - 1;
        boolean foundIt = false;
        while ((i >= 0) && (foundIt == false)) {
            Node n = lastList.get(i);
            if (whileDefnode.getParentNode().equals(n.getParentNode())) {
                last = n;
                foundIt = true;
            }
            i--;
        }

        if (i < 0) {
            last = whileDefnode;
        }

        return last;
    }
}
