/*******************************************************************************
 * Copyright (c) 2008 SpringSource and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *      SpringSource
 *      Andrew Eisenberg (initial implementation)
 *******************************************************************************/
package scala.tools.eclipse.contribution.weaving.jdt.imagedescriptor;

/*******************************************************************************
 * Added to the Scala plugin to fix interoperability issues between the 
 * Spring IDE and the Scala IDE. This plugin now implements the cuprovider and
 * imagedescriptorselector extension points, previously provided by the 
 * JDT weaving plugin.
 * 
 * Repo: git://git.eclipse.org/gitroot/ajdt/org.eclipse.ajdt.git
 * File: src/org.eclipse.contribution.weaving.jdt/src/org/eclipse/contribution/jdt/imagedescriptor/ImageDescriptorSelectorRegistry.java
 * 
 *******************************************************************************/

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

import scala.tools.eclipse.contribution.weaving.jdt.ScalaJDTWeavingPlugin;

public class ImageDescriptorSelectorRegistry implements Iterable<IImageDescriptorSelector> {
    private static final ImageDescriptorSelectorRegistry INSTANCE = 
        new ImageDescriptorSelectorRegistry();
    private static final String SELECTORS_EXTENSION_POINT = "org.scala-ide.sdt.aspects.imagedescriptorselector"; //$NON-NLS-1$
    
    public static ImageDescriptorSelectorRegistry getInstance() {
        return INSTANCE;
    }

    private ImageDescriptorSelectorRegistry() { 
        // do nothing
    }
    
    private Set<IImageDescriptorSelector> registry;
    
    void registerSelector(IImageDescriptorSelector provider) {
        registry.add(provider);
    }
    
    public boolean isRegistered() {
        return registry != null;
    }
    
    public void registerSelectors() {
        registry = new HashSet<IImageDescriptorSelector>();
        IExtensionPoint exP =
            Platform.getExtensionRegistry().getExtensionPoint(SELECTORS_EXTENSION_POINT);
        if (exP != null) {
            IExtension[] exs = exP.getExtensions();
            if (exs != null) {
                for (int i = 0; i < exs.length; i++) {
                    IConfigurationElement[] configs = exs[i].getConfigurationElements();
                    for (int j = 0; j < configs.length; j++) {
                        try {
                            IConfigurationElement config = configs[j];
                            if (config.isValid()) {
                                IImageDescriptorSelector provider = (IImageDescriptorSelector) 
                                        config.createExecutableExtension("class"); //$NON-NLS-1$
                                registry.add(provider);
                            }
                        } catch (CoreException e) {
                            ScalaJDTWeavingPlugin.logException(e);
                        } 
                    }
                }
            }
        }
    }

    public Iterator<IImageDescriptorSelector> iterator() {
        if (!isRegistered()) {
            registerSelectors();
        }
        return registry.iterator();
    }
}
