/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import javaelements.ScalaSourceFile
import scala.tools.refactoring.implementations.OrganizeImports

/**
 * A simple wrapper for the refactoring library's organize imports.
 * 
 * Organize Imports currently supports the following actions:
 * 
 *  - Sort imports by the package name
 *  - Collapse imports from the same package into one statement 
 *    (the refactoring library could also expand imports, but this
 *    is not yet implemented here)
 *  - Removes unneeded imports. This operation is very conservative
 *    at the moment, so it will likely not remove all unnneded imports.
 */
class OrganizeImportsAction extends RefactoringAction {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new OrganizeImportsScalaIdeRefactoring(file)
  
  class OrganizeImportsScalaIdeRefactoring(file: ScalaSourceFile) extends ScalaIdeRefactoring("Organize Imports", file, 0, 0) {
                  
    val refactoring = withCompiler( c => new OrganizeImports { val global = c })

    /**
     * The refactoring does not take any parameters.
     */        
    def refactoringParameters = new refactoring.RefactoringParameters  
  }
}
