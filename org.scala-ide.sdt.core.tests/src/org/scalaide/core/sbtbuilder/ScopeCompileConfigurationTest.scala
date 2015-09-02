package org.scalaide.core.sbtbuilder

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.NullProgressMonitor
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
import org.scalaide.ui.internal.preferences.PropertyStore
import org.eclipse.ui.preferences.ScopedPreferenceStore

object ScopeCompileConfigurationTest extends IProjectOperations {
  import org.scalaide.core.testsetup.SDTTestUtils._
  private val projectName = "scopeConfiguration"
  private var project: IScalaProject = _
  private val bundleName = "org.scala-ide.sdt.core.tests"
  private val errorTypes = Array(SdtConstants.ProblemMarkerId, IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER)

  private val withSrcOutputStructure: SrcPathOutputEntry = (project, jProject) => {
    val macrosSourceFolder = project.getFolder("/src/macros")
    val macrosAsMainSourceFolder = project.getFolder("/src/macros-as-main")
    val mainSourceFolder = project.getFolder("/src/main")
    val testSourceFolder = project.getFolder("/src/test")
    val integrationSourceFolder = project.getFolder("/src/integration")
    val macrosOutputFolder = project.getFolder("/target/macros")
    val macrosAsMainOutputFolder = project.getFolder("/target/macros-as-main")
    val mainOutputFolder = project.getFolder("/target/main")
    val testOutputFolder = project.getFolder("/target/test")
    val integrationOutputFolder = project.getFolder("/target/integration")
    val srcOuts = List(
      macrosSourceFolder -> macrosOutputFolder,
      macrosAsMainSourceFolder -> macrosAsMainOutputFolder,
      mainSourceFolder -> mainOutputFolder,
      testSourceFolder -> testOutputFolder,
      integrationSourceFolder -> integrationOutputFolder)
    srcOuts.map {
      case (src, out) => JavaCore.newSourceEntry(
        jProject.getPackageFragmentRoot(src).getPath,
        Array[IPath](),
        jProject.getPackageFragmentRoot(out).getPath)
    }
  }

  @BeforeClass def setup(): Unit = {
    initializeProjects(bundleName, Seq(projectName)) {
      project = createProjectInWorkspace(projectName, withSrcOutputStructure)
      val storage = project.storage.asInstanceOf[ScopedPreferenceStore]
      storage.setValue("src/integration", "tests")
      storage.setValue("src/macros-as-main", "main")
      storage.save()
    }
  }

  @AfterClass def cleanup(): Unit = {
    SDTTestUtils.deleteProjects(project)
  }
}

class ScopeCompileConfigurationTest extends IProjectOperations with IProjectHelpers {
  import org.scalaide.core.testsetup.SDTTestUtils._
  import ScopeCompileConfigurationTest._

  @Test def shouldCorrectlyBuildProject(): Unit = {
    givenCleanWorkspaceForProjects(project)

    workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
    val expectedNoError =
      markersMessages(findProjectProblemMarkers(project, errorTypes: _*).toList)

    Assert.assertTrue("See what's wrong: " + expectedNoError.mkString(", "), 0 == expectedNoError.length)
  }
}
