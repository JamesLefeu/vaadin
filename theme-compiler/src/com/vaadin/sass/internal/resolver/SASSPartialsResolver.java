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
package com.vaadin.sass.internal.resolver;

import java.io.File;

import org.w3c.css.sac.InputSource;

public class SASSPartialsResolver implements ScssStylesheetResolver {

    private static ScssStylesheetResolver fileSystemResolver;

    public static final String URI_FILE_SCHEME = "file:";

    public SASSPartialsResolver() {
        if (fileSystemResolver == null) {
            fileSystemResolver = new FilesystemResolver();
        }
    }

    /**
     * This will remove the file URI scheme, if any, which is prepended to the
     * identifier.
     * 
     * The intention here is to handle the case of malformed input where "file:"
     * exists twice in the string. Thus, using "lastindexof()" would trim it.
     * 
     * An alternate possible approach is that one could simply say that if
     * "file:" exists more than once in the string to reject the entire string
     * and simply "return identifier". Or, we could simply not handle the error
     * case of malformed input and just use "indexof()".
     * 
     * But currently, it is coded to handle at least one possible type of
     * malformed input.
     * 
     * @param identifier
     *            This is the file path to check.
     * @return The file path without a URI scheme.
     */
    public static String removeURIScheme(String identifier) {
        if ((identifier != null) && (identifier.startsWith(URI_FILE_SCHEME))) {
            return identifier.substring(identifier.lastIndexOf(URI_FILE_SCHEME)
                    + URI_FILE_SCHEME.length());
        }
        return identifier;
    }

    /**
     * The underscore handling is intended to match the semantics of SASS
     * partials:
     * http://sass-lang.com/docs/yardoc/file.SASS_REFERENCE.html#partials
     * 
     * Because this compiler implementation will only compile the explicitly
     * specified SCSS file to CSS, we only need to be aware of the underscore in
     * the filename and the requirement that the same name cannot exist in the
     * same path of its name with the underscore.
     * 
     * @param identifier
     *            The path to resolve.
     * @return The input source which was found (or null).
     */
    @Override
    public InputSource resolve(String identifier) {

        InputSource source = null;
        String filePath = removeURIScheme(identifier);

        source = fileSystemResolver.resolve(filePath);

        if (source == null) {
            source = fileSystemResolver.resolve(getPartialsPath(filePath));
        }

        return source;
    }

    /**
     * This method will convert a normal path to a partials path. In particular,
     * it is useful for when an import statement has a normal path and what is
     * accessible is a partials path.
     * 
     * E.g.: @import compass; //compass.scss doesn't exist on the path, but
     * _compass.scss does.
     * 
     * @param identifier
     *            The path to resolve.
     * @return The corresponding partials path.
     */
    private String getPartialsPath(String identifier) {

        StringBuilder sb = new StringBuilder();

        if (identifier.contains(String.valueOf(File.separatorChar))) {
            int index = identifier.lastIndexOf(File.separatorChar);
            sb.append(identifier.substring(0, index + 1));
            sb.append(new String("_"));
            sb.append(identifier.substring((index + 1), identifier.length()));
        } else {
            sb.append(new String("_"));
            sb.append(identifier);
        }
        if (!sb.toString().endsWith(".scss")) {
            sb.append(".scss");
        }

        return sb.toString();
    }

}
