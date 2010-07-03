/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.builderoptions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.core.builder.JavaBuilder;

import scala.tools.eclipse.contribution.weaving.jdt.util.ReflectionUtils;

@SuppressWarnings("restriction")
public class ScalaJavaBuilder extends JavaBuilder {

  private static final Method setProjectMethod;
  static {
    try{
      Class<?> ibClazz = Class.forName("org.eclipse.core.internal.events.InternalBuilder");
      setProjectMethod = ReflectionUtils.getMethod(ibClazz, "setProject", IProject.class);
    }
    catch(ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public void clean(IProgressMonitor monitor) throws CoreException {
    super.clean(monitor);
  }
  
  @Override
  @SuppressWarnings("unchecked")
  public IProject[] build(int kind, Map ignored, IProgressMonitor monitor) throws CoreException {
    return super.build(kind, ignored, monitor);
  }
  
  public void setProject0(IProject project) {
    try
    {
      setProjectMethod.invoke(this, project);
    }
    catch(IllegalArgumentException e)
    {
      throw new RuntimeException(e);
    }
    catch(IllegalAccessException e)
    {
      throw new RuntimeException(e);
    }
    catch(InvocationTargetException e)
    {
      throw new RuntimeException(e);
    }
  }
}
