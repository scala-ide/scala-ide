package org.scalaide.core
package sbtbuilder

import java.io.File

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.jdt.core.JavaCore
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.scalaide.core.IScalaProject
import org.scalaide.core.SdtConstants
import org.scalaide.core.testsetup.FileUtils
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.SDTTestUtils.SrcPathOutputEntry
import org.scalaide.core.testsetup.SDTTestUtils.addToClasspath
import org.scalaide.core.testsetup.SDTTestUtils.changeContentOfFile
import org.scalaide.core.testsetup.SDTTestUtils.createProjectInWorkspace
import org.scalaide.core.testsetup.SDTTestUtils.findProjectProblemMarkers
import org.scalaide.core.testsetup.SDTTestUtils.getErrorMessages
import org.scalaide.core.testsetup.SDTTestUtils.sourceWorkspaceLoc
import org.scalaide.core.testsetup.SDTTestUtils.markersMessages
import org.scalaide.core.testsetup.SDTTestUtils.workspace
import org.scalaide.util.eclipse.EclipseUtils

import ScopeCompileTest.projectA
import ScopeCompileTest.projectB

object ScopeCompileTest {
  import org.scalaide.core.testsetup.SDTTestUtils._
  val projectAName = "scopeCompileProjectA"
  val projectBName = "scopeCompileProjectB"
  var projectA: IScalaProject = _
  var projectB: IScalaProject = _
  val bundleName = "org.scala-ide.sdt.core.tests"

  private val withSrcOutputStructure: SrcPathOutputEntry = (project, jProject) => {
    val macrosSourceFolder = project.getFolder("/src/macros")
    val mainSourceFolder = project.getFolder("/src/main")
    val testSourceFolder = project.getFolder("/src/test")
    val macrosOutputFolder = project.getFolder("/target/macros")
    val mainOutputFolder = project.getFolder("/target/main")
    val testOutputFolder = project.getFolder("/target/test")
    val srcOuts = List(
      macrosSourceFolder -> macrosOutputFolder,
      mainSourceFolder -> mainOutputFolder,
      testSourceFolder -> testOutputFolder)
    srcOuts.map {
      case (src, out) => JavaCore.newSourceEntry(
        jProject.getPackageFragmentRoot(src).getPath,
        Array[IPath](),
        jProject.getPackageFragmentRoot(out).getPath)
    }
  }

  @BeforeClass def setup(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
    var done = false
    List(projectAName, projectBName).foreach { name =>
      EclipseUtils.workspaceRunnableIn(workspace) { monitor =>
        val wspaceLoc = workspace.getRoot.getLocation
        val src = new File(sourceWorkspaceLoc(bundleName).toFile().getAbsolutePath + File.separatorChar + name)
        val dst = new File(wspaceLoc.toFile().getAbsolutePath + File.separatorChar + name)
        FileUtils.copyDirectory(src, dst)
        done = true
      }
    }
    while (!done) { Thread.sleep(0) }
    projectA = createProjectInWorkspace("scopeCompileProjectA", withSrcOutputStructure)
    projectB = createProjectInWorkspace("scopeCompileProjectB", withSrcOutputStructure)
    addToClasspath(projectB, JavaCore.newProjectEntry(projectA.underlying.getFullPath, false))
    projectA.underlying.refreshLocal(IResource.DEPTH_INFINITE, null)
    projectB.underlying.refreshLocal(IResource.DEPTH_INFINITE, null)
  }

  @AfterClass def cleanup(): Unit = {
    SDTTestUtils.deleteProjects(projectB, projectA)
  }
}

class ScopeCompileTest {
  import org.scalaide.core.testsetup.SDTTestUtils._
  import ScopeCompileTest._

  def givenCleanWorkspaceForProjects(projects: IScalaProject*): Unit = {
    workspace.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    projects.foreach { project =>
      Assert.assertTrue(getErrorMessages(project.underlying).isEmpty)
    }
  }

  def whenFileInScopeIsDamaged(project: IScalaProject, scopeRootPath: String, packageName: String, fileName: String)(thenAssertThat: => Unit): Unit = {
    val toChangeRoot = project.javaProject.findPackageFragmentRoot(new Path("/" + project.underlying.getName + scopeRootPath))
    val compilationUnit = toChangeRoot.getPackageFragment(packageName).getCompilationUnit(fileName)
    val unit = compilationUnit.getResource.asInstanceOf[IFile]
    val revert = compilationUnit.getBuffer.getContents
    try {
      changeContentOfFile(compilationUnit.getResource().asInstanceOf[IFile], changedToNonCompiling)
      workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

      thenAssertThat
    } finally
      changeContentOfFile(unit, revert)
  }

  @Test def shouldFailProjectBTestScope(): Unit = {
    givenCleanWorkspaceForProjects(projectB)

    whenFileInScopeIsDamaged(projectB, "/src/test", "acme", "AcmeRefTest.scala") {
      val expectedOneError =
        markersMessages(findProjectProblemMarkers(projectB.underlying, SdtConstants.ProblemMarkerId,
          IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER).toList)

      Assert.assertTrue("See what's wrong: " + expectedOneError.mkString(", "), 1 == expectedOneError.length)
    }
  }

  @Test def shouldFailProjectBMainScopeAndTestIsNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectB)

    whenFileInScopeIsDamaged(projectB, "/src/main", "acme", "AcmeMainRef.scala") {
      val expectedTwoErrors =
        markersMessages(findProjectProblemMarkers(projectB.underlying, SdtConstants.ProblemMarkerId,
          IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER).toList)

      Assert.assertTrue("See what's wrong: " + expectedTwoErrors.mkString(", "), 2 == expectedTwoErrors.length)
    }
  }

  @Test def shouldFailProjectBMacrosScopeAndMainTestIsNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectB)

    whenFileInScopeIsDamaged(projectB, "/src/macros", "acme", "AcmeMacroRef.scala") {
      val expectedThreeErrors =
        markersMessages(findProjectProblemMarkers(projectB.underlying, SdtConstants.ProblemMarkerId,
          IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER).toList)

      Assert.assertTrue("See what's wrong: " + expectedThreeErrors.mkString(", "), 3 == expectedThreeErrors.length)
    }
  }

  @Test def shouldFailProjectATestScopeAndProjectBTestIsNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    whenFileInScopeIsDamaged(projectA, "/src/test", "acme", "AcmeTest.scala") {
      val expectedOneError =
        markersMessages(findProjectProblemMarkers(projectA.underlying, SdtConstants.ProblemMarkerId,
          IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER).toList)
      val expectedOneErrorInB =
        markersMessages(findProjectProblemMarkers(projectB.underlying, SdtConstants.ProblemMarkerId,
          IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER).toList)

      val errors = expectedOneError ++ expectedOneErrorInB
      Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 2 == errors.length)
    }
  }

  @Test def shouldFailProjectAMainScopeAndItsTestAndProjectBMacrosMainTestNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    whenFileInScopeIsDamaged(projectA, "/src/main", "acme", "AcmeMain.scala") {
      val expectedTwoErrors =
        markersMessages(findProjectProblemMarkers(projectA.underlying, SdtConstants.ProblemMarkerId,
          IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER).toList)
      val expectedThreeErrorInB =
        markersMessages(findProjectProblemMarkers(projectB.underlying, SdtConstants.ProblemMarkerId,
          IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER).toList)
      val errors = expectedTwoErrors ++ expectedThreeErrorInB

      Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 5 == errors.length)
    }
  }

  @Test def shouldFailProjectAMacrosScopeAndItsMainTestAndProjectBMacrosMainTestNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    whenFileInScopeIsDamaged(projectA, "/src/macros", "acme", "AcmeMacro.scala") {
      val expectedThreeErrors =
        markersMessages(findProjectProblemMarkers(projectA.underlying, SdtConstants.ProblemMarkerId,
          IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER).toList)
      val expectedThreeErrorInB =
        markersMessages(findProjectProblemMarkers(projectB.underlying, SdtConstants.ProblemMarkerId,
          IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER).toList)

      val errors = expectedThreeErrors ++ expectedThreeErrorInB

      Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 6 == errors.length)
    }
  }

  lazy val changedToNonCompiling = """
package acme

class NonCompiling {
  ] // error
}
"""
}