/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.indexerprovider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

import scala.tools.eclipse.contribution.weaving.jdt.ScalaJDTWeavingPlugin;

public class IndexerProviderRegistry {
  public static String INDEXING_PROVIDERS_EXTENSION_POINT = "ch.epfl.lamp.sdt.aspects.indexerprovider"; //$NON-NLS-1$

  private static final IndexerProviderRegistry INSTANCE = new IndexerProviderRegistry();

  public static IndexerProviderRegistry getInstance() {
    return INSTANCE;
  }

  private List<IIndexerFactory> registry;

  List<IIndexerFactory> getProviders() {
    if (registry == null)
      registerProviders();

    return Collections.unmodifiableList(registry);
  }

  public void registerProviders() {
    registry = new ArrayList<IIndexerFactory>();
    IExtensionPoint exP = Platform.getExtensionRegistry().getExtensionPoint(INDEXING_PROVIDERS_EXTENSION_POINT);
    if (exP != null) {
      IExtension[] exs = exP.getExtensions();
      for (int i = 0; i < exs.length; i++) {
        IConfigurationElement[] configs = exs[i].getConfigurationElements();
        for (int j = 0; j < configs.length; j++) {
          try {
            IConfigurationElement config = configs[j];
            if (config.isValid()) {
              IIndexerFactory provider = (IIndexerFactory)config.createExecutableExtension("class"); //$NON-NLS-1$
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
