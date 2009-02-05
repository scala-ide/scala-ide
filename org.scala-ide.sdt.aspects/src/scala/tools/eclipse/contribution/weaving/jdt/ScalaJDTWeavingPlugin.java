/*
 * Copyright (c) 2008 Miles Sabin
 * All rights reserved
 * ------------------------
 * http://www.milessabin.com
 * mailto:miles@milessabin.com
 */

package scala.tools.eclipse.contribution.weaving.jdt;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;

public class ScalaJDTWeavingPlugin extends Plugin
{
  private static ScalaJDTWeavingPlugin INSTANCE;
  
  public static String ID = "org.eclipse.contribution.jdt"; //$NON-NLS-1$
  
  public ScalaJDTWeavingPlugin() {
      super();
      INSTANCE = this;
  }

  
  public static void logException(Throwable t) {
      INSTANCE.getLog().log(new Status(IStatus.ERROR, ID, t.getMessage(), t));
  }
  
  
  public static ScalaJDTWeavingPlugin getInstance() {
      return INSTANCE;
  }
  
  public void start(BundleContext context) throws Exception {
      super.start(context);
  }
}
