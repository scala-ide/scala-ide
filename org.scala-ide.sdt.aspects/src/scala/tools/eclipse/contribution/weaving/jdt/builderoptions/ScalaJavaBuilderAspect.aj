/*
 * Copyright (c) 2008 Miles Sabin
 * All rights reserved
 * ------------------------
 * http://www.milessabin.com
 * mailto:miles@milessabin.com
 */

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
    System.err.println("around cleanOutputFolders");
  }
  
  boolean around(String fileName) : isJavaLikeFileName(fileName) && (cflow(build()) || cflow(clean())) {
    System.err.println("around isJavaLikeFileName");

    if (fileName != null && fileName.endsWith("scala"))
      return false;
    else
      return proceed(fileName);
  }
}
