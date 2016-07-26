package org.scalaide.sbt.core.build

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
import org.scalaide.logging.HasLogger
import org.scalaide.sbt.core.builder.Shutdownable
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.ui.internal.preferences.ScalaPluginSettings

object BuildSbtProjectTest extends IProjectOperations {
  import org.scalaide.core.testsetup.SDTTestUtils._

  private val projectName = "buildSbtProjectTest"
  private var project: IScalaProject = _
  private val bundleName = "org.scala-ide.sbt.core.tests"
  private val errorTypes = Array(SdtConstants.ProblemMarkerId, IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER)

  private val withSrcOutputStructure: SrcPathOutputEntry = (project, jProject) => {
    val mainSourceFolder = project.getFolder("/src/main/scala")
    val mainOutputFolder = project.getFolder("/target/main/scala")
    Seq(JavaCore.newSourceEntry(
      jProject.getPackageFragmentRoot(mainSourceFolder).getPath,
      Array[IPath](),
      jProject.getPackageFragmentRoot(mainOutputFolder).getPath))
  }

  @BeforeClass def setup(): Unit = {
    initializeProjects(bundleName, Seq(projectName)) {
      project = createProjectInWorkspace(projectName, withSrcOutputStructure)
    }
  }

  @AfterClass def cleanup(): Unit = {
    project.buildManager().asInstanceOf[Shutdownable].shutdown()
    SDTTestUtils.deleteProjects(project)
  }
}

class BuildSbtProjectTest extends IProjectOperations with IProjectHelpers with HasLogger {
  import BuildSbtProjectTest._
  import org.scalaide.core.testsetup.SDTTestUtils._

  private def resetClasspathEntries(): Unit = {
    val testDir = project.getFolder("/src/test/scala")
    val jProject = project.javaProject
    val testEntry = JavaCore.newSourceEntry(jProject.getPackageFragmentRoot(testDir).getPath)
    jProject.setRawClasspath(jProject.readRawClasspath.filterNot {
      Seq(testEntry).contains
    }, true, null)
  }

  @Test
  def shouldRecognizeAutomaticallyAddedSourceFoldersAndCreateMarkersCorrectlyForStopOnErrors(): Unit = {
    givenCleanWorkspaceForProjects(project)
    resetClasspathEntries()
    toggleStopOnErrorsProperty(project, on = true)
    val useSbtCompilerProperty = SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.useSbtCompiler.name)
    project.storage.setValue(useSbtCompilerProperty, true)

    whenFileInScopeIsDamaged(project, "/src/main/scala", "acme", "Main.scala", changedToNonCompiling) {
      val expectedTwoErrors =
        markersMessages(findProjectProblemMarkers(project, errorTypes: _*).toList)

      logger.info(expectedTwoErrors.mkString(", "))
      Assert.assertTrue("See what's wrong: " + expectedTwoErrors.mkString(", "), 2 == expectedTwoErrors.length)
    }
  }

  lazy val changedToNonCompiling = """
package acme

class NonCompiling {
  ] // error
}
"""
}