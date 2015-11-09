package org.scalaide.core.sbtbuilder

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
import org.scalaide.logging.HasLogger

object BuildScopeUnitsTest extends IProjectOperations {
  import org.scalaide.core.testsetup.SDTTestUtils._
  private val projectName = "buildScopeUnits"
  private var project: IScalaProject = _
  private val bundleName = "org.scala-ide.sdt.core.tests"
  private val errorTypes = Array(SdtConstants.ProblemMarkerId, IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER)

  private val withSrcOutputStructure: SrcPathOutputEntry = (project, jProject) => {
    val macrosSourceFolder = project.getFolder("/src/macros")
    Seq(JavaCore.newSourceEntry(jProject.getPackageFragmentRoot(macrosSourceFolder).getPath))
  }

  @BeforeClass def setup(): Unit = {
    initializeProjects(bundleName, Seq(projectName)) {
      project = createProjectInWorkspace(projectName, withSrcOutputStructure)
    }
  }

  @AfterClass def cleanup(): Unit = {
    SDTTestUtils.deleteProjects(project)
  }
}

class BuildScopeUnitsTest extends IProjectOperations with IProjectHelpers with HasLogger {
  import org.scalaide.core.testsetup.SDTTestUtils._
  import BuildScopeUnitsTest._

  @Test
  def shouldRecognizeAutomaticallyAddedSourceFoldersAndCreateMarkersCorrectly(): Unit = {
    givenCleanWorkspaceForProjects(project)

    whenFileInScopeIsDamaged(project, "/src/macros", "acme", "Macro.scala", changedToNonCompiling) {
      val expectedOneError =
        markersMessages(findProjectProblemMarkers(project, errorTypes: _*).toList)

      Assert.assertTrue("See what's wrong: " + expectedOneError.mkString(", "), 1 == expectedOneError.length)
    }

    thenAddMainScopeSourceDirToProject()

    whenFileInScopeIsDamaged(project, "/src/macros", "acme", "Macro.scala", changedToNonCompiling) {
      val expectedTwoErrors =
        markersMessages(findProjectProblemMarkers(project, errorTypes: _*).toList)

      Assert.assertTrue("See what's wrong: " + expectedTwoErrors.mkString(", "), 2 == expectedTwoErrors.length)
    }

    thenAddTestsScopeSourceDirToProject()

    whenFileInScopeIsDamaged(project, "/src/macros", "acme", "Macro.scala", changedToNonCompiling) {
      val expectedThreeErrors =
        markersMessages(findProjectProblemMarkers(project, errorTypes: _*).toList)

      Assert.assertTrue("See what's wrong: " + expectedThreeErrors.mkString(", "), 3 == expectedThreeErrors.length)
    }

    thenRemoveMainScopeForExampleFromProject()

    whenFileInScopeIsDamaged(project, "/src/macros", "acme", "Macro.scala", changedToNonCompiling) {
      val expectedTwoErrors =
        markersMessages(findProjectProblemMarkers(project, errorTypes: _*).toList)

      Assert.assertTrue("See what's wrong: " + expectedTwoErrors.mkString(", "), 2 == expectedTwoErrors.length)
    }
  }

  private def thenAddMainScopeSourceDirToProject(): Unit = thenAddScopeWithSourceDirToProject("/src/main")

  private def thenAddTestsScopeSourceDirToProject(): Unit = thenAddScopeWithSourceDirToProject("/src/test")

  private def thenAddScopeWithSourceDirToProject(path: String): Unit = {
    val srcFolder = project.getFolder(path)
    val jProject = project.javaProject
    val classpathEntry = JavaCore.newSourceEntry(jProject.getPackageFragmentRoot(srcFolder).getPath)
    jProject.setRawClasspath(jProject.readRawClasspath :+ classpathEntry, true, null)
  }

  private def thenRemoveScopeWithSourceDirFromProject(path: String): Unit = {
    val srcFolder = project.getFolder(path)
    val jProject = project.javaProject
    val classpathEntry = JavaCore.newSourceEntry(jProject.getPackageFragmentRoot(srcFolder).getPath)
    jProject.setRawClasspath(jProject.readRawClasspath.toSeq.filterNot { _ == classpathEntry }.toArray, true, null)
  }

  private def thenRemoveMainScopeForExampleFromProject() = thenRemoveScopeWithSourceDirFromProject("/src/main")

  lazy val changedToNonCompiling = """
package acme

class NonCompiling {
  ] // error
}
"""
}
