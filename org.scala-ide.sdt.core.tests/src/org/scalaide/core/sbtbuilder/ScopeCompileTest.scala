package org.scalaide.core
package sbtbuilder

import org.eclipse.core.resources.IFile
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
import org.scalaide.core.testsetup.IProjectHelpers
import org.scalaide.core.testsetup.IProjectOperations
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.SDTTestUtils.SrcPathOutputEntry
import org.scalaide.core.testsetup.SDTTestUtils.addToClasspath
import org.scalaide.core.testsetup.SDTTestUtils.changeContentOfFile
import org.scalaide.core.testsetup.SDTTestUtils.createProjectInWorkspace
import org.scalaide.core.testsetup.SDTTestUtils.findProjectProblemMarkers
import org.scalaide.core.testsetup.SDTTestUtils.markersMessages
import org.scalaide.core.testsetup.SDTTestUtils.workspace

import ScopeCompileTest.errorTypes
import ScopeCompileTest.projectA
import ScopeCompileTest.projectB

object ScopeCompileTest extends IProjectOperations {
  import org.scalaide.core.testsetup.SDTTestUtils._
  private val projectAName = "scopeCompileProjectA"
  private val projectBName = "scopeCompileProjectB"
  private var projectA: IScalaProject = _
  private var projectB: IScalaProject = _
  private val bundleName = "org.scala-ide.sdt.core.tests"
  private val errorTypes = Array(SdtConstants.ProblemMarkerId, IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER)

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
    initializeProjects(bundleName, Seq(projectAName, projectBName)) {
      projectA = createProjectInWorkspace(projectAName, withSrcOutputStructure)
      projectB = createProjectInWorkspace(projectBName, withSrcOutputStructure)
      addToClasspath(projectB, JavaCore.newProjectEntry(projectA.underlying.getFullPath, false))
    }
  }

  @AfterClass def cleanup(): Unit = {
    SDTTestUtils.deleteProjects(projectB, projectA)
  }
}

class ScopeCompileTest extends IProjectOperations with IProjectHelpers {
  import org.scalaide.core.testsetup.SDTTestUtils._
  import ScopeCompileTest._

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
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

      Assert.assertTrue("See what's wrong: " + expectedOneError.mkString(", "), 1 == expectedOneError.length)
    }
  }

  @Test def shouldFailProjectBMainScopeAndTestIsNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectB)

    whenFileInScopeIsDamaged(projectB, "/src/main", "acme", "AcmeMainRef.scala") {
      val expectedTwoErrors =
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

      Assert.assertTrue("See what's wrong: " + expectedTwoErrors.mkString(", "), 2 == expectedTwoErrors.length)
    }
  }

  @Test def shouldFailProjectBMacrosScopeAndMainTestIsNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectB)

    whenFileInScopeIsDamaged(projectB, "/src/macros", "acme", "AcmeMacroRef.scala") {
      val expectedThreeErrors =
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

      Assert.assertTrue("See what's wrong: " + expectedThreeErrors.mkString(", "), 3 == expectedThreeErrors.length)
    }
  }

  @Test def shouldFailProjectATestScopeAndProjectBTestIsNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    whenFileInScopeIsDamaged(projectA, "/src/test", "acme", "AcmeTest.scala") {
      val expectedOneError =
        markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)
      val expectedOneErrorInB =
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

      val errors = expectedOneError ++ expectedOneErrorInB
      Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 2 == errors.length)
    }
  }

  @Test def shouldFailProjectAMainScopeAndItsTestAndProjectBMacrosMainTestNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    whenFileInScopeIsDamaged(projectA, "/src/main", "acme", "AcmeMain.scala") {
      val expectedTwoErrors =
        markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)
      val expectedThreeErrorInB =
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)
      val errors = expectedTwoErrors ++ expectedThreeErrorInB

      Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 5 == errors.length)
    }
  }

  @Test def shouldFailProjectAMacrosScopeAndItsMainTestAndProjectBMacrosMainTestNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    whenFileInScopeIsDamaged(projectA, "/src/macros", "acme", "AcmeMacro.scala") {
      val expectedThreeErrors =
        markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)
      val expectedThreeErrorInB =
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

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