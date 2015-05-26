package org.scalaide.core
package sbtbuilder

import org.junit.Test
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IResource
import org.junit.Assert._
import org.eclipse.core.resources.IMarker
import testsetup.SDTTestUtils
import org.eclipse.core.resources.IFile
import org.junit.Ignore
import org.junit.Before
import org.eclipse.jdt.core.ICompilationUnit
import org.scalaide.core.IScalaProject

object ScalaJavaDepTest extends testsetup.TestProjectSetup("scalajavadep")

class ScalaJavaDepTest {

  import ScalaJavaDepTest._
  import SDTTestUtils._

  @Before
  def setupWorkspace(): Unit = {
    enableAutoBuild(false)
  }

  @Test def testSimpleScalaDep(): Unit = {
    println("building " + project)
    cleanProject()

    val JJavaCU = compilationUnit("test/J.java")
    val SScalaCU = compilationUnit("test/S.scala")

    def getProblemMarkers = getProblemMarkersFor(JJavaCU, SScalaCU)

    val problems0 = getProblemMarkers
    assertTrue("Build errors found: " + markersMessages(problems0), problems0.isEmpty)

    val originalJJava = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/test/J.java").getContents)
    SDTTestUtils.changeContentOfFile(JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedJJava)
    rebuild(project)
    val problems1 = getProblemMarkers
    assertTrue("One build error expected, got: " + markersMessages(problems1), problems1.length == 1) // do more precise matching later

    SDTTestUtils.changeContentOfFile(JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalJJava)
    rebuild(project)
    val problems2 = getProblemMarkers
    assertTrue("Build errors found: " + markersMessages(problems2), problems2.isEmpty)
  }

  @Ignore
  @Test def testSimpleJavaDep(): Unit = {
    println("building " + project)
    cleanProject()

    val JJavaCU = compilationUnit("test/J.java")
    val SScalaCU = compilationUnit("test/S.scala")

    def getProblemMarkers = getProblemMarkersFor(JJavaCU, SScalaCU)

    val problems0 = getProblemMarkers
    assertTrue("Build errors found: " + markersMessages(problems0), problems0.isEmpty)

    val originalSScala = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/test/S.scala").getContents)
    SDTTestUtils.changeContentOfFile(SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedSScala)
    rebuild(project)
    val problems1 = getProblemMarkers
    assertTrue("One build error expected: " + markersMessages(problems1), problems1.length == 1) // do more precise matching later

    SDTTestUtils.changeContentOfFile(SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalSScala)
    rebuild(project)
    val problems2 = getProblemMarkers
    assertTrue("Build errors found: " + markersMessages(problems2), problems2.isEmpty)
  }

  def rebuild(prj: IScalaProject): Unit = {
    println("building " + prj)
    prj.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

  /**
   * Return the markers for the given compilation units.
   */
  def getProblemMarkersFor(units: ICompilationUnit*): List[IMarker] = {
    units.toList.flatMap(SDTTestUtils.findProblemMarkers)
  }

  /**
   * Launch a clean build on the project.
   */
  private def cleanProject(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
  }

  lazy val changedJJava = """
package test;

public class J {
  public static void main(String[] args) {
    new S().foo("ahoy");
  }
  public String bar1(String s) {
    return s + s;
  }
}
"""

  lazy val changedSScala = """
package test

class S {
  def foo1(s:String): Unit = { println(new J().bar(s)) }
}
"""

}