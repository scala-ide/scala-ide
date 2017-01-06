package org.scalaide.core
package sbtbuilder

import java.io.File

import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.JavaCore
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.core.testsetup.IProjectHelpers
import org.scalaide.core.testsetup.IProjectOperations
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.util.internal.SettingConverterUtil

object SourcePathFinderTest extends IProjectOperations {
  import org.scalaide.core.testsetup.SDTTestUtils._
  private val projectAName = "sourcePathFinderA"
  private val projectBName = "sourcePathFinderB"
  private var projectA: IScalaProject = _
  private var projectB: IScalaProject = _
  private val bundleName = "org.scala-ide.sdt.core.tests"

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
      projectA.asInstanceOf[ScalaProject].projectSpecificStorage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
      projectA.asInstanceOf[ScalaProject].projectSpecificStorage.save()
      projectB = createProjectInWorkspace(projectBName, withSrcOutputStructure)
      projectB.asInstanceOf[ScalaProject].projectSpecificStorage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
      projectB.asInstanceOf[ScalaProject].projectSpecificStorage.save()
      addToClasspath(projectB, JavaCore.newProjectEntry(projectA.underlying.getFullPath, false))
    }
  }

  @AfterClass def cleanup(): Unit = {
    SDTTestUtils.deleteProjects(projectB, projectA)
  }
}

class SourcePathFinderTest extends IProjectOperations with IProjectHelpers {
  import SourcePathFinderTest._
  import org.scalaide.core.internal.project.SourcePathFinder._

  @Test def shouldFindSeveralSourcesInTrasitiveProjects(): Unit = {
    buildIncrementalWorkspaceForProjects(projectA, projectB)

    Assert.assertTrue(Some(File.separator + "acme" + File.separator + "AcmeMacro.scala") == projectB.sourcePath("acme.A.AcmeMacro"))
    Assert.assertTrue(Some(File.separator + "acme" + File.separator + "AcmeMain.scala") == projectB.sourcePath("acme.A.AcmeMain"))
    Assert.assertTrue(Some(File.separator + "acme" + File.separator + "AcmeTestA.scala") == projectB.sourcePath("acme.A.AcmeTest"))
  }

  @Test def shouldFindSeveralSourcesInProject(): Unit = {
    buildIncrementalWorkspaceForProjects(projectA, projectB)

    Assert.assertTrue(Some(File.separator + "acme" + File.separator + "AcmeMacroRef.scala") == projectB.sourcePath("acme.B.AcmeMacroRef"))
    Assert.assertTrue(Some(File.separator + "acme" + File.separator + "AcmeMainRef.scala") == projectB.sourcePath("acme.B.AcmeMainRef"))
    Assert.assertTrue(Some(File.separator + "acme" + File.separator + "AcmeRefTest.scala") == projectB.sourcePath("acme.AcmeRefTest"))
  }
}
