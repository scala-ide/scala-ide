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
import org.eclipse.core.runtime.Path

object SbtBuilderTest extends TestProjectSetup("builder") with CustomAssertion
object depProject extends TestProjectSetup("builder-sub")

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

  @Test def testSimpleBuildWithResources() {
    println("building " + depProject)
    depProject.project.clean(new NullProgressMonitor())
    depProject.project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
    
    val targetResource = depProject.project.javaProject.getOutputLocation().append(new Path("resource.txt"))
    val file = ScalaPlugin.plugin.workspaceRoot.findMember(targetResource)
    Assert.assertNotNull("Resource has been copied to the output directory", file ne null)
    Assert.assertTrue("Resource has been copied to the output directory and exists", file.exists())
  }

  @Test def dependent_projects_are_rebuilt_and_PC_notified() {

    def rebuild(prj: ScalaProject): List[IMarker] = {
      println("building " + prj)
      prj.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

      getProblemMarkers()
    }

    depProject.project // force initialization of this project
    depProject.project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

    println("=== Dependency Test === ")
    project.clean(new NullProgressMonitor())

    val problemsDep = rebuild(depProject.project)
    val problemsOrig = rebuild(project)
    Assert.assertTrue("Should succeed compilation " + problemsOrig, problemsOrig.isEmpty)

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
    Assert.assertEquals("Presentation compiler errors.", 2, pcProblems.size)
  }

  private def getProblemMarkers(): List[IMarker] = {
    val units = compilationUnits("test/ja/JClassA.java", "test/sc/ClassA.scala", "test/dependency/FooClient.scala").toList
    units.flatMap(SDTTestUtils.findProblemMarkers)
  }

  private def buildWith(resource: IResource, contents: String): List[String] = {
    SDTTestUtils.changeContentOfFile(depProject.project.underlying, resource.asInstanceOf[IFile], contents)

    println("=== Rebuilding workspace === ")
    SDTTestUtils.workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)

    val problems = getProblemMarkers()

    for (p <- problems) yield p.getAttribute(IMarker.MESSAGE).toString
  }

  @Test def dependentProject_should_restart_PC_after_build() {
    val fooCU = depProject.compilationUnit("subpack/Foo.scala")
    val changedErrors = buildWith(fooCU.getResource, changedFooScala)

    Assert.assertEquals("Build problems " + changedErrors, 2, changedErrors.size)

    val errorMessages = buildWith(fooCU.getResource, originalFooScala)
    Assert.assertEquals("No build problems: " + errorMessages, 0, errorMessages.size)

    val fooClientCU = scalaCompilationUnit("test/dependency/FooClient.scala")

    reload(fooClientCU)

    assertNoErrors(fooClientCU)
  }

  /** Returns true if the expected regular expression matches the given error message. */
  private def similarErrorMessage(msg: String)(expected: String): Boolean = {
    msg.matches(expected)
  }

  lazy val changedFooScala = """
    package subpack

class Foo1
"""

  lazy val originalFooScala = """
    package subpack

class Foo
"""

  /** Each error message is a regular expression. This allows some variation between compiler versions. */
  lazy val expectedMessages = List(
    "(object )?Foo is not a member of (package )?subpack",
    "not found: type Foo")
}
