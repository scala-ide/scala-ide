/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package util

import org.eclipse.ui.PlatformUI
import org.eclipse.core.runtime.jobs.Job

object JobUtils {

  def askRunInJob(label : String, priority : Int = Job.INTERACTIVE)(f : => Unit) = {
    import org.eclipse.core.runtime.IStatus
    import org.eclipse.core.runtime.IProgressMonitor
    import org.eclipse.core.runtime.Status
    
    val job = new Job(label) {
      def run(monitor: IProgressMonitor): IStatus = {
        Defensive.tryOrLog(f)
        Status.OK_STATUS
      }
    }

    job.setSystem(true)
    job.setPriority(priority)
    job.schedule()
  }
  
  def askRunInUI(f : => Unit) = {
    //FIXME what is the "best" : JobUtils.askRunInJob("build error", Job.INTERACTIVE) or PlatformUI.getWorkbench.getDisplay().asyncExec to 
    PlatformUI.getWorkbench.getDisplay().asyncExec(new Runnable() {
      def run() {
        Defensive.tryOrLog(f)
      }
    })
  }
}