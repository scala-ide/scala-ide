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
  final val ScalaLibContId = "org.scala-ide.sdt.launching.SCALA_CONTAINER"
  final val ScalaCompilerContId = "org.scala-ide.sdt.launching.SCALA_COMPILER_CONTAINER"

  // project nature
  final val NatureId = "org.scala-ide.sdt.core.scalanature"

  // marker ids
  final val ProblemMarkerId = "org.scala-ide.sdt.core.problem"
  final val ClasspathProblemMarkerId = "org.scala-ide.sdt.core.classpathProblem"
  final val ScalaVersionProblemMarkerId = "org.scala-ide.sdt.core.scalaVersionProblem"
  final val SettingProblemMarkerId = "org.scala-ide.sdt.core.settingProblem"
  final val TaskMarkerId = "org.scala-ide.sdt.core.task"
  /** All Scala error markers. */
  final val ScalaErrorMarkerIds = Set(ClasspathProblemMarkerId, ProblemMarkerId, SettingProblemMarkerId)

  // builder
  final val BuilderId = "org.scala-ide.sdt.core.scalabuilder"

  // wizards
  final val ClassWizId = "org.scala-ide.sdt.core.wizards.newClass"
  final val TraitWizId = "org.scala-ide.sdt.core.wizards.newTrait"
  final val ObjectWizId = "org.scala-ide.sdt.core.wizards.newObject"
  final val PackageObjectWizId = "org.scala-ide.sdt.core.wizards.newPackageObject"
  final val ApplicationWizId = "org.scala-ide.sdt.core.wizards.newApplication"
  final val ProjectWizId = "org.scala-ide.sdt.core.wizards.newProject"
  final val NetProjectWizId = "org.scala-ide.sdt.core.wizards.newNetProject"
  final val ScalaFileCreatorWidId = "org.scalaide.ui.wizards.scalaCreator"

  // file extensions
  final val ScalaFileExtn = ".scala"
  final val JavaFileExtn = ".java"

  final val IssueTracker = "https://www.assembla.com/spaces/scala-ide/support/tickets"
  final val SveltoHomepage = "https://github.com/dragos/svelto"

}
