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
import java.util.Map;

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
import com.vaadin.sass.internal.tree.IVariableNode;
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
import com.vaadin.sass.internal.util.DeepCopy;

/**
 * @version $Revision: 1.0 $
 * @author James Lefeu @ Liferay, Inc.
 */
public class FunctionNodeHandler {

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

        return evaluateCustomFunction(functionDef, node);
    }

    public static void evaluateFunctions(LexicalUnitImpl value) {
        if (containsFunction(value)) {
            // evaluate the function and replace the appropriate LexicalUnits
            // within value.
            LexicalUnitImpl iter = value;
            LexicalUnitImpl result = null;
            while (iter != null) {
                if (iter.getLexicalUnitType() == LexicalUnit.SAC_FUNCTION) {
                    try {
                        result = evaluateFunction(iter.getFunctionName(),
                                iter.getParameters());
                        if (result != null) {
                            result.setPrevLexicalUnit(iter
                                    .getPreviousLexicalUnit());
                            if (iter.getPreviousLexicalUnit() != null) {
                                iter.getPreviousLexicalUnit()
                                        .setNextLexicalUnit(result);
                            }

                            if (iter.getNextLexicalUnit() != null) {
                                result.setNextLexicalUnit(iter
                                        .getNextLexicalUnit());
                                if (iter.getNextLexicalUnit() != null) {
                                    iter.getNextLexicalUnit()
                                            .setPrevLexicalUnit(result);
                                }
                            }

                            // caching the value
                            iter.setFunctionEvaluated(true);
                            iter.setFunctionResult(result);

                        }
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                iter = iter.getNextLexicalUnit();
            }
        }
    }

    public static String evaluateFunctionToString(String functionName,
            LexicalUnitImpl parameters) throws Exception {
        LexicalUnitImpl result = evaluateFunction(functionName, parameters);

        return (result == null) ? "" : result.toString();
    }

    public static LexicalUnitImpl evaluateFunction(String functionName,
            LexicalUnitImpl parameters) throws Exception {

        LexicalUnitImpl result = null;
        LexicalUnitImpl builtInFunction = LexicalUnitImpl.createFunction(0, 0,
                null, functionName, parameters);
        result = LexicalUnitImpl.evaluateBuiltInFunction(builtInFunction);
        if (result == null) {
            result = evaluateCustomFunction(functionName, parameters);
        }

        return result;
    }

    public static LexicalUnitImpl evaluateCustomFunction(String functionName,
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

        LexicalUnitImpl result = evaluateCustomFunction(functionDef,
                functionNode);

        return result;
    }

    private static LexicalUnitImpl evaluateCustomFunction(
            FunctionDefNode functionDef, FunctionNode node) throws Exception {

        LexicalUnitImpl retval = null;

        FunctionDefNode defClone = (FunctionDefNode) DeepCopy.copy(functionDef);
        Map<String, VariableNode> variableScope = ScssStylesheet
                .openVariableScope();

        try {
            retval = evaluateFunctionElements(defClone, node);
        } catch (ReturnNodeException e) {
            retval = e.getReturnValue();
        }
        ScssStylesheet.closeVariableScope(variableScope);

        return retval;
    }

    private static LexicalUnitImpl evaluateFunctionElements(
            FunctionDefNode node, FunctionNode functionNode) throws Exception {

        if (node == null) {
            throw new ParseException(
                    "Null Parameter passed in for FunctionDefNode.");
        }

        if (functionNode == null) {
            throw new ParseException(
                    "Null Parameter passed in for FunctionNode.");
        }

        LexicalUnitImpl retVal = null;
        boolean hasArguments = true;

        String functionName = functionNode.getName();
        ArrayList<LexicalUnitImpl> argValues = functionNode.getArglist();

        String defNodeName = node.getName();
        ArrayList<VariableNode> argNames = node.getArglist();

        if ((argValues != null) && (argNames != null)) {
            if (argValues.size() != argNames.size()) {
                throw new ParseException("Parameters passed into function: "
                        + functionName + "( " + argValues.toString()
                        + " ) do not match the function definition: "
                        + defNodeName + "( " + argNames.toString() + " )");
            }
        } else if ((argValues == null) && (argNames != null)) {
            throw new ParseException("Parameters passed into function: "
                    + functionName
                    + "() do not match the function definition: " + defNodeName
                    + "( " + argNames.toString() + " )");
        } else if ((argValues != null) && (argNames == null)) {
            throw new ParseException("Parameters passed into function: "
                    + functionName + "( " + argValues.toString()
                    + " ) do not match the function definition: " + defNodeName
                    + "()");
        } else if ((functionName == null) || (defNodeName == null)
                || (!functionName.equals(defNodeName))) {
            throw new ParseException("Passed in function name: " + functionName
                    + "() does not match the function definition: "
                    + defNodeName + "()");
        } else if ((argValues == null) && (argNames == null)) {
            hasArguments = false;
        }

        // Add the values for the args to the SCSS Variables list.

        ArrayList<VariableNode> variables = new ArrayList<VariableNode>(
                ScssStylesheet.getVariables());

        if (hasArguments) {
            for (int i = 0; i < argValues.size(); i++) {
                variables = addVariable(argNames.get(i).getName(),
                        argValues.get(i), variables);
            }
        }

        // Then, as you process each child, populate the variables in the node.

        String notAllowed = " is not allowed within a function.";

        ArrayList<Node> children = node.getChildren();
        if (children != null) {

            for (int i = 0; i < children.size(); i++) {

                Node child = children.get(i);

                replaceInterpolation(child, variables);

                /*
                 * if (child instanceof RuleNode) { // grab the variable value
                 * and name variables = addVariable(((RuleNode)
                 * child).getVariable(), ((RuleNode) child).getValue());
                 * 
                 * } else if (child instanceof VariableNode) { // grab the
                 * variable value and name if (!((VariableNode)
                 * child).isGuarded()) { variables = addVariable(
                 * ((VariableNode) child).getName(), ((VariableNode)
                 * child).getExpr()); } } else
                 */
                if ((child instanceof CommentNode)
                        || (child instanceof SimpleNode)
                        || (child instanceof BlockNode)) {
                    // ignore and move on
                } else if ((child instanceof ContentNode)
                        || (child instanceof ExtendNode)
                        || (child instanceof ListAppendNode)
                        || (child instanceof ListContainsNode)
                        || (child instanceof ListRemoveNode)
                        || (child instanceof ListModifyNode)
                        || (child instanceof MixinNode)
                        || (child instanceof NestPropertiesNode)
                        || (child instanceof EachDefNode)
                        || (child instanceof ElseNode)
                        || (child instanceof ForNode)
                        || (child instanceof IfElseDefNode)
                        || (child instanceof IfNode)
                        || (child instanceof IfElseNode)
                        || (child instanceof WhileNode)
                        || (child instanceof FunctionNode)
                        || (child instanceof ReturnNode)
                        || (child instanceof VariableNode)
                        || (child instanceof RuleNode)) {
                    // traverse through this node
                    child.traverse();
                } else if ((child instanceof KeyframeSelectorNode)
                        || (child instanceof KeyframesNode)
                        || (child instanceof FontFaceNode)
                        || (child instanceof MicrosoftRuleNode)
                        || (child instanceof MediaNode)
                        || (child instanceof FunctionDefNode)
                        || (child instanceof MixinDefNode)
                        || (child instanceof ImportNode)) {
                    // this is not allowed in a function
                    throw new ParseException(child.getClass().getName() + ": "
                            + notAllowed);
                } else {
                    // this is not allowed in a function
                    throw new ParseException("Node: unknown type" + notAllowed);
                }
            }
        }

        // a function without a return statement returns void
        return null;
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

    private static ArrayList<VariableNode> addVariable(String name,
            LexicalUnitImpl value, ArrayList<VariableNode> variables) {

        VariableNode varNode = new VariableNode(name, value, false);

        replaceInterpolation(varNode, variables);
        variables.add(0, varNode);

        return variables;
    }

    public static boolean containsFunction(LexicalUnitImpl value) {
        boolean retVal = false;

        while ((value != null) && (retVal != true)) {
            if (value.getLexicalUnitType() == LexicalUnit.SAC_FUNCTION) {
                retVal = true;
            }
            value = value.getNextLexicalUnit();
        }

        return retVal;
    }
}
