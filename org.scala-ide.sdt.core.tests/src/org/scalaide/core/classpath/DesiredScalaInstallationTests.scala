package org.scalaide.core.classpath

import scala.tools.nsc.settings.ScalaVersion
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.JavaCore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scalaide.core.IScalaPlugin
import org.eclipse.core.runtime.IPath
import java.io.File
import org.eclipse.core.runtime.IPath
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.core.IScalaProject
import org.scalaide.util.internal.CompilerUtils
import org.scalaide.util.eclipse.EclipseUtils
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.JavaCore
import org.junit.After
import org.eclipse.core.runtime.Path
import org.junit.Test
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.AfterClass
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.ScalaInstallation.extractVersion
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation
import org.scalaide.core.internal.project.ScalaInstallationChoice
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.core.internal.project.LabeledScalaInstallation
import org.scalaide.core.SdtConstants
import org.scalaide.core.testsetup.SDTTestUtils

object DesiredScalaInstallationTests {
  private var projects: List[IScalaProject] = List()

    @AfterClass
  final def deleteProject(): Unit = {
    SDTTestUtils.deleteProjects(projects: _*)
  }
}

class DesiredScalaInstallationTests {
  import DesiredScalaInstallationTests._

  val libraryId = SdtConstants.ScalaLibContId
  def getLibraryJar(project: IScalaProject) = {
    val libraryContainer = JavaCore.getClasspathContainer(new Path(libraryId), project.javaProject)
    libraryContainer.getClasspathEntries() find {e => (""".*scala-library(?:.2\.\d+(?:\.\d*?)?(?:[\.-].*)*)?\.jar""".r).pattern.matcher(e.getPath().toFile().getName()).matches }
  }

  val compilerId = SdtConstants.ScalaCompilerContId
  def getCompilerJar(project: IScalaProject) = {
    val compilerContainer = JavaCore.getClasspathContainer(new Path(compilerId), project.javaProject)
    compilerContainer.getClasspathEntries() find { e => (""".*scala-compiler(?:.2\.\d+(?:\.\d*?)?(?:[\.-].*)*)?\.jar""".r).pattern.matcher(e.getPath().toFile().getName()).matches }
  }

  def anotherBundle(dsi : LabeledScalaInstallation): Option[LabeledScalaInstallation] = ScalaInstallation.availableBundledInstallations.find { si => si != dsi }

  def createProject(): ScalaProject = {
    val project = SDTTestUtils.internalCreateProjectInWorkspace(s"compiler-settings${projects.size}", true)
    projects = project :: projects
    project
  }

  @After
  def deleteProjects(): Unit = {
    EclipseUtils.workspaceRunnableIn(EclipseUtils.workspaceRoot.getWorkspace) { _ =>
      projects foreach { project =>
        project.underlying.delete(true, null)
        (new File(IScalaPlugin().getStateLocation().toFile(), project.underlying.getName + new Path(libraryId).toPortableString() + ".container")).delete()
      }
    }
    projects = List()
  }

  @Test
  def install_for_default_container_is_platform(): Unit = {
    val project = createProject()
    val cc = getLibraryJar(project)
    val v = cc flatMap (lib => extractVersion(lib.getPath()))
    assertTrue(s"The default scala lib container should contain the platform's version. Found ${v.map(_.unparse)}, expected ${platformInstallation.version}", v.isDefined && v.get == platformInstallation.version)
  }

  @Test
  def configured_install_for_default_container_is_platform(): Unit = {
    val project = createProject()
    val dsi = project.effectiveScalaInstallation()
    assertTrue(s"The default scala installation should be the platform. Found ${project.desiredinstallationChoice()}", dsi == ScalaInstallation.platformInstallation)
  }

  @Test
  def at_least_two_available_installs(): Unit = {
    assertTrue("There should be at least two scala installations (current and legacy)", ScalaInstallation.availableInstallations.size >= 2)
  }

  @Test
  def at_least_two_available_bundled_installs(): Unit = {
    assertTrue("There should be at least two bundled scala installations (current and legacy)", ScalaInstallation.availableBundledInstallations.size >= 2)
  }

  @Test
  def legacy_is_not_current(): Unit = {
    val project = createProject()
    val current_dsi  = project.effectiveScalaInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    assertTrue("There should be a bundled Scala installation that is not the platform installation", otherInstallation.isDefined)
  }

  @Test
  def legacy_is_not_binary_compatible(): Unit ={
    val project = createProject()
    val current_dsi  = project.effectiveScalaInstallation()
    val current_choice_before = project.desiredinstallationChoice()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si.version)} // the .version ensures a dynamic choice
    expectedChoice foreach {si => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, si.toString())}
    assertTrue(s"Switching to a former bundle should show a change in desired installation choices, Found ${project.desiredinstallationChoice()}, expected ${expectedChoice.getOrElse("")}", project.desiredinstallationChoice() != current_choice_before)
  }

  @Test
  def change_to_legacy_registers_choice_constant(): Unit ={
    val project = createProject()
    val current_dsi  = project.effectiveScalaInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si)}
    expectedChoice foreach {si => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, si.toString())}
    assertTrue(s"Switching to a former bundle should reflect in configuration, Found ${project.desiredinstallationChoice()}, expected ${expectedChoice.getOrElse("")}", project.desiredinstallationChoice() == expectedChoice.get)
  }

   @Test
  def change_to_legacy_registers_choice_dynamic(): Unit ={
    val project = createProject()
    val current_dsi = project.effectiveScalaInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si.version)}
    expectedChoice foreach {c => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, c.toString())}
    assertTrue(s"Switching to a former bundle should reflect in configuration. Found ${project.desiredinstallationChoice()}, expected ${expectedChoice.getOrElse("")}", project.desiredinstallationChoice() == expectedChoice.get)
  }

  @Test
  def change_to_legacy_registers_constant(): Unit ={
    val project = createProject()
    val current_dsi  = project.effectiveScalaInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si)}
    expectedChoice foreach {si => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, si.toString())}
    assertTrue(s"Switching to a former bundle should reflect in configuration, Found ${project.desiredinstallationChoice()}, expected ${expectedChoice.getOrElse("")}", project.effectiveScalaInstallation() == otherInstallation.get)
  }

  @Test
  def change_to_legacy_registers_dynamic(): Unit ={
    val project = createProject()
    val current_dsi = project.effectiveScalaInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si.version)}
    expectedChoice foreach {c => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, c.toString())}
    assertTrue(s"Switching to a former bundle should reflect in configuration. Found ${project.desiredinstallationChoice()}, expected ${expectedChoice.getOrElse("")}", project.effectiveScalaInstallation() == otherInstallation.get)
  }

  @Test
  def change_to_legacy_registers_on_classpath(): Unit ={
    val project = createProject()
    val current_dsi = project.effectiveScalaInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si.version)}
    expectedChoice foreach {c => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, c.toString())}

    val libraryPath = getLibraryJar(project) map (_.getPath())
    val newVersion = libraryPath flatMap (ScalaInstallation.extractVersion(_))
    assertTrue(s"Switching to a former bundle should show that bundle's version on the library classpath Container. Found ${newVersion map {_.unparse}}. Expected ${otherInstallation.map(_.version)}", newVersion == otherInstallation.map{_.version})
  }

  @Test
  def change_to_legacy_registers_on_compiler_classpath(): Unit ={
    val project = createProject()
    val current_dsi = project.effectiveScalaInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si.version)}
    expectedChoice foreach {c => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, c.toString())}

    val compilerPath = getCompilerJar(project) map (_.getPath())
    val newVersion = compilerPath flatMap (ScalaInstallation.extractVersion(_))
    assertTrue(s"Switching to a former bundle should show that bundle's version on the compiler classpath Container. Found ${newVersion map {_.unparse}}. Expected ${otherInstallation.map(_.version)}", newVersion == otherInstallation.map{_.version})
  }

}
