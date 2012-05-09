/*
 * SCALA LICENSE
 *
 * Copyright (C) 2011-2012 Artima, Inc. All rights reserved.
 *
 * This software was developed by Artima, Inc.
 *
 * Permission to use, copy, modify, and distribute this software in source
 * or binary form for any purpose with or without fee is hereby granted,
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the EPFL nor the names of its contributors
 *    may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package scala.tools.eclipse.scalatest.ui

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
  
  private val PLUGIN_ID = "org.scala-ide.sdt.scalatest"
  private val VIEW_PART_NAME = "scala.tools.eclipse.scalatest.ui.ScalaTestResultView"
    
  var listener: ScalaTestListener = null

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
    
    listener = new ScalaTestListener()
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