package org.scalaide.core.sbtbuilder

import org.junit.Test
import org.junit.Assert
import org.scalaide.core.testsetup.SDTTestUtils._
import org.scalaide.core.internal.project.ScalaProject
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.JavaCore
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.util.internal.CompilerUtils.ShortScalaVersion
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.eclipse.core.runtime.Path
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.scalaide.util.internal.SettingConverterUtil
import org.eclipse.core.resources.IMarker
import scala.tools.nsc.settings.ScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion
import sbt.ScalaInstance
import org.scalaide.core.internal.project.ScalaModule
import org.scalaide.core.internal.project.ScalaInstallationChoice
import org.scalaide.core.internal.project.LabeledScalaInstallation

class MultiScalaVersionTest {
  // this was deprecated in 2.10, and invalid in 2.11
  // we use this code to show that the project build succeeds, therefore it must be 2.10
  // This might not be robust enough for 2.12
  val sourceCode = "case class InvalidCaseClass" // parameter-less case classes forbidden in 2.11

  @Test // Build using the previous version of the Scala library
  def previousVersionBuildSucceeds() {
    val Seq(p) = createProjects("prev-version-build")
    p.projectSpecificStorage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)

    for (installation <- findPreviousScalaInstallation()) {
      val choice = ScalaInstallationChoice(installation)
      p.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, choice.toString())
      Assert.assertEquals(s"Expected to see the desired choice, found ${p.getDesiredInstallationChoice()}", choice, p.getDesiredInstallationChoice())
      setScalaLibrary(p, installation.library.classJar)
      val ShortScalaVersion(major, minor) = installation.version
      p.projectSpecificStorage.setValue(CompilerSettings.ADDITIONAL_PARAMS, s"-Xsource:$major.$minor")

      p.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)
      val (_, errors) = getErrorMessages(p.underlying).filter(_._1 == IMarker.SEVERITY_ERROR).unzip
      Assert.assertEquals(s"No errors expected, but found: $errors", 0, errors.size)
    }
  }

  private def findPreviousScalaInstallation(): Option[LabeledScalaInstallation] = {
    ScalaInstallation.availableInstallations find { installation =>
      (installation.version, ScalaPlugin.plugin.scalaVer) match {
        case (ShortScalaVersion(_, minor), ShortScalaVersion(_, pluginMinor)) => minor < pluginMinor
        case _ => false
      }
    }
  }

  private def setScalaLibrary(p: ScalaProject, lib: IPath): Unit = {
    val baseClasspath = p.javaProject.getRawClasspath().filter(_.getPath().toPortableString() != ScalaPlugin.plugin.scalaLibId)
    p.javaProject.setRawClasspath(baseClasspath :+ JavaCore.newLibraryEntry(lib, null, null), null)
  }
}