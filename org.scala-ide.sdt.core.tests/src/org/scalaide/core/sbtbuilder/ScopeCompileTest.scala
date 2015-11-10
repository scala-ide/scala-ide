package org.scalaide.core
package sbtbuilder

import org.eclipse.core.runtime.IPath
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
      toggleStopOnErrorsProperty(projectA, on = false)
      toggleStopOnErrorsProperty(projectB, on = false)
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

  @Test def shouldFailProjectBTestScope(): Unit = {
    givenCleanWorkspaceForProjects(projectB)

    whenFileInScopeIsDamaged(projectB, "/src/test", "acme", "AcmeRefTest.scala", changedToNonCompiling) {
      val expectedOneError =
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

      Assert.assertTrue("See what's wrong: " + expectedOneError.mkString(", "), 1 == expectedOneError.length)
    }
  }

  @Test def shouldFailProjectBMainScopeAndTestIsNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectB)

    whenFileInScopeIsDamaged(projectB, "/src/main", "acme", "AcmeMainRef.scala", changedToNonCompiling) {
      val expectedTwoErrors =
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

      Assert.assertTrue("See what's wrong: " + expectedTwoErrors.mkString(", "), 1 == expectedTwoErrors.length)
    }
  }

  @Test def shouldFailProjectBMacrosScopeAndMainTestIsNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectB)

    whenFileInScopeIsDamaged(projectB, "/src/macros", "acme", "AcmeMacroRef.scala", changedToNonCompiling) {
      val expectedThreeErrors =
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

      Assert.assertTrue("See what's wrong: " + expectedThreeErrors.mkString(", "), 1 == expectedThreeErrors.length)
    }
  }

  @Test def shouldFailProjectATestScopeAndProjectBTestIsNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    whenFileInScopeIsDamaged(projectA, "/src/test", "acme", "AcmeTest.scala", changedToNonCompiling) {
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

    whenFileInScopeIsDamaged(projectA, "/src/main", "acme", "AcmeMain.scala", changedToNonCompiling) {
      val expectedTwoErrors =
        markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)
      val expectedThreeErrorInB =
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)
      val errors = expectedTwoErrors ++ expectedThreeErrorInB

      Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 4 == errors.length)
    }
  }

  @Test def shouldFailProjectAMacrosScopeAndItsMainTestAndProjectBMacrosMainTestNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    whenFileInScopeIsDamaged(projectA, "/src/macros", "acme", "AcmeMacro.scala", changedToNonCompiling) {
      val expectedThreeErrors =
        markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)
      val expectedThreeErrorInB =
        markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

      val errors = expectedThreeErrors ++ expectedThreeErrorInB

      Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 4 == errors.length)
    }
  }

  lazy val changedToNonCompiling = """
package acme

class NonCompiling {
  ] // error
}
"""
}
