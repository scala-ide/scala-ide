package org.scalaide.core
package sbtbuilder

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.scalaide.core.IScalaProject
import org.scalaide.core.util.when

import org.scalaide.core.testsetup.SDTTestUtils

object ScalaJavaDepTest extends testsetup.TestProjectSetup("scalajavadep")

class ScalaJavaDepTest {

  import ScalaJavaDepTest._
  import org.scalaide.core.testsetup.SDTTestUtils._

  @Before
  def setupWorkspace(): Unit = {
    enableAutoBuild(false)
  }

  @Test def testSimpleScalaDep(): Unit = {
    println("building " + project)
    cleanProject()

    val JJavaCU = compilationUnit("test/J.java")
    val SScalaCU = compilationUnit("test/S.scala")
    val originalJJava = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/test/J.java").getContents)

    def getProblemMarkers = getProblemMarkersFor(JJavaCU, SScalaCU)

    when("initialize project") `then` "there is no error in project" in {
      val problems = getProblemMarkers
      assertTrue("No build errors expected, found: " + markersMessages(problems), problems.isEmpty)
    }

    when("change java bar to bar1") `then` "expect error" in {
      SDTTestUtils.changeContentOfFile(JJavaCU.getResource().getAdapter(classOf[IFile]), changedJJava)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("One build error expected, got: " + markersMessages(problems), problems.length == 1)
    }

    when("revert java bar1 to bar") `then` "expect no error" in {
      SDTTestUtils.changeContentOfFile(JJavaCU.getResource().getAdapter(classOf[IFile]), originalJJava)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("No build errors expected, found: " + markersMessages(problems), problems.isEmpty)
    }
  }

  @Test def testSimpleJavaDep(): Unit = {
    println("building " + project)
    cleanProject()

    val JJavaCU = compilationUnit("test/J.java")
    val SScalaCU = compilationUnit("test/S.scala")
    val originalSScala = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/test/S.scala").getContents)

    def getProblemMarkers = getProblemMarkersFor(JJavaCU, SScalaCU)

    when("initialize project") `then` "is no error" in {
      val problems = getProblemMarkers
      assertTrue("No build errors expected, found: " + markersMessages(problems), problems.isEmpty)
    }

    when("change scala foo to foo1") `then` "expect one error" in {
      SDTTestUtils.changeContentOfFile(SScalaCU.getResource().getAdapter(classOf[IFile]), changedSScala)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("One build error expected, found: " + markersMessages(problems), problems.length == 1)
    }

    when("revert scala foo1 to foo") `then` "expect no error" in {
      SDTTestUtils.changeContentOfFile(SScalaCU.getResource().getAdapter(classOf[IFile]), originalSScala)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("No build errors expected, found: " + markersMessages(problems), problems.isEmpty)
    }
  }

  @Test def testSimpleJavaDepWhenJavaProducesWarning(): Unit = {
    println("building " + project)
    cleanProject()

    val JJavaCU = compilationUnit("test/J.java")
    val SScalaCU = compilationUnit("test/S.scala")
    val originalJJava = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/test/J.java").getContents)

    def getProblemMarkers = getProblemMarkersFor(JJavaCU, SScalaCU)

    when("initialize project") `then` "is no java problem" in {
      val problems = getProblemMarkers
      assertTrue("No build problem expected, found: " + markersMessages(problems), problems.isEmpty)
    }

    when("change java") `then` "expect one java problem of warning severity" in {
      SDTTestUtils.changeContentOfFile(JJavaCU.getResource().getAdapter(classOf[IFile]), changedWarningJJava)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("One build warning problem expected, found: " + markersMessages(problems),
          problems.length == 1 &&
          problems.filter(_.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) == IMarker.SEVERITY_WARNING).length == 1)
    }

    when("revert java change") `then` "expect no problem" in {
      SDTTestUtils.changeContentOfFile(JJavaCU.getResource().getAdapter(classOf[IFile]), originalJJava)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("No build problems expected, found: " + markersMessages(problems), problems.isEmpty)
    }
  }

  @Test def testSimpleJavaDepWhenJavaProducesError(): Unit = {
    println("building " + project)
    cleanProject()

    val JJavaCU = compilationUnit("test/J.java")
    val SScalaCU = compilationUnit("test/S.scala")
    val originalJJava = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/test/J.java").getContents)

    def getProblemMarkers = getProblemMarkersFor(JJavaCU, SScalaCU)

    when("initialize project") `then` "is no java problem" in {
      val problems = getProblemMarkers
      assertTrue("No build problem expected, found: " + markersMessages(problems), problems.isEmpty)
    }

    when("change java") `then` "expect one java problem of error severity" in {
      SDTTestUtils.changeContentOfFile(JJavaCU.getResource().getAdapter(classOf[IFile]), changeErrorJJava)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("One build error problem expected, found: " + markersMessages(problems),
          problems.length == 1 &&
          problems.filter(_.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) == IMarker.SEVERITY_ERROR).length == 1)
    }

    when("revert java change") `then` "expect no problem" in {
      SDTTestUtils.changeContentOfFile(JJavaCU.getResource().getAdapter(classOf[IFile]), originalJJava)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("No build problems expected, found: " + markersMessages(problems), problems.isEmpty)
    }
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

  private lazy val changedJJava = """
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

  private lazy val changedWarningJJava = """
package test;
import java.util.List;
public class J {
  public static void main(String[] args) {
    new S().foo("ahoy");
  }
  public String bar(String s) {
    return s + s;
  }
  public List getList() {
    return null;
  }
}
"""

  private lazy val changeErrorJJava = """
package test;
public class J {
  public String bar(String s) {
    return s + s;
  }
  public List getList() {
    return null;
  }
}
"""

  private lazy val changedSScala = """
package test

class S {
  def foo1(s:String): Unit = { println(new J().bar(s)) }
}
"""
}
