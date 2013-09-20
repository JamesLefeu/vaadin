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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.css.sac.InputSource;

public class CustomVaadinResolver extends VaadinResolver implements
        ScssStylesheetResolver {

    private List<String> importPaths;
    private static ScssStylesheetResolver sassResolver;
    private static ScssStylesheetResolver classLoaderResolver;

    /**
     * Create a resolver without any custom paths.
     * 
     */
    public CustomVaadinResolver() {
        init();
        setImportPaths(null);
    }

    /**
     * Create a resolver with one custom path.
     * 
     * @param path
     *            The customPath to add.
     */
    public CustomVaadinResolver(String path) {
        init();
        addImportPath(path);
    }

    /**
     * Create a resolver with multiple custom paths.
     * 
     * @param paths
     *            The customPaths to add.
     */
    public CustomVaadinResolver(List<String> paths) {
        init();
        setImportPaths(paths);
    }

    /**
     * Resolve/Find the identifier.
     * 
     * If we locate the identifier, we add the located path to our importPaths
     * since the located item may itself import items from its relative own
     * path.
     * 
     * E.g.: if /filepath/ is on our importPaths lists and we import
     * /filepath/compass.scss which itself calls "@import compass/utilities" and
     * the file is actually located at /filepath/compass/utilities.scss
     * 
     * We will indeed find utilities.scss, but if utilities.scss then calls
     * "@import utilities/color" and the file is actually located at
     * /filepath/compass/utilities/color.scss
     * 
     * We will need to add /filepath/compass/ to our importPaths to correctly
     * locate color.scss.
     * 
     * Now, one might pose the question: Are all the parents of imported paths
     * added or is the example missing an import or compass/xyz.scss? In
     * response, the intention is to show why there is a need to add the
     * children paths. We do not currently add the parent paths because it isn't
     * necessary for Compass to work.
     * 
     * We first check "file.scss" and then "_file.scss" in the same directory.
     * For handling "/file.scss", "/path1/file.scss", "/path2/file.scss"
     * ambiguity, perhaps the ideal approach would be to imitate how its done at
     * sass-lang.com which possibly is to just grab the first path we see.
     * 
     * Continuously adding like this assumes that we will not be loading
     * excessive numbers (thousands or millions or more) of unique *.scss
     * directories on a single resolver instance. If we wish to handle such a
     * case, we should add functionality to remove items from the importList
     * once we get out of the scope of the recently added item. We would also
     * need to distinguish between items to be removed upon exit of scope and
     * items which remain permanently. In the meantime, it seems perhaps
     * acceptable to assume this method will not undergo such load.
     * 
     * 
     * @param identifier
     *            Used to find the stylesheet.
     * @return InputSource for stylesheet (with URI set) or null if not found.
     */
    @Override
    public InputSource resolve(String identifier) {

        // Remove extra "." and ".."
        identifier = normalize(identifier);

        InputSource source = null;

        // Can we find the scss from the file system?

        source = sassResolver.resolve(identifier);

        if (source == null) {
            // How about our custom file paths?
            source = checkCustomPaths(identifier);
        }

        if (source != null) {
            // Found it! Now add the importPath.
            String filePath = SASSPartialsResolver.removeURIScheme(source
                    .getURI().toString());
            filePath = filePath.substring(0,
                    filePath.lastIndexOf(File.separatorChar));
            addImportPath(filePath);
        } else {
            // How about the classpath?
            source = classLoaderResolver.resolve(identifier);
        }

        return source;
    }

    /**
     * Add import path from this resolver. Since Strings are immutable, we can
     * simply add it.
     * 
     * An import path is an alternate file system path to check when trying to
     * find the location of an item to be resolved. In particular, this is used
     * when a filename is given as the item to be resolved. The import path
     * would then check for that filename at the specified file system path.
     * 
     * @param path
     *            The path to add.
     */
    public void addImportPath(String path) {
        if (path != null) {
            String filePath = SASSPartialsResolver.removeURIScheme(path);
            if (!importPaths.contains(filePath)) {
                importPaths.add(filePath);
            }
        }
    }

    /**
     * Get import paths from this resolver. But, don't give out references to
     * instances of our objects. The constructor will instantiate the
     * "importPaths" object. So, this value should never be null.
     * 
     * An import path is an alternate file system path to check when trying to
     * find the location of an item to be resolved. In particular, this is used
     * when a filename or relative path is given as the item to be resolved. The
     * import path would then check for that filename at the specified file
     * system path.
     * 
     * @return The list of paths.
     */
    public List<String> getImportPaths() {
        return Collections.unmodifiableList(importPaths);
    }

    /**
     * Remove an import path from this resolver.
     * 
     * An import path is an alternate file system path to check when trying to
     * find the location of an item to be resolved. In particular, this is used
     * when a filename or relative path is given as the item to be resolved. The
     * import path would then check for that filename at the specified file
     * system path.
     * 
     * @param path
     *            The path to remove.
     * @return <tt>true</tt> if this list contained the specified element
     */
    public boolean removeImportPath(String path) {
        if (importPaths.contains(path)) {
            return importPaths.remove(path);
        }
        return false;
    }

    /**
     * Set import paths to this resolver. But, protect against outside
     * references to instances of our objects.
     * 
     * An import path is an alternate file system path to check when trying to
     * find the location of an item to be resolved. In particular, this is used
     * when a filename or relative path is given as the item to be resolved. The
     * import path would then check for that filename at the specified file
     * system path.
     * 
     * @param paths
     *            The list of paths to set.
     */
    public void setImportPaths(List<String> paths) {
        if (paths == null) {
            importPaths = new ArrayList<String>();
        } else {
            importPaths = new ArrayList<String>(paths);
        }
    }

    /**
     * Check our array of custom paths to try to resolve/find the identifier.
     * 
     * @param identifier
     *            The item to find.
     * @return InputSource for stylesheet (with URI set) or null if not found.
     */
    private InputSource checkCustomPaths(String identifier) {
        InputSource source = null;
        String filename = stripPath(identifier);

        int i = 0;
        while ((i < importPaths.size()) && (source == null)) {
            String item = importPaths.get(i).concat(
                    String.valueOf((File.separatorChar)).concat(filename));

            source = sassResolver.resolve(item);
            i++;
        }

        return source;
    }

    /**
     * This will initialize class and object variables.
     * 
     */
    private void init() {
        if (sassResolver == null) {
            sassResolver = new SASSPartialsResolver();
        }
        if (classLoaderResolver == null) {
            classLoaderResolver = new ClassloaderResolver();
        }
        if (importPaths == null) {
            importPaths = new ArrayList<String>();
        }
    }

    /**
     * This will remove the path from an identifier.
     * 
     * @param identifier
     *            A filename with a path.
     * @return The filename without the path.
     */
    private String stripPath(String identifier) {
        String retVal = identifier;
        if ((identifier != null)
                && (identifier.contains(String.valueOf(File.separatorChar)))) {
            int index = identifier.lastIndexOf(File.separatorChar);
            retVal = identifier.substring(index + 1);
        }
        return retVal;
    }
}
