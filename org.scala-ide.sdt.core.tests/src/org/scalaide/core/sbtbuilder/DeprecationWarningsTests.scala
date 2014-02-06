package scala.tools.eclipse.sbtbuilder

import scala.tools.eclipse.SettingConverterUtil
import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.util.FileUtils

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.junit.Assert
import org.junit.Test

import scala.language.reflectiveCalls

object deprecationWarningsProject extends TestProjectSetup("builder-deprecation-warnings") {
  // enable deprecation warnings for this project
  val storage = deprecationWarningsProject.project.projectSpecificStorage.asInstanceOf[PropertyStore]
  storage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
  storage.setValue("deprecation", "true")
  storage.save()
}

class DeprecationWarningsTests {
  private type Warnings = Seq[String]

  @Test def deprecationWarningsDoNotAccumulate_1001595() {
    val expectedDeprecationWarnings = Seq("method a in class B is deprecated")

    val unitA = deprecationWarningsProject.compilationUnit("A.scala")
    val warningsAfterFullClean = doBuild(IncrementalProjectBuilder.FULL_BUILD) andGetProblemsOf unitA
    Assert.assertEquals(expectedDeprecationWarnings, warningsAfterFullClean)

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
    Assert.assertEquals(expectedDeprecationWarnings, warningsAfterIncrementalBuild)
  }

  private def doBuild(buildFlag: Int) = new {
    def andGetProblemsOf(unit: ICompilationUnit): Warnings = {
      deprecationWarningsProject.project.underlying.build(buildFlag, new NullProgressMonitor)
      FileUtils.findBuildErrors(unit.getResource()).map(_.getAttribute(IMarker.MESSAGE).toString)
    }
  }
}