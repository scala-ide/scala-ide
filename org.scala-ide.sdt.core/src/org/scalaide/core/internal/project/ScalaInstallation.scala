package org.scalaide.core.internal.project

import scala.tools.nsc.settings.ScalaVersion
import org.eclipse.core.runtime.IPath
import xsbti.compile.ScalaInstance
import org.scalaide.core.ScalaPlugin.plugin
import org.scalaide.util.internal.CompilerUtils._

/** This class represents a valid Scala installation. It encapsulates
 *  a Scala version and paths to the standard Scala jar files:
 *
 *  - scala-library.jar
 *  - scala-compiler.jar
 *  - scala-reflect.jar
 *  - others (actors, swing, etc.)
 */
trait ScalaInstallation {

  /** The version of Scala */
  def version: ScalaVersion

  def compilerJar: IPath

  def libraryJar: IPath

  /** All jars provided by Scala (including the compiler) */
  def allJars: Seq[IPath]

  /** Create an Sbt-compatible ScalaInstance */
  def scalaInstance: ScalaInstance
}

/** The Scala installation installed on the Eclipse platform.
 *
 *  This uses the same classloader as the Scala IDE plugin, since the two versions of Scala
 *  are identical.
 */
class PlatformScalaInstallation extends ScalaInstallation {
  def version: ScalaVersion =
    plugin.scalaVer

  def compilerJar: IPath = {
    // We assume there must be an installed compiler jar, otherwise it's ok to crash here
    plugin.compilerClasses.get
  }

  def libraryJar: IPath = {
    // We assume there must be an installed library jar, otherwise the plugin wouldn't work at all
    plugin.libClasses.get
  }

  def extraJars: Seq[IPath] = {
    Seq(plugin.actorsClasses, plugin.reflectClasses, plugin.swingClasses).flatten
  }

  def allJars: Seq[IPath] = version match {
    case ShortScalaVersion(2, 10) =>
      Seq(libraryJar, compilerJar) ++ extraJars
  }

  def scalaInstance: xsbti.compile.ScalaInstance = {
    // we use the current classloader since this installation is the same as the one we're running in
    val platformLoader = getClass.getClassLoader
    new sbt.ScalaInstance(version.unparse, platformLoader, libraryJar.toFile, compilerJar.toFile, extraJars.map(_.toFile).toList, None)
  }
}

object ScalaInstallation {

  /** Return the Scala installation currently running in Eclipse. */
  def platformInstallation: ScalaInstallation = {
    new PlatformScalaInstallation
  }
}
