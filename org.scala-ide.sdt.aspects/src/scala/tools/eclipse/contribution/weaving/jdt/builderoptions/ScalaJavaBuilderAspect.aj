/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.builderoptions;

import org.eclipse.core.resources.IResource;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.core.builder.BatchImageBuilder;
import org.eclipse.jdt.internal.core.builder.JavaBuilder;
//import org.eclipse.jdt.internal.core.builder.ClasspathMultiDirectory;
import org.eclipse.jdt.internal.core.util.Util;

@SuppressWarnings("restriction")
public privileged aspect ScalaJavaBuilderAspect {
  pointcut build() :
    execution(IProject[] ScalaJavaBuilder.build(int, Map, IProgressMonitor) throws CoreException);
  
  pointcut cleanOutputFolders(boolean copyBack) :
    args(copyBack) &&
    execution(void BatchImageBuilder.cleanOutputFolders(boolean) throws CoreException);
  
  pointcut isJavaLikeFileName(String fileName) :
    args(fileName) &&
    execution(boolean Util.isJavaLikeFileName(String));  
  
  pointcut filterExtraResource(IResource resource) :
    args(resource) &&
    execution(boolean JavaBuilder.filterExtraResource(IResource));
  
  void around(BatchImageBuilder builder, boolean copyBack) throws CoreException :
    target(builder) &&
    cleanOutputFolders(copyBack) &&
    cflow(build()) {
    // Suppress the cleaning behaviour but do the extra resource copying if requested
    if (copyBack)
      for (int i = 0, l = builder.sourceLocations.length; i < l; i++) {
        org.eclipse.jdt.internal.core.builder.ClasspathMultiDirectory sourceLocation = builder.sourceLocations[i];
        if (sourceLocation.hasIndependentOutputFolder)
          builder.copyExtraResourcesBack(sourceLocation, false);
      }
  }
  
  boolean around(String fileName) :
    isJavaLikeFileName(fileName) &&
    cflow(build()) &&
    !cflow(cleanOutputFolders(*)) {
    if (fileName != null && fileName.endsWith("scala"))
      return false;
    else
      return proceed(fileName);
  }
  
  boolean around(IResource resource) :
    filterExtraResource(resource) &&
    cflow(build()) {
    return resource.getName().endsWith(".scala") || proceed(resource);
  }
}
