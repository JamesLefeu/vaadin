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

import com.vaadin.sass.internal.parser.LexicalUnitImpl;
import com.vaadin.sass.internal.tree.Node;
import com.vaadin.sass.internal.tree.controldirective.EachDefNode;

public class EachNodeHandler extends ControlDirectiveNodeHandler {

    public static void traverse(EachDefNode node) {
        replaceEachDefNode(node);
    }

    private static void replaceEachDefNode(EachDefNode defNode) {
        Node last = defNode;

        for (final String var : defNode.getVariables()) {

            last = loop(defNode.getVariableName(),
                    LexicalUnitImpl.createIdent(var), last, defNode);
        }
        defNode.setChildren(new ArrayList<Node>());
        defNode.getParentNode().removeChild(defNode);
    }

}
