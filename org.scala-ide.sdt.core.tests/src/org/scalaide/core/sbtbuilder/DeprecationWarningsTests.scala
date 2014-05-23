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

object deprecationWarningsProject extends TestProjectSetup("builder-deprecation-warnings")

class DeprecationWarningsTests {
  private type Warnings = Seq[String]

  private  def initDeprecationSetting() {
    // enable deprecation warnings for this project
    val storage = deprecationWarningsProject.project.projectSpecificStorage.asInstanceOf[PropertyStore]
    storage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
    storage.setValue("deprecation", "true")
    storage.save()
  }

  @Test def deprecationWarningsDoNotAccumulate_1001595() {
    val expectedDeprecationWarnings = Seq("method a in class B is deprecated")
    initDeprecationSetting()

    val unitA = deprecationWarningsProject.compilationUnit("A.scala")
    val warningsAfterFullClean = doBuild(IncrementalProjectBuilder.FULL_BUILD) andGetProblemsOf unitA
    Assert.assertEquals("First compilation should show one deprecation warning", expectedDeprecationWarnings, warningsAfterFullClean)

    // let's modify the compilation unit B, which is referenced by A
    val unitB = deprecationWarningsProject.compilationUnit("B.scala")
    val newContentB = """
      |class B {
      |  @deprecated
      |  var a = 2
      |
      |  var c = 2
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