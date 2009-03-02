/*
 * Copyright 2005-2009 LAMP/EPFL
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
  val SCALA_INTERPRETER_LAUNCH_ID = "scala.interpreter"
  override def setActivePart(action : IAction, targetpart : IWorkbenchPart) = {}
  
  override def run(action: IAction) = {
    if(target.isEmpty) {
      val shell = new Shell
      MessageDialog.openInformation(shell, "Scala Development Tools", "Intepreter could not be created")
    }
    import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants._
    import org.eclipse.debug.core.DebugPlugin
    val manager = DebugPlugin.getDefault().getLaunchManager();
    val launchType = manager.getLaunchConfigurationType(SCALA_INTERPRETER_LAUNCH_ID);
    val projectName = target.get.getJavaProject.getProject.getName
    //Pull out the first interpreter configuration we can find for the project.
    val configuration = manager.getLaunchConfigurations(launchType).filter({ config =>       
      projectName.equals(config.getAttribute(ATTR_PROJECT_NAME, ""))
    }).firstOption
    
    val workingCopy = configuration match {
      case Some(config) =>
        config.getWorkingCopy
      case None =>
        val newConfig = launchType.newInstance(null, projectName);
        newConfig.setAttribute(ATTR_PROJECT_NAME, projectName);
        //TODO - What else do we need to set on the properties for this to work...
        newConfig
    }
    
    target match {
      case Some(x : IPackageFragment) =>
        workingCopy.setAttribute(InterpreterLaunchConstants.PACKAGE_IMPORT, x.getElementName)
      case Some(x : ITypeRoot) =>
        
      case _ => //Ignore
    }
    
    import org.eclipse.debug.ui.DebugUITools
    import org.eclipse.debug.core.ILaunchManager
    DebugUITools.launch(workingCopy, ILaunchManager.RUN_MODE)
    
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
