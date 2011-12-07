package scala.tools.eclipse
package sbtbuilder

import org.junit.{ Before, Test, Assert }
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder

import scala.tools.eclipse.testsetup.SDTTestUtils

object MultipleErrorsTest extends testsetup.TestProjectSetup("builder-errors")

class MultipleErrorsTest {
  import MultipleErrorsTest._
    
  @Before
  def setupWorkspace {
    SDTTestUtils.enableAutoBuild(false)
  }

  @Test def test1000735() {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    val units = compilationUnits("test/Foo.scala")
    val errors = units.flatMap(SDTTestUtils.findProblemMarkers)

    Assert.assertTrue("Expected one error message, got: " + errors.length, errors.length == 1)
  }
}