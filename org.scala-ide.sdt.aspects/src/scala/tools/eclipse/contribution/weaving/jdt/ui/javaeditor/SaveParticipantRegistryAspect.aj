/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider;
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.IPostSaveListener;
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.SaveParticipantRegistry;

@SuppressWarnings("restriction")
public privileged aspect SaveParticipantRegistryAspect {
  pointcut getEnabledPostSaveListeners(IProject project) :
    args(project) &&
    execution(IPostSaveListener[] SaveParticipantRegistry.getEnabledPostSaveListeners(IProject));
  
  pointcut commitWorkingCopy(ScalaCompilationUnitDocumentProvider provider) :
    target(provider) &&
    execution(void CompilationUnitDocumentProvider.commitWorkingCopy(IProgressMonitor, Object, CompilationUnitDocumentProvider.CompilationUnitInfo, boolean));
  
  IPostSaveListener[] around(ScalaCompilationUnitDocumentProvider provider, IProject project) :
    getEnabledPostSaveListeners(project) && cflow(commitWorkingCopy(provider)) {
    System.err.println("Around getEnabledPostSaveListeners");
    return new IPostSaveListener[0];
  }
}
