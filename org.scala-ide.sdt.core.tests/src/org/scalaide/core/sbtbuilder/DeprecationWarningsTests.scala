package org.scalaide.core
package sbtbuilder

import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.ui.internal.preferences.PropertyStore
import testsetup.SDTTestUtils
import testsetup.TestProjectSetup
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.junit.Assert
import org.junit.Test
import scala.language.reflectiveCalls
import org.scalaide.util.internal.eclipse.FileUtils
import org.junit.Before

object deprecationWarningsProject extends TestProjectSetup("builder-deprecation-warnings") {

  def initSettings() {
    // enable deprecation warnings for this project
    val storage = deprecationWarningsProject.project.projectSpecificStorage.asInstanceOf[PropertyStore]
    storage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
    storage.setValue("deprecation", true)
    storage.setValue("nameHashing", true)
    storage.save()
  }

  val originalContentB =
    """|package test
       |
       |class B {
       |  @deprecated
       |  def a = 2
       |}""".stripMargin
}

class DeprecationWarningsTests {
  private type Warnings = Seq[String]

  @Before
  def prepareTest() {
    import deprecationWarningsProject._
    initSettings()
    val unitB = deprecationWarningsProject.compilationUnit("test/B.scala")
    SDTTestUtils.changeContentOfFile(unitB.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalContentB)
  }

  @Test def deprecationWarningsDoNotAccumulate_1001595() {
    val expectedDeprecationWarnings = Seq("method a in class B is deprecated")

    val unitA = deprecationWarningsProject.compilationUnit("test/A.scala")
    val warningsAfterFullClean = doBuild(IncrementalProjectBuilder.FULL_BUILD) andGetProblemsOf unitA
    Assert.assertEquals("First compilation should show one deprecation warning", expectedDeprecationWarnings, warningsAfterFullClean)

    // let's modify the compilation unit B, which is referenced by A
    val unitB = deprecationWarningsProject.compilationUnit("test/B.scala")
    val newContentB = """
      |package test
      |
      |class B {
      |  @deprecated
      |  def a = 2
      |
      |  def c(x: Int) = x
      |}""".stripMargin
    SDTTestUtils.changeContentOfFile(unitB.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], newContentB)

    val warningsAfterIncrementalBuild = doBuild(IncrementalProjectBuilder.INCREMENTAL_BUILD) andGetProblemsOf unitA
    Assert.assertEquals("Second compilation should show only one deprecation warning", expectedDeprecationWarnings, warningsAfterIncrementalBuild)
  }

  @Test def deprecationWarningsAreNotLost() {
    val expectedDeprecationWarnings = Seq("method a in class B is deprecated")

    val unitA = deprecationWarningsProject.compilationUnit("test/A.scala")
    deprecationWarningsProject.cleanProject()
    val warningsAfterFullClean = doBuild(IncrementalProjectBuilder.FULL_BUILD) andGetProblemsOf unitA
    Assert.assertEquals("First compilation should show one deprecation warning", expectedDeprecationWarnings, warningsAfterFullClean)

    // let's modify the compilation unit B, which is referenced by A
    // but this change will NOT trigger compilation of A (name hashing should ensure that, since we add an unused member)
    val unitB = deprecationWarningsProject.compilationUnit("test/B.scala")
    val newContentB = """
      |package test
      |
      |class B {
      |  @deprecated
      |  def a = 2
      |
      |  def c = 2
      |}""".stripMargin
    SDTTestUtils.changeContentOfFile(unitB.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], newContentB)

    val warningsAfterIncrementalBuild = doBuild(IncrementalProjectBuilder.INCREMENTAL_BUILD) andGetProblemsOf unitA
    Assert.assertEquals("Second compilation should show only one deprecation warning", expectedDeprecationWarnings, warningsAfterIncrementalBuild)
  }


  private def doBuild(buildFlag: Int) = new {
    def andGetProblemsOf(unit: ICompilationUnit): Warnings = {
      deprecationWarningsProject.project.underlying.build(buildFlag, new NullProgressMonitor)
      FileUtils.findBuildErrors(unit.getResource()).map(_.getAttribute(IMarker.MESSAGE).toString)
    }
  }
}