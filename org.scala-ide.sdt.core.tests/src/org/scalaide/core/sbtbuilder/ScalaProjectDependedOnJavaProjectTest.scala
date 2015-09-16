package org.scalaide.core.sbtbuilder

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.scalaide.core.IScalaProject
import org.scalaide.core.SdtConstants
import org.scalaide.core.testsetup.IProjectHelpers
import org.scalaide.core.testsetup.IProjectOperations
import org.scalaide.core.testsetup.SDTTestUtils.addToClasspath
import org.scalaide.core.testsetup.SDTTestUtils.buildWorkspace
import org.scalaide.core.testsetup.SDTTestUtils.createJavaProjectInWorkspace
import org.scalaide.core.testsetup.SDTTestUtils.createProjectInWorkspace
import org.scalaide.core.testsetup.SDTTestUtils.findProjectProblemMarkers
import org.scalaide.core.testsetup.SDTTestUtils.markersMessages
import org.scalaide.util.eclipse.EclipseUtils

import ScalaProjectDependedOnJavaProjectTest.projectJ
import ScalaProjectDependedOnJavaProjectTest.projectS

object ScalaProjectDependedOnJavaProjectTest extends IProjectOperations {
  import org.scalaide.core.testsetup.SDTTestUtils._
  private val projectJName = "scalaDependedOnJavaJ"
  private val projectSName = "scalaDependedOnJavaS"
  private var projectJ: IJavaProject = _
  private var projectS: IScalaProject = _
  private val bundleName = "org.scala-ide.sdt.core.tests"

  private def withSrcOutputStructure(project: IProject, jProject: IJavaProject): Seq[IClasspathEntry] = {
    val mainSourceFolder = project.getFolder("/src/main")
    val mainOutputFolder = project.getFolder("/target/main")
    Seq(JavaCore.newSourceEntry(
      jProject.getPackageFragmentRoot(mainSourceFolder).getPath,
      Array[IPath](),
      jProject.getPackageFragmentRoot(mainOutputFolder).getPath))
  }

  @BeforeClass def setup(): Unit = {
    initializeProjects(bundleName, Seq(projectJName, projectSName)) {
      projectJ = createJavaProjectInWorkspace(projectJName, withSrcOutputStructure)
      projectS = createProjectInWorkspace(projectSName, withSrcOutputStructure _)
      addToClasspath(projectS, JavaCore.newProjectEntry(projectJ.getProject.getFullPath, false))
    }
  }

  @AfterClass def cleanup(): Unit = {
    EclipseUtils.workspaceRunnableIn(EclipseUtils.workspaceRoot.getWorkspace) { _ =>
      projectS.underlying.delete( /* force = */ true, /* monitor = */ null)
      projectJ.getProject.delete( /* force = */ true, /* monitor = */ null)
    }
  }
}

class ScalaProjectDependedOnJavaProjectTest extends IProjectOperations with IProjectHelpers {
  import ScalaProjectDependedOnJavaProjectTest._

  @Test def shouldCorrectlyBuildScalaProjectWhichDependsOnJavaOne(): Unit = {
    givenCleanWorkspaceForProjects(projectJ, projectS)

    buildWorkspace()

    val errors = markersMessages(findProjectProblemMarkers(projectS, SdtConstants.ProblemMarkerId).toList)

    Assert.assertTrue("no error expected: " + errors.mkString(", "), errors.isEmpty)
  }
}