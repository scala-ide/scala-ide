/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import javaelements.ScalaSourceFile
import scala.tools.refactoring.implementations.OrganizeImports
import org.eclipse.core.runtime.IProgressMonitor
import scala.tools.eclipse.properties.OrganizeImportsPreferences

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

    override def checkInitialConditions(pm: IProgressMonitor) = {
      val status = super.checkInitialConditions(pm)
      if(file.getProblems != null && !file.getProblems.isEmpty) {
        status.addWarning("There are errors in the file, organizing imports might produce incorrect results.")
      }
      status
    }
        
    val options = {    
      import OrganizeImportsPreferences._
      val project = file.getJavaProject.getProject
      
      val expandOrCollapse = getExpandOrCollapseForProject(project) match {
        case ExpandImports => refactoring.ExpandImports
        case CollapseImports => refactoring.CollapseImports
      }
      
      val wildcards = refactoring.AlwaysUseWildcards(getWildcardImportsForProject(project).toSet)
      
      val groups = getGroupsForProject(project).toList
      
      List(expandOrCollapse, wildcards, refactoring.SortImports, refactoring.GroupImports(groups))
    }
    
    def refactoringParameters = new refactoring.RefactoringParameters(options = options, deps = refactoring.Dependencies.FullyRecompute)
  }
}
