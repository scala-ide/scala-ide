package org.scalaide.ui.internal.editor.sbt

import java.io.File

import scala.tools.nsc.Settings
import scala.tools.nsc.settings.ScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion

import org.scalaide.core.internal.compiler.PresentationCompilerProxy
import org.scalaide.core.internal.project.ScalaInstallation

object SbtPresentationCompiler {

  val compiler = new PresentationCompilerProxy("Sbt compiler", getSettings _)

  def getSettings: Settings = {
    val settings = new Settings
    val projects = Seq("actions", "api", "cache", "classfile", "classpath",
      "collections", "command", "compiler-integration", "completion", "control",
      "incremental-compiler", "interface", "io", "ivy", "launcher", "logging",
      "main", "main-settings", "persist", "process", "relation", "run", "sbt", "tasks")

    // TODO Fix hard-coded paths
    val jars = for (p <- projects) yield s"/Users/dragos/.ivy2/cache/org.scala-sbt/$p/jars/$p-0.13.7.jar"
    settings.classpath.value = jars.mkString(File.pathSeparator)

    settings.usejavacp.value = true
    settings.YpresentationDebug.value = true
    settings.YpresentationVerbose.value = true
    settings.debug.value = true
    settings.Ymacronoexpand.value = true
    val install = ScalaInstallation.availableInstallations.find {
      _.version match {
        case SpecificScalaVersion(2, 10, _, _) => true
        case _                                 => false
      }
    }

    if (install.isDefined) {
      settings.bootclasspath.value = install.get.allJars.map(_.classJar).mkString(File.pathSeparator)
    }
    settings.source.value = ScalaVersion("2.10")

    settings
  }
}