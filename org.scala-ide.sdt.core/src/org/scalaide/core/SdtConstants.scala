package org.scalaide.core

object SdtConstants {

  // flags to enable using "-D..=true" vm arguments
  private[core] final val HeadlessProperty = "sdtcore.headless"
  private[core] final val NoTimeoutsProperty = "sdtcore.notimeouts"

  // Eclipse ids

  final val PluginId = "org.scala-ide.sdt.core"
  final val LibraryPluginId = "org.scala-lang.scala-library"
  final val SbtPluginId = "org.scala-ide.sbt.full.library"
  final val SbtCompilerInterfacePluginId = "org.scala-ide.sbt.compiler.interface"
  final val EditorId = "scala.tools.eclipse.ScalaSourceFileEditor"
  final val ScalaPerspectiveId = "org.scala-ide.sdt.core.perspective"

  final val LaunchTypeId = "scala.application"

  // containers
  private final val LaunchId = "org.scala-ide.sdt.launching"
  final val ScalaLibContId = LaunchId + "." + "SCALA_CONTAINER"
  final val ScalaCompilerContId = LaunchId + "." + "SCALA_COMPILER_CONTAINER"

  // project nature
  final val NatureId = PluginId + ".scalanature"

  // marker ids
  final val ProblemMarkerId = PluginId + ".problem"
  final val ClasspathProblemMarkerId = PluginId + ".classpathProblem"
  final val ScalaVersionProblemMarkerId = PluginId + ".scalaVersionProblem"
  final val SettingProblemMarkerId = PluginId + ".settingProblem"
  final val TaskMarkerId = PluginId + ".task"
  /** All Scala error markers. */
  final val ScalaErrorMarkerIds = Set(ClasspathProblemMarkerId, ProblemMarkerId, SettingProblemMarkerId)

  // builder
  final val BuilderId = PluginId + ".scalabuilder"

  // wizards
  private final val WizardPath = PluginId + ".wizards"
  private def wizardId(name: String) = WizardPath + ".new" + name
  final val ClassWizId = wizardId("Class")
  final val TraitWizId = wizardId("Trait")
  final val ObjectWizId = wizardId("Object")
  final val PackageObjectWizId = wizardId("PackageObject")
  final val ApplicationWizId = wizardId("Application")
  final val ProjectWizId = wizardId("Project")
  final val NetProjectWizId = wizardId("NetProject")
  final val ScalaFileCreatorWidId = "org.scalaide.ui.wizards.scalaCreator"


  final val ScalaFileExtn = ".scala"
  final val JavaFileExtn = ".java"

  final val IssueTracker = "https://www.assembla.com/spaces/scala-ide/support/tickets"

}