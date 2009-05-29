/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider;
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.IPostSaveListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.IFileEditorInput;

@SuppressWarnings("restriction")
public privileged aspect SaveParticipantRegistryAspect {
  pointcut notifyPostSaveListeners(CompilationUnitDocumentProvider.CompilationUnitInfo info, IRegion[] changedRegions, IPostSaveListener[] listeners, IProgressMonitor monitor) :
    args(info, changedRegions, listeners, monitor) &&
    execution(void notifyPostSaveListeners(CompilationUnitDocumentProvider.CompilationUnitInfo, IRegion[], IPostSaveListener[], IProgressMonitor));
  
  void around(CompilationUnitDocumentProvider.CompilationUnitInfo info, IRegion[] changedRegions, IPostSaveListener[] listeners, IProgressMonitor monitor) :
    notifyPostSaveListeners(info, changedRegions, listeners, monitor) {
    if (info.fElement instanceof IFileEditorInput) {
      IFile file = ((IFileEditorInput)info.fElement).getFile();
      if (!file.getFileExtension().equals("scala"))
        proceed(info, changedRegions, listeners, monitor);
      else
        return;
    }
  }
}
