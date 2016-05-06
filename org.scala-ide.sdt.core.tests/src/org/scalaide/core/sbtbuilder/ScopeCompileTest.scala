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
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.util.internal.SettingConverterUtil

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

  def defaultStopOnErrors() = {
    toggleStopOnErrorsProperty(projectA, on = false)
    toggleStopOnErrorsProperty(projectB, on = false)
  }

  @BeforeClass def setup(): Unit = {
    initializeProjects(bundleName, Seq(projectAName, projectBName)) {
      projectA = createProjectInWorkspace(projectAName, withSrcOutputStructure)
      projectA.asInstanceOf[ScalaProject].projectSpecificStorage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
      projectA.asInstanceOf[ScalaProject].projectSpecificStorage.save()
      projectB = createProjectInWorkspace(projectBName, withSrcOutputStructure)
      projectB.asInstanceOf[ScalaProject].projectSpecificStorage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
      projectB.asInstanceOf[ScalaProject].projectSpecificStorage.save()
      defaultStopOnErrors()
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

  private def runWithStopOnErrors(settings: (IScalaProject, Boolean)*)(run: => Unit): Unit = synchronized {
    try {
      settings.foreach {
        case (project, stopOnErrors) => toggleStopOnErrorsProperty(project, stopOnErrors)
      }
      run
    } finally {
      defaultStopOnErrors()
    }
  }

  @Test def shouldFailProjectAWithOneErrorBecauseOnStopErrorIsOnSoOtherScopesAreNotBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectA)

    runWithStopOnErrors((projectA, true)) {
      whenFileInScopeIsDamaged(projectA, "/src/macros", "acme", "AcmeMacro.scala", changedToNonCompiling) {
        val expectedOneError =
          markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)

        Assert.assertTrue("See what's wrong: " + expectedOneError.mkString(", "), 1 == expectedOneError.size)
      }
    }
  }

  @Test def shouldFailProjectAWithTwoErrorsBecauseOnStopErrorIsOffSoTriesToBuildMainScope(): Unit = {
    givenCleanWorkspaceForProjects(projectA)

    runWithStopOnErrors((projectA, false)) {
      whenFileInScopeIsDamaged(projectA, "/src/macros", "acme", "AcmeMacro.scala", changedToNonCompiling) {
        val expectedTwoErrors =
          markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)

        Assert.assertTrue("See what's wrong: " + expectedTwoErrors.mkString(", "), 2 == expectedTwoErrors.size)
      }
    }
  }

  @Test def shouldFailProjectsWithFourErrorsBecauseNonBNotBuildDueToErrorsInA(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    runWithStopOnErrors((projectA, true), (projectB, true)) {
      whenFileInScopeIsDamaged(projectA, "/src/macros", "acme", "AcmeMacro.scala", changedToNonCompiling) {
        val expectedOneErrorInA =
          markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)
        val expectedThreeErrorsInB =
          markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

        val errors = expectedOneErrorInA ++ expectedThreeErrorsInB
        Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 4 == errors.size)
      }
    }
  }

  @Test def shouldFailProjectsWithThreeErrorsBecauseBBuildsMacroScopeOnly(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    runWithStopOnErrors((projectA, true), (projectB, false)) {
      whenFileInScopeIsDamaged(projectA, "/src/macros", "acme", "AcmeMacro.scala", changedToNonCompiling) {
        val expectedOneErrorInA =
          markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)
        val expectedTwoErrorsInB =
          markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

        val errors = expectedOneErrorInA ++ expectedTwoErrorsInB
        Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 3 == errors.size)
      }
    }
  }

  @Test def shouldFailProjectsWithFiveErrorsSoTestScopeOfAIsBuiltOnly(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    runWithStopOnErrors((projectA, false), (projectB, true)) {
      whenFileInScopeIsDamaged(projectA, "/src/macros", "acme", "AcmeMacro.scala", changedToNonCompiling) {
        val expectedTwoErrorsInA =
          markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)
        val expectedThreeErrorsInB =
          markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

        val errors = expectedTwoErrorsInA ++ expectedThreeErrorsInB
        Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 5 == errors.size)
      }
    }
  }

  @Test def shouldFailProjectsWithFourErrorsSoTestScopeOfAAndMacroScopeInBAreBuilt(): Unit = {
    givenCleanWorkspaceForProjects(projectA, projectB)

    runWithStopOnErrors((projectA, false), (projectB, false)) {
      whenFileInScopeIsDamaged(projectA, "/src/macros", "acme", "AcmeMacro.scala", changedToNonCompiling) {
        val expectedTwoErrorsInA =
          markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)
        val expectedTwoErrorsInB =
          markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)

        val errors = expectedTwoErrorsInA ++ expectedTwoErrorsInB
        Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 4 == errors.size)
      }
    }
  }

  lazy val changedToNonCompiling = """
package acme

class NonCompiling {
  ] // error
}
"""
}
