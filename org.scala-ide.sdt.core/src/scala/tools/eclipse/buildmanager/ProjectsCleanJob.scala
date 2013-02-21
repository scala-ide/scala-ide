package scala.tools.eclipse.buildmanager

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.WorkspaceJob
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.SubProgressMonitor
import org.eclipse.ui.progress.IProgressConstants2

/** Job for asynchronously cleaning the passed `projects`.*/
class ProjectsCleanJob private (projects: Seq[IProject]) {

  private class DoCleanJob(projects: Seq[IProject]) extends WorkspaceJob("Cleaning project" + (if (projects.length > 1) "s" else "")) {
    override def belongsTo(family: AnyRef): Boolean = ResourcesPlugin.FAMILY_MANUAL_BUILD.equals(family)
    override def runInWorkspace(monitor: IProgressMonitor): IStatus = {
      doClean(monitor)
      Status.OK_STATUS
    }

    private def doClean(monitor: IProgressMonitor): Unit = {
      try {
        monitor.beginTask(getName(), projects.length)
        for {
          project <- projects
          if project != null
        } project.build(IncrementalProjectBuilder.CLEAN_BUILD, new SubProgressMonitor(monitor, 1))
      }
      finally monitor.done()
    }
  }

  private def cleanJob: WorkspaceJob = {
    val cleanJob = new DoCleanJob(projects)
    cleanJob.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule())
    cleanJob.setUser(true)
    cleanJob.setProperty(IProgressConstants2.SHOW_IN_TASKBAR_ICON_PROPERTY, true)
    cleanJob
  }

  /** Schedule this job to run. */
  def schedule(): Unit = {
    if (projects.nonEmpty) cleanJob.schedule()
  }
}

object ProjectsCleanJob {
  def apply(projects: Seq[IProject]): ProjectsCleanJob = new ProjectsCleanJob(projects)
}