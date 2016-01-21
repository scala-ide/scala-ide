package org.scalaide.core
package sbtbuilder

import org.junit.Test
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.runtime.Path
import org.eclipse.core.resources.IResource
import org.junit.Assert

import testsetup.SDTTestUtils
import org.junit.Before

object ScalaCompilerClasspathTest extends testsetup.TestProjectSetup("builder-compiler-classpath") {
  val baseRawClasspath = project.javaProject.getRawClasspath()
}

class ScalaCompilerClasspathTest {

  import ScalaCompilerClasspathTest._

  @Before
  def setupWorkspace(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
  }

  @Test def testWithoutCompilerOnClasspath(): Unit = {
    println("building " + project)
    project.javaProject.setRawClasspath(baseRawClasspath, new NullProgressMonitor)

    project.clean(new NullProgressMonitor())
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    val unit = compilationUnit("test/CompilerDep.scala")
    val errors = unit.getUnderlyingResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
    println("problem: %s: %s".format(unit.getResource(), errors.toList))
    Assert.assertTrue("Single compiler error expected", errors.length == 1)
  }

  @Test def testWithCompilerOnClasspath(): Unit = {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    val p = new Path(project.underlying.getLocation().toOSString()).append("/lib/2.10.x/scala-compiler.jar")
    Assert.assertTrue("scala compiler exists in the test framework", p.toFile().exists())
    val newRawClasspath = baseRawClasspath :+ JavaCore.newLibraryEntry(p, null, null)
    project.javaProject.setRawClasspath(newRawClasspath, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    val unit = compilationUnit("test/CompilerDep.scala")
    // val errors = unit.getUnderlyingResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
    val errors = SDTTestUtils.getErrorMessages(unit)
    println("problem: %s: %s".format(unit, errors))
    Assert.assertTrue("Build errors found", errors.isEmpty)
  }
}
