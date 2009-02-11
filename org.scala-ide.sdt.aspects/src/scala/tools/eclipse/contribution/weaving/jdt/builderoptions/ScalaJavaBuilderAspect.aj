/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.builderoptions;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.core.builder.BatchImageBuilder;
import org.eclipse.jdt.internal.core.util.Util;

@SuppressWarnings("restriction")
public aspect ScalaJavaBuilderAspect {
  pointcut build() : execution(IProject[] ScalaJavaBuilder.build(int, Map, IProgressMonitor) throws CoreException);
  pointcut clean() : execution(void ScalaJavaBuilder.clean(IProgressMonitor) throws CoreException);
  pointcut cleanOutputFolders() : execution(void BatchImageBuilder.cleanOutputFolders(boolean) throws CoreException);
  pointcut isJavaLikeFileName(String fileName) : args(fileName) && execution(boolean Util.isJavaLikeFileName(String));  
  
  void around() : cleanOutputFolders() && cflow(build()) {
    // Just suppress the behaviour
  }
  
  boolean around(String fileName) : isJavaLikeFileName(fileName) && (cflow(build()) || cflow(clean())) {
    if (fileName != null && fileName.endsWith("scala"))
      return false;
    else
      return proceed(fileName);
  }
}
