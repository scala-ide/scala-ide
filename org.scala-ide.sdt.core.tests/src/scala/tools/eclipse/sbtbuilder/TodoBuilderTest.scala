package scala.tools.eclipse
package sbtbuilder

import org.junit.Test
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IResource
import org.junit.Assert
import org.eclipse.core.resources.IMarker
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.eclipse.core.resources.IFile
import org.junit.Ignore
import org.junit.Before
import org.mockito.Mockito._
import org.mockito.Matchers.any
import org.eclipse.jdt.core.IProblemRequestor
import org.eclipse.jdt.core.WorkingCopyOwner
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.util.matching.Regex
import testsetup._

object TodoBuilderTest extends TestProjectSetup("todobuilder") with CustomAssertion

class TodoBuilderTest {

  import TodoBuilderTest._

  @Before
  def setupWorkspace {
    SDTTestUtils.enableAutoBuild(false)
  }

  @Test def testTODOSearch() {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    val units = compilationUnits("test/foo/ClassA.scala")
    val allTasks = units.map { unit =>
      val tasks = unit.getUnderlyingResource().findMarkers(IJavaModelMarker.TASK_MARKER, true, IResource.DEPTH_INFINITE)
      println("tasks: %s: %s".format(unit, tasks.toList))
      tasks
    } flatten

    Assert.assertTrue("No valid TODO was found", allTasks.exists(p => similarMessage(p.getAttribute(IMarker.MESSAGE).toString)(expectedTODO)))
  }

  /** Returns true if the expected regular expression matches the given message. */
  private def similarMessage(msg: String)(expected: String): Boolean = {
    msg.matches(expected)
  }

  lazy val expectedTODO = "TODO : this works"
}
