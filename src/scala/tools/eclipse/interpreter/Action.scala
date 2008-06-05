/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 * @author Josh Suereth
 */
// $Id$

package scala.tools.eclipse.interpreter

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
class Action extends IObjectActionDelegate {
  var target : Option[IJavaElement] = None
  
  override def setActivePart(action : IAction, targetpart : IWorkbenchPart) = {}
  
  override def run(action: IAction) = {
    if(target.isEmpty) {
      val shell = new Shell
      MessageDialog.openInformation(shell, "Scala Development Tools", "Intepreter could not be created")
    }
    println("Action.run")
    ScalaConsoleMgr.mkConsole(target.get)
  }
  
  override def selectionChanged(action: IAction, select: ISelection) = {
    if (select.isInstanceOf[IStructuredSelection]) {
      var target = (select.asInstanceOf[IStructuredSelection]).getFirstElement
      if (target.isInstanceOf[IJavaProject]) {
        val item = target.asInstanceOf[IJavaProject]
        this.target = Some(item)
        action.setText("Create Scala interpreter in " + item.getElementName)
      } else if (target.isInstanceOf[IPackageFragment]) {
        val item = target.asInstanceOf[IPackageFragment]
        this.target = Some(item)
        action.setText("Create Scala interpreter in " + item.getElementName)
      } else {
        this.target = null
        action.setText("Create Scala interpreter")
      }
    }
  }
}
