package org.scalaide.core
package sbtbuilder

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.scalaide.core.testsetup.SDTTestUtils

import ScalaJavaDepWhenJavaIsWrongTest.cleanProject
import ScalaJavaDepWhenJavaIsWrongTest.compilationUnit
import ScalaJavaDepWhenJavaIsWrongTest.project
import testsetup.SDTTestUtils.changeContentOfFile
import testsetup.SDTTestUtils.getProblemMarkers
import testsetup.SDTTestUtils.slurpAndClose
import testsetup.SDTTestUtils.markersMessages

object ScalaJavaDepWhenJavaIsWrongTest
    extends testsetup.TestProjectSetup("scalajavadepjavaiswrong") {
  @BeforeClass def setup(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
  }
}

class ScalaJavaDepWhenJavaIsWrongTest {
  import ScalaJavaDepWhenJavaIsWrongTest._
  import testsetup.SDTTestUtils._

  @Test def shouldCreateProjectFromWrongJava(): Unit = {
    val TypeCMustImplementFoo = 1
    val BarIsNotMemberOfJ = 1
    cleanProject()

    val aClass = compilationUnit("ticket_1000607/A.scala")
    val cClass = compilationUnit("ticket_1000607/C.java")
    val JJavaCU = compilationUnit("test/J.java")
    val SScalaCU = compilationUnit("test/S.scala")
    def findProblems() = getProblemMarkers(aClass, cClass, JJavaCU, SScalaCU)

    rebuild()
    val problems0 = findProblems()
    assertTrue("One build error expected, got: " + markersMessages(problems0), problems0.length == TypeCMustImplementFoo)

    slurpAndClose(project.underlying.getFile("src/ticket_1000607/A.scala").getContents)
    changeContentOfFile(aClass.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changed_ticket_1000607_A)
    rebuild()
    val problems1 = findProblems()
    assertTrue("Build errors found: " + markersMessages(problems1), problems1.isEmpty)

    val originalJJava = slurpAndClose(project.underlying.getFile("src/test/J.java").getContents)
    changeContentOfFile(JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedJJava)
    rebuild()
    val problems2 = findProblems()
    assertTrue("One build error expected, got: " + markersMessages(problems2),
      problems2.length == BarIsNotMemberOfJ)

    changeContentOfFile(JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalJJava)
    rebuild()
    val problems3 = findProblems()
    assertTrue("Build errors found: " + markersMessages(problems3), problems3.isEmpty)
  }

  private def rebuild(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

  lazy private val changedJJava = """
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

  lazy val changed_ticket_1000607_A = """
package ticket_1000607

trait A {
  def foo(s: String): Unit = {}
}

abstract class B extends A
"""
}
