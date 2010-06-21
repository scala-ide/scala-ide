/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.cfprovider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

import scala.tools.eclipse.contribution.weaving.jdt.ScalaJDTWeavingPlugin;

public class ClassFileProviderRegistry {
  public static String CFPROVIDERS_EXTENSION_POINT = "ch.epfl.lamp.sdt.aspects.cfprovider"; //$NON-NLS-1$

  private static final ClassFileProviderRegistry INSTANCE = new ClassFileProviderRegistry();

  public static ClassFileProviderRegistry getInstance() {
    return INSTANCE;
  }

  private List<IClassFileProvider> registry;

  List<IClassFileProvider> getProviders() {
    if (registry == null)
      registerProviders();

    return Collections.unmodifiableList(registry);
  }

  public void registerProviders() {
    registry = new ArrayList<IClassFileProvider>();
    IExtensionPoint exP = Platform.getExtensionRegistry().getExtensionPoint(CFPROVIDERS_EXTENSION_POINT);
    if (exP != null) {
      IExtension[] exs = exP.getExtensions();
      for (int i = 0; i < exs.length; i++) {
        IConfigurationElement[] configs = exs[i].getConfigurationElements();
        for (int j = 0; j < configs.length; j++) {
          try {
            IConfigurationElement config = configs[j];
            if (config.isValid()) {
              IClassFileProvider provider = (IClassFileProvider)config.createExecutableExtension("class"); //$NON-NLS-1$
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
