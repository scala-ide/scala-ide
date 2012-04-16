package scala.tools.eclipse.ui

import org.eclipse.swt.widgets.Display
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.IStatus
import org.eclipse.ui.PartInitException
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.debug.core.ILaunch
import org.eclipse.ui.PlatformUI
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.CoreException
import java.lang.reflect.InvocationTargetException
import org.eclipse.debug.internal.ui.DebugUIMessages
import org.eclipse.debug.internal.ui.DebugUIPlugin

object ScalaTestPlugin extends AbstractUIPlugin {
  
  private val PLUGIN_ID = "scala.tools.eclipse.scalatest"
  private val VIEW_PART_NAME = "scala.tools.eclipse.ui.ScalaTestResultView"
    
  val listener = new ScalaTestListener()

  private def getDisplay = {
    val display= Display.getCurrent
    if (display == null) 
      Display.getDefault()
    else
      display
  }
  
  def getActiveWorkbenchWindow = {
    val workBench = getWorkbench
    if (workBench == null)
      null
    else
      workBench.getActiveWorkbenchWindow
  }
  
  def getActivePage() = {
    val activeWorkbenchWindow = getActiveWorkbenchWindow
    if (activeWorkbenchWindow == null)
      null
    else
      activeWorkbenchWindow.getActivePage
  }
  
  def showTestRunnerViewPartInActivePage(): ScalaTestRunnerViewPart =  {
    try {
      // Have to force the creation of view part contents
      // otherwise the UI will not be updated
      val page = getActivePage
      if (page == null) 
        null
      else {
        /*val view = page.findView(VIEW_PART_NAME).asInstanceOf[ScalaTestRunnerViewPart];
        if (view == null) {
          //	create and show the result view if it isn't created yet.
          page.showView(VIEW_PART_NAME, null, IWorkbenchPage.VIEW_VISIBLE).asInstanceOf[ScalaTestRunnerViewPart];
        } 
        else {
          view
        }*/
        page.showView(VIEW_PART_NAME).asInstanceOf[ScalaTestRunnerViewPart]
      }
    } 
    catch {
      case pie: PartInitException =>
        pie.printStackTrace()
        log(pie)
        null
    }
  }
  
  def asyncShowTestRunnerViewPart(fLaunch: ILaunch, fRunName: String, projectName: String) {
    listener.bindSocket()
    getDisplay.asyncExec(new Runnable() {
      def run() {
        val view = showTestRunnerViewPartInActivePage()
        if (view != null) {
          listener.addObserver(view)
          view.setSession(new ScalaTestRunSession(fLaunch, fRunName, projectName))
          val thread = new Thread(listener)
          thread.start()
        }
      }
    })
    listener.getPort
  }
  
  def getPluginId = PLUGIN_ID
  
  def log(e: Throwable) {
    log(new Status(IStatus.ERROR, getPluginId, IStatus.ERROR, "Error", e)); //$NON-NLS-1$
  }
  
  def log(status: IStatus) {
    getLog().log(status);
  }
  
  def doBuild(): Boolean = {
    try {
      PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {
        def run(monitor: IProgressMonitor)  {
          try {
            ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
          } 
          catch {
            case e: CoreException =>
              throw new InvocationTargetException(e);
          }
        }
      });
    } 
    catch {
      case e: InterruptedException =>
        return false // canceled by user
      case e: InvocationTargetException =>
        val title = DebugUIMessages.DebugUIPlugin_Run_Debug_1
        val message= DebugUIMessages.DebugUIPlugin_Build_error__Check_log_for_details__2
        val t = e.getTargetException();
        DebugUIPlugin.errorDialog(DebugUIPlugin.getShell, title, message, t);
        return false
    } 

    return true
  }
}