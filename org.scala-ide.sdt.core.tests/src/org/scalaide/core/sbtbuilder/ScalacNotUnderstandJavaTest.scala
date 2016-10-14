package org.scalaide.core
package sbtbuilder

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert._
import org.junit.BeforeClass
import org.junit.Test
import org.scalaide.core.testsetup.SDTTestUtils

object ScalacNotUnderstandJavaTest extends testsetup.TestProjectSetup("scalacnotunderstandjava") {
  @BeforeClass def setup(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
  }
}

class ScalacNotUnderstandJavaTest {
  import ScalacNotUnderstandJavaTest._
  import testsetup.SDTTestUtils._

  @Test def shouldContinueJavaCompilationEvenWhenScalacDoesNotUnderstandJavaFile(): Unit = {
    val JavaCannotConvertFromStringToInt = 1
    cleanProject()

    val SScalaCU = compilationUnit("test/S.scala")
    val JJavaCU = compilationUnit("test/J.java")
    def findProblems() = getProblemMarkers(JJavaCU, SScalaCU)

    rebuild()
    val problems0 = findProblems()
    assertTrue("No build error expected, got: " + markersMessages(problems0), problems0.isEmpty)

    val originalSScala = slurpAndClose(project.underlying.getFile("src/test/S.scala").getContents)
    changeContentOfFile(SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedSScala)
    rebuild()
    val problems1 = findProblems()
    assertTrue("One build error expected, got: " + markersMessages(problems1), problems1.length == JavaCannotConvertFromStringToInt)

    changeContentOfFile(SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalSScala)
    rebuild()
    val problems2 = findProblems()
    assertTrue("No build error expected, got: " + markersMessages(problems2), problems2.isEmpty)
  }

  private def rebuild(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

  lazy private val changedSScala = """
package test

class S {
  def foo = "51"
}
"""
}
