package org.scalaide.core.sbtbuilder

import org.junit.Test
import org.junit.Assert
import org.scalaide.core.testsetup.SDTTestUtils._
import org.scalaide.core.IScalaProject
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.JavaCore
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.project.ScalaInstallation.availableInstallations
import org.scalaide.util.internal.CompilerUtils.ShortScalaVersion
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.scalaide.util.internal.SettingConverterUtil
import org.eclipse.core.resources.IMarker
import org.scalaide.core.internal.project.ScalaInstallationChoice
import org.scalaide.core.internal.project.LabeledScalaInstallation
import org.scalaide.core.SdtConstants
import org.eclipse.core.runtime.NullProgressMonitor

class MultiScalaVersionTest {
  // this was deprecated in 2.10, and invalid in 2.11
  // we use this code to show that the project build succeeds, therefore it must be 2.10
  // This might not be robust enough for 2.12
  val sourceCode = "case class InvalidCaseClass" // parameter-less case classes forbidden in 2.11

  @Test // Build using the previous version of the Scala library
  def previousVersionBuildSucceeds(): Unit = {
    val Seq(p) = internalCreateProjects("prev-version-build")(new NullProgressMonitor)
    val projectSpecificStorage = p.projectSpecificStorage

    projectSpecificStorage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)

    for (installation <- findPreviousScalaInstallation()) {
      val choice = ScalaInstallationChoice(installation)
      projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, choice.toString())
      Assert.assertEquals(s"Expected to see the desired choice, found ${p.desiredinstallationChoice()}", choice, p.desiredinstallationChoice())
      setScalaLibrary(p, installation.library.classJar)
      val ShortScalaVersion(major, minor) = installation.version
      projectSpecificStorage.setValue(CompilerSettings.ADDITIONAL_PARAMS, s"-Xsource:$major.$minor")

      p.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)
      val (_, errors) = getErrorMessages(p.underlying).filter(_._1 == IMarker.SEVERITY_ERROR).unzip
      Assert.assertEquals(s"No errors expected, but found: $errors", 0, errors.size)
    }
  }

  private def findPreviousScalaInstallation(): Option[LabeledScalaInstallation] = {
    availableInstallations find { installation =>
      (installation.version, IScalaPlugin().scalaVersion) match {
        case (ShortScalaVersion(_, minor), ShortScalaVersion(_, pluginMinor)) => minor < pluginMinor
        case _ => false
      }
    }
  }

  private def setScalaLibrary(p: IScalaProject, lib: IPath): Unit = {
    val baseClasspath = p.javaProject.getRawClasspath().filter(_.getPath().toPortableString() != SdtConstants.ScalaLibContId)
    p.javaProject.setRawClasspath(baseClasspath :+ JavaCore.newLibraryEntry(lib, null, null), null)
  }
}
