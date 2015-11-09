package org.scalaide.core
package sbtbuilder

import testsetup.TestProjectSetup
import testsetup.SDTTestUtils
import org.scalaide.core.IScalaProject
import org.junit.Test
import org.junit.Assert._
import org.scalaide.util.eclipse.EclipseUtils
import org.eclipse.core.resources.ResourcesPlugin
import org.scalaide.core.IScalaPlugin
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IResource
import junit.framework.Assert
import org.eclipse.core.resources.IMarker

/**
 * Test for test cases requiring nested projects (one project root is a subfolder of an other project)
 */
object NestedProjectsTest extends TestProjectSetup("nested-parent") {

  final val scalaProjectName= "nested-scala"

  /**
   * The nested scala project
   */
  lazy val scalaProject: IScalaProject = {
    val workspace = ResourcesPlugin.getWorkspace()
    EclipseUtils.workspaceRunnableIn(workspace) { monitor =>
      // create the project
      val newProject= workspace.getRoot().getProject(scalaProjectName)
      val projectDescription= workspace.newProjectDescription(scalaProjectName)
      projectDescription.setLocation(project.underlying.getLocation().append(scalaProjectName))
      newProject.create(projectDescription, null)
      newProject.open(null)
      JavaCore.create(newProject)
    }
    IScalaPlugin().getScalaProject(workspace.getRoot.getProject(scalaProjectName))
  }

  lazy val scalaSrcPackageRoot: IPackageFragmentRoot = {
    scalaProject.javaProject.findPackageFragmentRoot(new Path("/" + scalaProjectName + "/src"))
  }
}

class NestedProjectsTest {
  import NestedProjectsTest._

  /**
   * At first, the ExpandableResourceDelta was crashing when used for nested projects. This test checks that it is not
   * happening any more.
   */
  @Test
  def checkJavaCompilesInNestedProject(): Unit = {
    // clean the nested project
    scalaProject.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    scalaProject.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    // update and recompile Java_01.java
    val compilationUnit= scalaSrcPackageRoot.getPackageFragment("test").getCompilationUnit("Java_01.java")
    SDTTestUtils.changeContentOfFile(compilationUnit.getResource().asInstanceOf[IFile], changed_test_Java_01)

    scalaProject.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

    val nestedErrors = scalaProject.underlying.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
    val msgs = nestedErrors.map(_.getAttribute(IMarker.MESSAGE))

    // if the compilation failed, the class file was not generated
    val classFile= scalaProject.underlying.getFile("bin/test/Java_01.class")
    assertTrue(s"Missing class file $msgs", classFile.exists())
  }

  @Test
  def checkErrorsAreReported_onTheNestedProject(): Unit = {
    // clean the nested project
    scalaProject.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    scalaProject.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    // update and recompile Java_01.java
    val compilationUnit = scalaSrcPackageRoot.getPackageFragment("test").getCompilationUnit("Scala_01.scala")
    val saved = compilationUnit.getBuffer().getContents()
    val unitIFile = compilationUnit.getResource().asInstanceOf[IFile]
    try {
      SDTTestUtils.changeContentOfFile(unitIFile, changed_test_Scala_01)

      scalaProject.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

      val topLevelErrors = project.underlying.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
      assertEquals("No errors in top-level project", 0, topLevelErrors.length)

      val nestedErrors = scalaProject.underlying.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
      val errors = SDTTestUtils.markersMessages(nestedErrors.toList)
      assertEquals("One error in nested project " + errors, 1, errors.length)
    } finally
      SDTTestUtils.changeContentOfFile(unitIFile, saved)
  }

  // no real change, just a space after foo
  lazy val changed_test_Java_01 = """
package test;

public class Java_01 {
    public void foo(Scala_01 s) {
        s.foo ();
    }
}
"""

  lazy val changed_test_Scala_01 = """
package test

class Scala_01 {
  ] // error
}
"""
}
