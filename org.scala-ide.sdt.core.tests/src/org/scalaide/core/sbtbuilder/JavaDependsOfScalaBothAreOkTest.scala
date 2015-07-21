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

object JavaDependsOfScalaBothAreOkTest extends testsetup.TestProjectSetup("javaDependsOnScalaBothAreOk") {
  @BeforeClass def setup(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
  }
}

class JavaDependsOfScalaBothAreOkTest {
  import JavaDependsOfScalaBothAreOkTest._

  @Test def shouldCreateProjectFromOkJavaAndSpoilAndFixIt(): Unit = {
    val TypeCMustImplementFoo = 1
    cleanProject()
    import SDTTestUtils._

    val aClass = compilationUnit("main/A.scala")
    val bClass = compilationUnit("main/B.java")
    def rebuildAndCollectProblems(): List[IMarker] = {
      project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
      getProblemMarkers(aClass, bClass)
    }

    step("first compile scala and dependent java classes") {
      val problems = rebuildAndCollectProblems()
      assertTrue("No build error expected, got: " + markersMessages(problems), problems.isEmpty)
    }

    step("then extends java B with abstract A") {
      changeContentOfFile(bClass.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changed_main_B)
      val problems = rebuildAndCollectProblems()
      assertTrue("One build error expected, got: " + markersMessages(problems), problems.length == TypeCMustImplementFoo)
    }

    step("and then give body to 'foo' in abstract A") {
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
  lazy val changed_main_B = """
package main;

public class B extends A {
}
"""
}
