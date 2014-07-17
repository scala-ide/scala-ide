package org.scalaide.core.classpath

import scala.tools.nsc.settings.ScalaVersion
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.JavaCore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scalaide.core.EclipseUserSimulator
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.util.internal.CompilerUtils
import org.scalaide.util.internal.eclipse.EclipseUtils
import org.eclipse.core.runtime.IPath
import java.io.File
import org.eclipse.core.runtime.IPath
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.util.internal.CompilerUtils
import org.scalaide.core.EclipseUserSimulator
import org.scalaide.util.internal.eclipse.EclipseUtils
import org.eclipse.jdt.core.IClasspathContainer
import org.scalaide.core.ScalaPlugin
import org.eclipse.jdt.core.JavaCore
import org.junit.After
import org.eclipse.core.runtime.Path
import org.junit.Test
import org.scalaide.core.internal.project.ScalaInstallation._
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.AfterClass
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.ScalaInstallationChoice
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.core.internal.project.LabeledScalaInstallation

object DesiredScalaInstallationTests {
  private val simulator = new EclipseUserSimulator
  private var projects: List[ScalaProject] = List()

    @AfterClass
  final def deleteProject(): Unit = {
    EclipseUtils.workspaceRunnableIn(ScalaPlugin.plugin.workspaceRoot.getWorkspace()) { _ =>
      projects foreach (_.underlying.delete(/* force */ true, new NullProgressMonitor))
    }
  }
}

class DesiredScalaInstallationTests {
  import DesiredScalaInstallationTests._

  val libraryId = ScalaPlugin.plugin.scalaLibId
  def getLibraryJar(project: ScalaProject) = {
    val libraryContainer = JavaCore.getClasspathContainer(new Path(libraryId), project.javaProject)
    libraryContainer.getClasspathEntries() find {e => (""".*scala-library(?:.2\.\d+(?:\.\d*?)?(?:[\.-].*)*)?\.jar""".r).pattern.matcher(e.getPath().toFile().getName()).matches }
  }

  val compilerId = ScalaPlugin.plugin.scalaCompilerId
  def getCompilerJar(project: ScalaProject) = {
    val compilerContainer = JavaCore.getClasspathContainer(new Path(compilerId), project.javaProject)
    compilerContainer.getClasspathEntries() find { e => (""".*scala-compiler(?:.2\.\d+(?:\.\d*?)?(?:[\.-].*)*)?\.jar""".r).pattern.matcher(e.getPath().toFile().getName()).matches }
  }

  def anotherBundle(dsi : LabeledScalaInstallation): Option[LabeledScalaInstallation] = ScalaInstallation.availableBundledInstallations.find { si => si != dsi }

  def createProject(): ScalaProject = {
    import ClasspathContainersTests.simulator
    val project = simulator.createProjectInWorkspace(s"compiler-settings${projects.size}", true)
    projects = project :: projects
    project
  }


  @After
  def deleteProjects() {
    EclipseUtils.workspaceRunnableIn(ScalaPlugin.plugin.workspaceRoot.getWorkspace) { _ =>
      projects foreach { project =>
        project.underlying.delete(true, null)
        (new File(ScalaPlugin.plugin.getStateLocation().toFile(), project.underlying.getName + new Path(libraryId).toPortableString() + ".container")).delete()
      }
    }
    projects = List()
  }

  @Test
  def install_for_default_container_is_platform() {
    val project = createProject()
    val cc = getLibraryJar(project)
    val v = cc flatMap (lib => extractVersion(lib.getPath()))
    assertTrue(s"The default scala lib container should contain the platform's version. Found ${v.map(_.unparse)}, expected ${platformInstallation.version}", v.isDefined && v.get == platformInstallation.version)
  }

  @Test
  def configured_install_for_default_container_is_platform() {
    val project = createProject()
    val dsi = project.getDesiredInstallation()
    assertTrue(s"The default scala installation should be the platform. Found ${project.getDesiredInstallationChoice()}", dsi == ScalaInstallation.platformInstallation)
  }

  @Test
  def at_least_two_available_installs() {
    assertTrue("There should be at least two scala installations (current and legacy)", ScalaInstallation.availableInstallations.size >= 2)
  }

  @Test
  def at_least_two_available_bundled_installs() {
    assertTrue("There should be at least two bundled scala installations (current and legacy)", ScalaInstallation.availableBundledInstallations.size >= 2)
  }

  @Test
  def legacy_is_not_current() {
    val project = createProject()
    val current_dsi  = project.getDesiredInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    assertTrue("There should be a bundled Scala installation that is not the platform installation", otherInstallation.isDefined)
  }

  @Test
  def legacy_is_not_binary_compatible(){
    val project = createProject()
    val current_dsi  = project.getDesiredInstallation()
    val current_choice_before = project.getDesiredInstallationChoice()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si.version)} // the .version ensures a dynamic choice
    expectedChoice foreach {si => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, si.toString())}
    assertTrue(s"Switching to a former bundle should show a change in desired installation choices, Found ${project.getDesiredInstallationChoice()}, expected ${expectedChoice.getOrElse("")}", project.getDesiredInstallationChoice() != current_choice_before)
  }

  @Test
  def change_to_legacy_registers_choice_constant(){
    val project = createProject()
    val current_dsi  = project.getDesiredInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si)}
    expectedChoice foreach {si => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, si.toString())}
    assertTrue(s"Switching to a former bundle should reflect in configuration, Found ${project.getDesiredInstallationChoice()}, expected ${expectedChoice.getOrElse("")}", project.getDesiredInstallationChoice() == expectedChoice.get)
  }

   @Test
  def change_to_legacy_registers_choice_dynamic(){
    val project = createProject()
    val current_dsi = project.getDesiredInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si.version)}
    expectedChoice foreach {c => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, c.toString())}
    assertTrue(s"Switching to a former bundle should reflect in configuration. Found ${project.getDesiredInstallationChoice()}, expected ${expectedChoice.getOrElse("")}", project.getDesiredInstallationChoice() == expectedChoice.get)
  }

  @Test
  def change_to_legacy_registers_constant(){
    val project = createProject()
    val current_dsi  = project.getDesiredInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si)}
    expectedChoice foreach {si => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, si.toString())}
    assertTrue(s"Switching to a former bundle should reflect in configuration, Found ${project.getDesiredInstallationChoice()}, expected ${expectedChoice.getOrElse("")}", project.getDesiredInstallation() == otherInstallation.get)
  }

  @Test
  def change_to_legacy_registers_dynamic(){
    val project = createProject()
    val current_dsi = project.getDesiredInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si.version)}
    expectedChoice foreach {c => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, c.toString())}
    assertTrue(s"Switching to a former bundle should reflect in configuration. Found ${project.getDesiredInstallationChoice()}, expected ${expectedChoice.getOrElse("")}", project.getDesiredInstallation() == otherInstallation.get)
  }

  @Test
  def change_to_legacy_registers_on_classpath(){
    val project = createProject()
    val current_dsi = project.getDesiredInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si.version)}
    expectedChoice foreach {c => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, c.toString())}

    val libraryPath = getLibraryJar(project) map (_.getPath())
    val newVersion = libraryPath flatMap (ScalaInstallation.extractVersion(_))
    assertTrue(s"Switching to a former bundle should show that bundle's version on the library classpath Container. Found ${newVersion map {_.unparse}}. Expected ${otherInstallation.map(_.version)}", newVersion == otherInstallation.map{_.version})
  }

  @Test
  def change_to_legacy_registers_on_compiler_classpath(){
    val project = createProject()
    val current_dsi = project.getDesiredInstallation()
    val otherInstallation = anotherBundle(current_dsi)
    val expectedChoice = otherInstallation map {si => ScalaInstallationChoice(si.version)}
    expectedChoice foreach {c => project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, c.toString())}

    val compilerPath = getCompilerJar(project) map (_.getPath())
    val newVersion = compilerPath flatMap (ScalaInstallation.extractVersion(_))
    assertTrue(s"Switching to a former bundle should show that bundle's version on the compiler classpath Container. Found ${newVersion map {_.unparse}}. Expected ${otherInstallation.map(_.version)}", newVersion == otherInstallation.map{_.version})
  }

}
