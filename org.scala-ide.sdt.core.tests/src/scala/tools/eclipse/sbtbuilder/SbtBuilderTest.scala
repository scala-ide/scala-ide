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

object SbtBuilderTest extends testsetup.TestProjectSetup("builder")

class SbtBuilderTest {

  import SbtBuilderTest._

  @Before
  def setupWorkspace {
    SDTTestUtils.enableAutoBuild(true)
  }

  @Test def testSimpleBuild() {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    val units = compilationUnits("test/ja/JClassA.java", "test/sc/ClassA.scala")
    val noErrors = units.forall { unit =>
      val problems = unit.getUnderlyingResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
      println("problems: %s: %s".format(unit, problems.toList))
      problems.isEmpty
    }

    Assert.assertTrue("Build errors found", noErrors)
  }

  @Test def dependencyTest() {

    def rebuild(prj: ScalaProject): List[IMarker] = {
      println("building " + prj)
      prj.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

      getProblemMarkers()
    }

    def getProblemMarkers(): List[IMarker] = {
      val units = compilationUnits("test/ja/JClassA.java", "test/sc/ClassA.scala", "test/dependency/FooClient.scala").toList
      units.flatMap(SDTTestUtils.findProblemMarkers)
    }

    object depProject extends testsetup.TestProjectSetup("builder-sub")

    println("=== Dependency Test === ")
    project.clean(new NullProgressMonitor())

    val problemsDep = rebuild(depProject.project)
    val problemsOrig = rebuild(project)
    Assert.assertTrue("Should succeed compilation", problemsOrig.isEmpty)

    val fooCU = depProject.compilationUnit("subpack/Foo.scala")
    println("IFile: " + fooCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile])
    SDTTestUtils.changeContentOfFile(depProject.project.underlying, fooCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedFooScala)

    val fooClientCU = scalaCompilationUnit("test/dependency/FooClient.scala")

    println("=== Rebuilding workspace === ")
    SDTTestUtils.workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)

    val problems = getProblemMarkers()

    val errorMessages: List[String] = for (p <- problems) yield p.getAttribute(IMarker.MESSAGE).toString

    Assert.assertEquals("Build problems", 2, problems.size)
    Assert.assertEquals("Build Problem should be in FooClient.scala", problems(0).getResource(), fooClientCU.getResource())
    Assert.assertEquals("Number of error messages differ", expectedMessages.size, errorMessages.size)
    for (error <- errorMessages) {
      Assert.assertTrue("Build error messages differ. Expected: %s, Actual: %s".format(expectedMessages, errorMessages), expectedMessages.exists(similarErrorMessage(error)))
    }

    fooClientCU.doWithSourceFile { (sf, comp) =>
      comp.askReload(fooClientCU, fooClientCU.getContents()).get // synchronize with the good compiler
    }

    val pcProblems = fooClientCU.asInstanceOf[ScalaSourceFile].getProblems()
    println(pcProblems)
    Assert.assertEquals("Presentation compiler errors.", 2, pcProblems.size)
  }
  
  /** Returns true if the expected regular expression matches the given error message. */
  private def similarErrorMessage(msg: String)(expected: String): Boolean = {
    msg.matches(expected)
  }

  lazy val changedFooScala = """
    package subpack

class Foo1
"""

  /** Each error message is a regular expression. This allows some variation between compiler versions. */
  lazy val expectedMessages = List(
    "(object )?Foo is not a member of (package )?subpack",
    "not found: type Foo")
}
