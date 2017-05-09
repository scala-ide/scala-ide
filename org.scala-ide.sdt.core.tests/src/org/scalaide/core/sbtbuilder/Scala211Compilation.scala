package org.scalaide.core
package sbtbuilder

import java.io.File

import scala.tools.nsc.settings.SpecificScalaVersion

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.util.when

object Scala211Compilation extends testsetup.TestProjectSetup("scala211compilation")

class Scala211Compilation {
  import Scala211Compilation._
  import org.scalaide.core.testsetup.SDTTestUtils._

  @Before
  def setupWorkspace(): Unit = {
    enableAutoBuild(false)
  }

  private def getProblemMarkersFor(units: ICompilationUnit*): List[IMarker] = {
    units.toList.flatMap(SDTTestUtils.findProblemMarkers)
  }

  private def cleanProject(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
  }

  private def getProblemMarkers = getProblemMarkersFor(compilationUnit("test/Test211.scala"))

  @Test def testSimpleScalaDep(): Unit = {
    when("initialize project") `then` "there is no error in project and zinc creates .cache-main file" in {
      cleanProject()
      val problems = getProblemMarkers
      val version = project.effectiveScalaInstallation.version match {
        case SpecificScalaVersion(major, minor, _, _) => major + "." + minor
        case t => t.unparse
      }
      val root = project.javaProject.getResource.getLocation.makeAbsolute.toFile
      val cacheMain = new File(root.getAbsolutePath + File.separator + ".cache-main")
      assertTrue("Expected 2.11 scala installation, found: " + project.effectiveScalaInstallation.version.unparse, version == "2.11")
      assertTrue("No build errors expected, found: " + markersMessages(problems), problems.isEmpty)
      assertTrue("Expected .cache-main but not found", cacheMain.exists)
    }
  }
}
