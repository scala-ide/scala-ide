package org.scalaide.core
package sbtbuilder

import org.junit.BeforeClass
import org.scalaide.core.testsetup.SDTTestUtils
import org.junit.Assert._
import org.junit.Test
import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.resources.IMarker

object JavaDependsOnScalaTest extends testsetup.TestProjectSetup("javaDependsOnScala") {
  @BeforeClass def setup(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
  }
}

class JavaDependsOnScalaTest {
  import JavaDependsOnScalaTest._

  @Test def shouldCreateProjectFromWrongJavaAndSpoilAndFixScalaClass(): Unit = {
    val TypeBMustImplementFoo = 1
    cleanProject()
    import SDTTestUtils._

    val aClass = compilationUnit("main/A.scala")
    val initiallyWrongAclass = slurpAndClose(project.underlying.getFile("src/main/A.scala").getContents)
    val bClass = compilationUnit("main/B.java")
    def rebuildAndCollectProblems(): List[IMarker] = {
      project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
      getProblemMarkers(aClass, bClass)
    }

    step("first compile scala and dependent java classes") {
      val problems = rebuildAndCollectProblems()
      assertTrue("One build error expected, got: " + markersMessages(problems), problems.length == TypeBMustImplementFoo)
    }

    step("then fix scala class") {
      changeContentOfFile(aClass.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changed_main_A)
      val problems = rebuildAndCollectProblems()
      assertTrue("No build error expected, got: " + markersMessages(problems), problems.isEmpty)
    }

    step("and then flaw scala class again") {
      changeContentOfFile(aClass.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], initiallyWrongAclass)
      val problems = rebuildAndCollectProblems()
      assertTrue("One build error expected, got: " + markersMessages(problems), problems.length == TypeBMustImplementFoo)
    }

    step("finally fix scala class") {
      changeContentOfFile(aClass.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changed_main_A)
      val problems = rebuildAndCollectProblems()
      assertTrue("No build error expected, got: " + markersMessages(problems), problems.isEmpty)
    }
  }

  private def step(description: String)(runInStep: => Unit): Unit = runInStep

  lazy val changed_main_A = """
package main

abstract class A {
  def foo(s: String): Unit = {}
}
"""
}
