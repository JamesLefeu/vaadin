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

import org.w3c.css.sac.LexicalUnit;
import org.w3c.flute.parser.ParseException;

import com.vaadin.sass.internal.parser.LexicalUnitImpl;
import com.vaadin.sass.internal.tree.Node;
import com.vaadin.sass.internal.tree.controldirective.ForNode;

/**
 * @version $Revision: 1.0 $
 * @author James Lefeu @ Liferay, Inc.
 */
public class ForNodeHandler extends ControlDirectiveNodeHandler {

    public static void traverse(ForNode node) {
        replaceForNode(node);
    }

    private static void replaceForNode(ForNode forNode) {
        Node last = forNode;

        // Sass-lang.com Implementation:
        // * cannot count down
        // * can only iterate by 1
        LexicalUnitImpl from = forNode.getFrom();
        LexicalUnitImpl to = forNode.getTo();

        if (from.getLexicalUnitType() != LexicalUnit.SAC_INTEGER) {

            throw new ParseException("Invalid @for in scss file, "
                    + "'from' is not an integer expression : " + from);
        }

        if (to.getLexicalUnitType() != LexicalUnit.SAC_INTEGER) {

            throw new ParseException("Invalid @for in scss file, "
                    + "'to' is not an integer expression : " + to);
        } else if (from.getIntegerValue() > to.getIntegerValue()) {

            throw new ParseException("Invalid @for in scss file, " + "'from' ("
                    + from.getIntegerValue() + ") cannot be larger than 'to' ("
                    + to.getIntegerValue() + ")");
        }

        int i = from.getIntegerValue();
        int j = (forNode.getInclusive()) ? 1 : 0;
        j += to.getIntegerValue();

        for (int var = i; var < j; var++) {

            last = loop(forNode.getVariableName(), new LexicalUnitImpl(0, 0,
                    null, var), last, forNode);
        }
        forNode.setChildren(new ArrayList<Node>());
        forNode.getParentNode().removeChild(forNode);
    }
}
