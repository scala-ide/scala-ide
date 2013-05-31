package scala.tools.eclipse.buildmanager

import java.util.concurrent.CountDownLatch
import org.eclipse.core.internal.runtime.RuntimeLog
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.ILogListener
import org.eclipse.core.runtime.IStatus
import org.eclipse.jdt.core.JavaCore
import org.junit.Assert
import org.junit.Test
import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.junit.Before
import org.junit.After

class ProjectsCleanJobTest {
  val simulator = new EclipseUserSimulator
  import SDTTestUtils._

  @Test def clean_dependent_project_does_not_result_in_exception() {
    // The latch is used to have a deterministic (sequential) execution of the test.
    val latch: CountDownLatch = new CountDownLatch(1)
    // this listener gets called the moment the `ProjectsCleanJob` has finished run
    val resourceListener: IResourceChangeListener = { event: IResourceChangeEvent => latch.countDown() }

    // stores the status an exception gets logged by the `logListener`
    @volatile var status: IStatus = null
    // Listener to get notified if an exception goes uncaught while the `ProjectsCleanJob` is executing.
    // This listener is always called *before* the `resourceListener`.
    val logListener: ILogListener = { (_status: IStatus, _: String) =>
      if (!_status.isOK) {
        status = _status
        latch.countDown()
      }
    }

    val Seq(prjA, prjB) = createProjects("A", "B")

    try {
      // B -> A
      addToClasspath(prjB, JavaCore.newProjectEntry(prjA.underlying.getFullPath, false))

      Assert.assertEquals("No dependencies for base project", Seq(), prjA.transitiveDependencies)
      Assert.assertEquals("One direct dependency for B", Seq(prjA.underlying), prjB.transitiveDependencies)

      ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceListener)
      RuntimeLog.addLogListener(logListener)

      // The actual code that we want to test. Here we want to make sure that the scheduling of a clean job doesn't throw an exception.
      // This code is run asynchronously.
      ProjectsCleanJob(Seq(prjB.underlying)).schedule()

      latch.await() // wait until the clean job is finished (or an exception is thrown)

      if (status != null)
        Assert.fail(status.toString())
    }
    finally {
      ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceListener)
      RuntimeLog.removeLogListener(logListener)
      util.control.Exception.ignoring(classOf[Exception]) { deleteProjects(prjA, prjB) }
    }
  }

  private implicit def anonResourceChangeListener(f: IResourceChangeEvent => Unit): IResourceChangeListener = new IResourceChangeListener() {
    override def resourceChanged(event: IResourceChangeEvent): Unit = f(event)
  }

  private implicit def anonLogListener(f: (IStatus, String) => Unit): ILogListener = new ILogListener {
    def logging(status: IStatus, plugin: String): Unit = f(status, plugin)
  }
}