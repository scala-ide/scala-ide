/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Sean McDirmid
 * @author Josh Suereth
 */
// $Id$

package scala.tools.eclipse
package actions

import org.eclipse.jface.action.IAction
import org.eclipse.ui.IObjectActionDelegate
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.swt.widgets.Shell
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jdt.core._

/**
 * This is the action defintion that will run a scala interpreter
 */
class InterpreterAction extends IObjectActionDelegate {
  var target : Option[IJavaElement] = None
  val SCALA_INTERPRETER_LAUNCH_ID = "scala.interpreter"
  override def setActivePart(action : IAction, targetpart : IWorkbenchPart) = {}

  override def run(action: IAction) = {
    if(target.isEmpty) {
      val shell = new Shell
      MessageDialog.openInformation(shell, "Scala Development Tools", "Intepreter could not be created")
    }

    val project = target.get.getJavaProject.getProject

    import scala.tools.eclipse.interpreter.Factory
    Factory.openConsoleInProjectFromTarget(project, target)
  }

  override def selectionChanged(action: IAction, select: ISelection) = {
    if (select.isInstanceOf[IStructuredSelection]) {
      (select.asInstanceOf[IStructuredSelection]).getFirstElement match {
        case item : IJavaProject =>
          this.target = Some(item)
          action.setText("Create Scala interpreter in " + item.getElementName)
        case item : IPackageFragment =>
          this.target = Some(item)
          action.setText("Create Scala interpreter in " + item.getElementName)
        case _ =>
          this.target = null
          action.setText("Create Scala interpreter")
      }
    }
  }
}
