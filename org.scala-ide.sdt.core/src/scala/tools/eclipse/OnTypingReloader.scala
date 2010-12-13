package scala.tools.eclipse

import org.eclipse.core.runtime.jobs.Job
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.internal.logging.Tracer
  
class OnTypingReloader {
  import scala.tools.eclipse.util.IDESettings
  
  private var _lastJob : ReloadJob = null
  
  def compileOnTyping : Boolean = IDESettings.compileOnTyping.value
  def compileOnTypingDelay : Int = IDESettings.compileOnTypingDelay.value
  
  def schedule(project : ScalaProject, scu : ScalaCompilationUnit) = {
    // cancel any reload job regardless of the target ScalaCompilationUnit
    if (_lastJob != null) _lastJob.cancel()
    
    if (compileOnTyping) {
      val j = new ReloadJob(project, scu)
      val delay = compileOnTypingDelay
      if (delay < 1) {
        j.run(null)
        _lastJob = null
      } else {
        Tracer.println("job : askReload schedule " + delay)
        j.schedule(delay)
        _lastJob = j
      }
    }
  }
}

private class ReloadJob(project : ScalaProject, scu : ScalaCompilationUnit) extends Job("Scalac Reload Content Job") {
  import org.eclipse.core.runtime.IProgressMonitor
  import org.eclipse.core.runtime.{IStatus, Status}
  
  // constructor
  setSystem(true)
  setPriority(Job.DECORATE)

  override def shouldRun() : Boolean = super.shouldRun() && scu.isOpen

  def run(monitor : IProgressMonitor) : IStatus = {
    Tracer.println("job : askReload ")
    project.withPresentationCompiler(_.askReload(scu, scu.getContents))
    return Status.OK_STATUS;
  }
}

