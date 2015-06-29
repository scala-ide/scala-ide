package org.scalaide.core
package sbtbuilder

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.scalaide.core.testsetup.SDTTestUtils

import ScalaJavaDepTicket_1000607Test.compilationUnit
import ScalaJavaDepTicket_1000607Test.project

object ScalaJavaDepTicket_1000607Test extends testsetup.TestProjectSetup("scalajavadepticket1000607")

/**
 * Check the test case from the assembla ticket 1000607.
 * Start from a setup with a compilation error, 'fix' it in the Scala file and check that
 * the incremental compilation correctly recompile the Java file.
 */
class ScalaJavaDepTicket_1000607Test {

  import ScalaJavaDepTicket_1000607Test._

  @Before
  def setupWorkspace(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
  }

  @Test def ticket_1000607(): Unit = {
    val aClass = compilationUnit("ticket_1000607/A.scala")
    val cClass = compilationUnit("ticket_1000607/C.java")

    def getProblemMarkers = SDTTestUtils.getProblemMarkers(aClass, cClass)

    // do a clean build and check the expected error
    cleanProject()
    var problems = getProblemMarkers
    assertEquals("One error expected: " + SDTTestUtils.markersMessages(problems), 1, problems.size)

    // "fix" the scala code
    SDTTestUtils.changeContentOfFile(aClass.getResource().asInstanceOf[IFile], changed_ticket_1000607_A)

    // trigger incremental compile
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

    // and check that the error disappeared
    problems = getProblemMarkers
    assertTrue("Unexpected error: " + SDTTestUtils.markersMessages(problems), problems.isEmpty)
  }

  private def cleanProject(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
  }

  lazy val changed_ticket_1000607_A = """
package ticket_1000607

trait A {
  def foo(s: String): Unit = {}
}

abstract class B extends A
"""

}