/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.JavaModelException 
import org.eclipse.jdt.internal.core.{ CompilationUnit, JavaElement, OpenableElementInfo }
import org.eclipse.jface.viewers.{ Viewer, ViewerFilter } 

/**
 * Prevents .scala files being displayed twice (both as an IFile and as an
 * ICompilationUnit), we need to filter the IFile if a corresponding
 * ICompilationUnit exists.
 */
class ScalaCompilationUnitFilter extends ViewerFilter {
  def select(viewer : Viewer, parentElement : AnyRef, element : AnyRef) : Boolean = {
    try {
      if (element.isInstanceOf[ScalaCompilationUnit])
        return element.asInstanceOf[ScalaCompilationUnit].exists
      
      if (!element.isInstanceOf[IFile])
        return true
      
      val f = element.asInstanceOf[IFile]
      if(f.getFileExtension != "scala")
        return true
      
      val scu = ScalaCompilationUnitManager.getScalaCompilationUnitFromCache(f)
      if (scu == null)
        return f.exists
      
      if (!ScalaCompilationUnitManager.ensureUnitIsInModel(scu))
          JDTUtils.refreshPackageExplorer
      false
    } catch {
      // Deliberately ignored
      case _ : JavaModelException => true
    }
  }
}
