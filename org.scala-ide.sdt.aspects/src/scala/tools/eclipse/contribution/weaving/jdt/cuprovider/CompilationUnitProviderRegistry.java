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
package scala.tools.eclipse.contribution.weaving.jdt.cuprovider;

/*******************************************************************************
 * Added to the Scala plugin to fix interoperability issues between the 
 * Spring IDE and the Scala IDE. This plugin now implements the cuprovider and
 * imagedescriptorselector extension points, previously provided by the 
 * JDT weaving plugin.
 * 
 * Repo: git://git.eclipse.org/gitroot/ajdt/org.eclipse.ajdt.git
 * File: src/org.eclipse.contribution.weaving.jdt/src/org/eclipse/contribution/jdt/cuprovider/CompilationUnitProviderRegistry.java
 * 
 *******************************************************************************/


import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

import scala.tools.eclipse.contribution.weaving.jdt.ScalaJDTWeavingPlugin;

public class CompilationUnitProviderRegistry {
    public static String CUPROVIDERS_EXTENSION_POINT = "org.scala-ide.sdt.aspects.cuprovider"; //$NON-NLS-1$
    
    private static final CompilationUnitProviderRegistry INSTANCE = 
        new CompilationUnitProviderRegistry();
    
    public static CompilationUnitProviderRegistry getInstance() {
        return INSTANCE;
    }

    private CompilationUnitProviderRegistry() { 
        // do nothing
    }
    
    private Map<String, ICompilationUnitProvider> registry; 
    
    void registerCompilationUnitProvider(String key, ICompilationUnitProvider provider) {
        registry.put(key, provider);
    }
    
    ICompilationUnitProvider getProvider(String key) {
        if (!isRegistered()) {
            registerProviders();
        }
        return (ICompilationUnitProvider) registry.get(key);
    }
    
    
    public boolean isRegistered() {
        return registry != null;
    }
    
    public void registerProviders() {
        registry = new HashMap<String, ICompilationUnitProvider>();
        IExtensionPoint exP =
            Platform.getExtensionRegistry().getExtensionPoint(CUPROVIDERS_EXTENSION_POINT);
        if (exP != null) {
            IExtension[] exs = exP.getExtensions();
            for (int i = 0; i < exs.length; i++) {
                IConfigurationElement[] configs = exs[i].getConfigurationElements();
                for (int j = 0; j < configs.length; j++) {
                    try {
                        IConfigurationElement config = configs[j];
                        if (config.isValid()) {
                            ICompilationUnitProvider provider = (ICompilationUnitProvider) 
                                    config.createExecutableExtension("class"); //$NON-NLS-1$
                            registerCompilationUnitProvider(config.getAttribute("file_extension"), provider); //$NON-NLS-1$
                        }
                    } catch (CoreException e) {
                      ScalaJDTWeavingPlugin.logException(e);
                    } 
                }
            }
        }
    }
}
