/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.jdt.core.{ ICompilationUnit, WorkingCopyOwner }
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner

object ScalaWorkingCopyOwner extends WorkingCopyOwner {
  override def createBuffer(workingCopy : ICompilationUnit) = {
    if (DefaultWorkingCopyOwner.PRIMARY.primaryBufferProvider != null) 
      DefaultWorkingCopyOwner.PRIMARY.primaryBufferProvider.createBuffer(workingCopy)
    else
      super.createBuffer(workingCopy)
  }
    
  override def  toString = "SDT working copy owner"
}
