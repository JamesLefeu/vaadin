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

import org.w3c.css.sac.LexicalUnit;
import org.w3c.flute.parser.ParseException;

import com.vaadin.sass.internal.ScssStylesheet;
import com.vaadin.sass.internal.parser.LexicalUnitImpl;
import com.vaadin.sass.internal.parser.ReturnNodeException;
import com.vaadin.sass.internal.tree.BlockNode;
import com.vaadin.sass.internal.tree.CommentNode;
import com.vaadin.sass.internal.tree.ContentNode;
import com.vaadin.sass.internal.tree.ExtendNode;
import com.vaadin.sass.internal.tree.FontFaceNode;
import com.vaadin.sass.internal.tree.ForNode;
import com.vaadin.sass.internal.tree.FunctionDefNode;
import com.vaadin.sass.internal.tree.FunctionNode;
import com.vaadin.sass.internal.tree.ImportNode;
import com.vaadin.sass.internal.tree.KeyframeSelectorNode;
import com.vaadin.sass.internal.tree.KeyframesNode;
import com.vaadin.sass.internal.tree.ListAppendNode;
import com.vaadin.sass.internal.tree.ListContainsNode;
import com.vaadin.sass.internal.tree.ListModifyNode;
import com.vaadin.sass.internal.tree.ListRemoveNode;
import com.vaadin.sass.internal.tree.MediaNode;
import com.vaadin.sass.internal.tree.MicrosoftRuleNode;
import com.vaadin.sass.internal.tree.MixinDefNode;
import com.vaadin.sass.internal.tree.MixinNode;
import com.vaadin.sass.internal.tree.NestPropertiesNode;
import com.vaadin.sass.internal.tree.Node;
import com.vaadin.sass.internal.tree.ReturnNode;
import com.vaadin.sass.internal.tree.RuleNode;
import com.vaadin.sass.internal.tree.SimpleNode;
import com.vaadin.sass.internal.tree.VariableNode;
import com.vaadin.sass.internal.tree.WhileNode;
import com.vaadin.sass.internal.tree.controldirective.EachDefNode;
import com.vaadin.sass.internal.tree.controldirective.ElseNode;
import com.vaadin.sass.internal.tree.controldirective.IfElseDefNode;
import com.vaadin.sass.internal.tree.controldirective.IfElseNode;
import com.vaadin.sass.internal.tree.controldirective.IfNode;

/**
 * @version $Revision: 1.0 $
 * @author James Lefeu @ Liferay, Inc.
 */
public class FunctionNodeHandler extends MixinNodeHandler {

    public static void traverse(FunctionNode node) throws Exception {
        traverse(node, node.getName());
    }

    public static LexicalUnitImpl traverse(FunctionNode node, String name)
            throws Exception {

        FunctionDefNode functionDef = ScssStylesheet
                .getFunctionDefinition(name);
        if (functionDef == null) {
            throw new ParseException("Function Definition: " + name
                    + " not found");
        }

        return evaluateFunction(functionDef, node);
    }

    public static String evaluateFunction(String functionName,
            LexicalUnitImpl parameters) throws Exception {

        FunctionDefNode functionDef = ScssStylesheet
                .getFunctionDefinition(functionName);
        if (functionDef == null) {
            throw new ParseException("Function Definition: " + functionName
                    + " not found");
        }

        ArrayList<LexicalUnitImpl> params = new ArrayList<LexicalUnitImpl>();
        LexicalUnitImpl iter = parameters;
        LexicalUnitImpl item = null;
        LexicalUnitImpl prev = null;
        LexicalUnitImpl item2 = null;

        if (parameters == null) {
            // no parameters to add
        } else if (parameters.getNextLexicalUnit() == null) {
            params.add(parameters);
        } else {
            //
            // This should handle the cases of:
            // {"15" ","} ------------ 1 parameter
            // {"," ","} ------------- 0 parameters
            // {"1" "+" "3"} --------- 1 parameter
            // {"," "15"} ------------ 1 parameter
            // { } ------------------- 0 parameters
            // {"1" "," "2" "," "3"} - 3 parameters
            // {","} ----------------- 0 parameters
            //
            while (iter != null) {
                if ((iter.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_COMMA)
                        || (iter.getNextLexicalUnit() == null)) {
                    // add the entry, and create cut to a new parameter
                    item = iter;
                    if (iter.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_COMMA) {
                        item = item.getPreviousLexicalUnit();
                    }

                    // Here, we add the item we just found.
                    if ((item != null)
                            && (item.getLexicalUnitType() != LexicalUnit.SAC_OPERATOR_COMMA)) {
                        item = item.clone();
                        item2 = item;
                        while (item2.getPreviousLexicalUnit() != null) {
                            prev = item2.getPreviousLexicalUnit().clone();
                            if (prev.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_COMMA) {
                                item2.setPrevLexicalUnit(null);
                            } else {
                                item2.setPrevLexicalUnit(prev);
                            }

                            if (item2.getPreviousLexicalUnit() != null) {
                                item2 = item2.getPreviousLexicalUnit();
                            }
                        }
                        item.setNextLexicalUnit(null);
                        params.add(item);
                    }

                }
                iter = iter.getNextLexicalUnit();
            }
        }

        FunctionNode functionNode = new FunctionNode(functionName, params);

        LexicalUnitImpl result = evaluateFunction(functionDef, functionNode);

        return result.toString();
    }

    private static LexicalUnitImpl evaluateFunction(
            FunctionDefNode functionDef, FunctionNode node) throws Exception {

        LexicalUnitImpl retval = null;

        MixinDefNode replacedNode = replaceMixinNode(node, functionDef);

        try {
            retval = evaluateFunctionElements(replacedNode);
        } catch (ReturnNodeException e) {
            retval = e.getReturnValue();
        }

        return retval;
    }

    private static LexicalUnitImpl evaluateFunctionElements(Node node)
            throws Exception {
        LexicalUnitImpl retVal = null;
        String alreadyRemoved = " should have already been removed during traversal.";
        String notAllowed = " is not allowed within a function.";

        ArrayList<Node> children = node.getChildren();

        for (int i = 0; i < children.size(); i++) {
            LexicalUnitImpl temp = null;

            String fontFace = null;
            String microsoftVariable = null;
            String name = null;

            Node child = children.get(i);

            if (child instanceof ReturnNode) {
                // process the final value
                retVal = ReturnNodeHandler
                        .evaluateExpression((ReturnNode) child);

                // return immediately as an exception
                throw new ReturnNodeException(retVal);
            } else if (child instanceof CommentNode) {
                // ignore and move on
            } else if (child instanceof FunctionNode) {
                // evaluate function
                temp = traverse((FunctionNode) child,
                        ((FunctionNode) child).getName());
            } else if (child instanceof FontFaceNode) {
                // grab String value - maybe we can use it for something?
                fontFace = child.toString();
            } else if (child instanceof MicrosoftRuleNode) {
                // grab String variable value of the format:
                // name + ": " + value + ";"
                microsoftVariable = child.toString();
            } else if (child instanceof SimpleNode) {
                // 'throw an error' or 'ignore and move on' ?

            } else if (child instanceof RuleNode) {
                // grab the variable value and name
                temp = ((RuleNode) child).getValue();
                name = ((RuleNode) child).getVariable();
            } else if (child instanceof VariableNode) {
                // grab the variable value and name
                if (!((VariableNode) child).isGuarded()) {
                    temp = ((VariableNode) child).getExpr();
                    name = ((VariableNode) child).getName();
                }
            } else if (child instanceof BlockNode) {
                // this should have been removed during traversal
                throw new ParseException("BlockNode: " + child.toString()
                        + alreadyRemoved);
            } else if (child instanceof ContentNode) {
                // this should have been removed during traversal
                throw new ParseException("ContentNode: " + child.toString()
                        + alreadyRemoved);
            } else if (child instanceof ExtendNode) {
                // this should have been removed during traversal
                throw new ParseException("ExtendNode: " + child.toString()
                        + alreadyRemoved);
            } else if (child instanceof FunctionDefNode) {
                // this should have been removed during traversal
                throw new ParseException("FunctionDefNode: "
                        + ((FunctionDefNode) child).getName() + alreadyRemoved);
            } else if (child instanceof ImportNode) {
                // this should have been removed during traversal
                throw new ParseException("ImportNode: "
                        + ((ImportNode) child).getUri() + alreadyRemoved);
            } else if (child instanceof KeyframeSelectorNode) {
                // this is not allowed in a function
                throw new ParseException("KeyFrameSelectorNode: " + notAllowed);
            } else if (child instanceof KeyframesNode) {
                // this is not allowed in a function
                throw new ParseException("KeyframesNode: " + notAllowed);
            } else if (child instanceof ListAppendNode) {
                // this should have been removed during traversal
                throw new ParseException("ListAppendNode: "
                        + ((ListAppendNode) child).getNewVariable()
                        + alreadyRemoved);
            } else if (child instanceof ListContainsNode) {
                // this should have been removed during traversal
                throw new ParseException("ListContainsNode: "
                        + ((ListContainsNode) child).getNewVariable()
                        + alreadyRemoved);
            } else if (child instanceof ListRemoveNode) {
                // this should have been removed during traversal
                throw new ParseException("ListRemoveNode: "
                        + ((ListRemoveNode) child).getNewVariable()
                        + alreadyRemoved);
            } else if (child instanceof ListModifyNode) {
                // this should have been removed during traversal
                throw new ParseException("ListModifyNode: "
                        + ((ListModifyNode) child).getNewVariable()
                        + alreadyRemoved);
            } else if (child instanceof MediaNode) {
                // this is not allowed in a function
                throw new ParseException("MediaNode: " + notAllowed);
            } else if (child instanceof MixinDefNode) {
                // this should have been removed during traversal
                throw new ParseException("MixinDefNode: "
                        + ((MixinDefNode) child).getName() + alreadyRemoved);
            } else if (child instanceof MixinNode) {
                // this should have been removed during traversal
                // FunctionNode, a subclass of MixinNode is handled above
                throw new ParseException("MixinNode: "
                        + ((MixinNode) child).getName() + alreadyRemoved);
            } else if (child instanceof NestPropertiesNode) {
                // this should have been removed during traversal
                throw new ParseException("NestedPropertiesNode: "
                        + ((NestPropertiesNode) child).getName()
                        + alreadyRemoved);
            } else if (child instanceof EachDefNode) {
                // this should have been removed during traversal
                throw new ParseException("EachDefNode: "
                        + ((EachDefNode) child).getVariableName()
                        + alreadyRemoved);
            } else if (child instanceof ElseNode) {
                // this should have been removed during traversal
                throw new ParseException("ElseNode: " + alreadyRemoved);
            } else if (child instanceof ForNode) {
                // this should have been removed during traversal
                throw new ParseException("ForNode: "
                        + ((ForNode) child).toString() + alreadyRemoved);
            } else if (child instanceof IfElseDefNode) {
                // this should have been removed during traversal
                throw new ParseException("IfElseDefNode: " + alreadyRemoved);
            } else if (child instanceof IfNode) {
                // this should have been removed during traversal
                throw new ParseException("IfNode: "
                        + ((IfNode) child).getExpression() + alreadyRemoved);
            } else if (child instanceof IfElseNode) {
                // this should have been removed during traversal
                throw new ParseException("IfElseNode: " + alreadyRemoved);
            } else if (child instanceof WhileNode) {
                // this should have been removed during traversal
                throw new ParseException("WhileNode: "
                        + ((WhileNode) child).toString() + alreadyRemoved);
            } else {
                // this is not allowed in a function
                throw new ParseException("Node: unknown type" + notAllowed);
            }

        }

        // a function without a return statement returns void
        return null;
    }
}
