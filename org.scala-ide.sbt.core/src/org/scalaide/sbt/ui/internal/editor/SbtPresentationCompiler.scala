package org.scalaide.ui.internal.editor.sbt

import java.io.File

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.tools.nsc.Settings
import scala.tools.nsc.settings.ScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion

import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.compiler.PresentationCompilerProxy
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.sbt.core.SbtBuild
import org.scalaide.sbt.util.SourceUtils

import akka.stream.ActorMaterializer

class SbtPresentationCompiler(project: IScalaProject, sbtBuild: SbtBuild) {

  val compiler = new PresentationCompilerProxy("Sbt compiler", mkSettings _)

  private def mkSettings: Settings = {
    import sbtBuild.system
    import SourceUtils._
    implicit val m = ActorMaterializer()

    val structure = Await.result(sbtBuild.watchBuild().firstFuture, Duration.Inf)
    val cp = structure.buildsData.head.classpath

    val s = new Settings
    s.classpath.value = cp.mkString(File.pathSeparator)
    s.usejavacp.value = true
    s.YpresentationDebug.value = true
    s.YpresentationVerbose.value = true
    s.debug.value = true
    s.Ymacronoexpand.value = true
    val install = ScalaInstallation.availableInstallations.find {
      _.version match {
        case SpecificScalaVersion(2, 10, _, _) => true
        case _                                 => false
      }
    }

    if (install.isDefined) {
      s.bootclasspath.value = install.get.allJars.map(_.classJar).mkString(File.pathSeparator)
    }
    s.source.value = ScalaVersion("2.10")

    s
  }
}
