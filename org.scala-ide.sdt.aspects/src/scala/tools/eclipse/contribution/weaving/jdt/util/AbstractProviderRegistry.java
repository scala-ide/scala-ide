/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

import scala.tools.eclipse.contribution.weaving.jdt.ScalaJDTWeavingPlugin;

public abstract class AbstractProviderRegistry<T> {

	abstract protected String getExtensionPointId();

	private List<T> registry = null;

	public List<T> getProviders() {
		if (registry == null)
			registry = registerProviders();
		return registry;
	}

	@SuppressWarnings("unchecked")
	public List<T> registerProviders() {
		List<T> registry = new ArrayList<T>();
		IExtensionPoint exP = Platform.getExtensionRegistry().getExtensionPoint( getExtensionPointId());
		if (exP != null) {
			IExtension[] exs = exP.getExtensions();
			for (int i = 0; i < exs.length; i++) {
				IConfigurationElement[] configs = exs[i].getConfigurationElements();
				for (int j = 0; j < configs.length; j++) {
					try {
						IConfigurationElement config = configs[j];
						if (config.isValid()) {
							T provider = (T) config.createExecutableExtension("class"); //$NON-NLS-1$
							registry.add(provider);
						}
					} catch (CoreException e) {
						ScalaJDTWeavingPlugin.logException(e);
					}
				}
			}
		}
		return Collections.unmodifiableList(registry);
	}
}
