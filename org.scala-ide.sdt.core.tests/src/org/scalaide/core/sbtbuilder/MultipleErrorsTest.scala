package org.scalaide.core
package sbtbuilder

import org.junit.Before
import org.junit.Test
import org.junit.Assert
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder

import testsetup.SDTTestUtils

object MultipleErrorsTest extends testsetup.TestProjectSetup("builder-errors")

class MultipleErrorsTest {
  import MultipleErrorsTest._

  @Before
  def setupWorkspace(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
  }

  @Test def test1000735(): Unit = {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    val units = compilationUnits("test/Foo.scala")
    val errors = units.flatMap(SDTTestUtils.findProblemMarkers)

    Assert.assertTrue("Expected one error message, got: " + errors.length, errors.length == 1)
  }
}