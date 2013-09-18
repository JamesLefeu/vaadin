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

import com.vaadin.sass.internal.ScssStylesheet;
import com.vaadin.sass.internal.tree.Node;
import com.vaadin.sass.internal.tree.VariableNode;
import com.vaadin.sass.internal.tree.controldirective.WhileNode;
import com.vaadin.sass.internal.util.DeepCopy;

/**
 * @version $Revision: 1.0 $
 * @author James Lefeu @ Liferay, Inc.
 */
public class WhileNodeHandler extends ControlDirectiveNodeHandler {

    private static final int EVAL_INDEX = 0;
    private static final int LASTNODE_INDEX = 1;

    public static void traverse(WhileNode node) throws Exception {

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

    private static ArrayList evaluateExpression(WhileNode node, Node last) {
        Boolean result = Boolean.FALSE;
        ArrayList retVal = new ArrayList();
        node.replaceVariables(ScssStylesheet.getVariables());
        String expression = node.getCurrentExpression();

        if (evaluateExpression(expression)) {
            result = Boolean.TRUE;
            last = populateWithChildren(node, last);
        }

        retVal.add(result);
        retVal.add(last);
        return retVal;

    }

    private static Node populateWithChildren(Node whileNode, Node last) {
        ArrayList<VariableNode> variables = new ArrayList<VariableNode>(
                ScssStylesheet.getVariables());

        ArrayList<Node> lastList = new ArrayList<Node>();
        lastList.add(whileNode);

        for (final Node child : whileNode.getChildren()) {

            Node copy = (Node) DeepCopy.copy(child);
            replaceInterpolation(copy, variables);
            whileNode.getParentNode().appendChild(copy, last);
            lastList.add(copy);
            copy.traverse();
        }

        int i = lastList.size() - 1;
        boolean foundIt = false;
        while ((i >= 0) && (foundIt == false)) {
            Node n = lastList.get(i);
            if (whileNode.getParentNode().equals(n.getParentNode())) {
                last = n;
                foundIt = true;
            }
            i--;
        }

        if (i < 0) {
            last = whileNode;
        }

        return last;
    }
}
