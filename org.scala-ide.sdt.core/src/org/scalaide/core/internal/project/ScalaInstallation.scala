package org.scalaide.core.internal.project

import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import scala.tools.nsc.settings.ScalaVersion

import org.eclipse.core.runtime.FileLocator
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Platform
import org.osgi.framework.Bundle
import org.osgi.framework.Version
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.ScalaPlugin.plugin
import org.scalaide.util.internal.CompilerUtils.ShortScalaVersion
import org.scalaide.util.internal.eclipse.OSGiUtils

import xsbti.compile.ScalaInstance

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

  override def toString() =
    s"Scala $version: ${allJars.mkString(", ")})"
}

/** The Scala installation installed on the Eclipse platform.
 *
 *  This uses the same classloader as the Scala IDE plugin, since the two versions of Scala
 *  are identical.
 *
 *  TODO: this should be replaced by using the right version from ScalaInstallation.multiBundleInstallations.
 */
class PlatformScalaInstallation extends ScalaInstallation {
  override def version: ScalaVersion =
    plugin.scalaVer

  override def compilerJar: IPath = {
    // We assume there must be an installed compiler jar, otherwise it's ok to crash here
    plugin.compilerClasses.get
  }

  override def libraryJar: IPath = {
    // We assume there must be an installed library jar, otherwise the plugin wouldn't work at all
    plugin.libClasses.get
  }

  def extraJars: Seq[IPath] = {
    Seq(plugin.actorsClasses, plugin.reflectClasses, plugin.swingClasses).flatten
  }

  override def allJars: Seq[IPath] = version match {
    case ShortScalaVersion(2, 10) =>
      Seq(libraryJar, compilerJar) ++ extraJars
  }

  override def scalaInstance: xsbti.compile.ScalaInstance = {
    // we use the current classloader since this installation is the same as the one we're running in
    val platformLoader = getClass.getClassLoader
    // TODO: new one everytime?
    new sbt.ScalaInstance(version.unparse, platformLoader, libraryJar.toFile, compilerJar.toFile, extraJars.map(_.toFile).toList, None)
  }
}

/** Represent a version of Scala installed as a bundle containing the necessary jars.
 */
case class BundledScalaInstallation(
  override val version: ScalaVersion,
  bundle: Bundle,
  override val libraryJar: IPath,
  override val compilerJar: IPath) extends ScalaInstallation {

  import BundledScalaInstallation._

  lazy val extraJars =
    Seq(
      OSGiUtils.pathInBundle(bundle, ScalaReflectPath),
      OSGiUtils.pathInBundle(bundle, ScalaActorPath),
      OSGiUtils.pathInBundle(bundle, ScalaSwingPath)).flatten

  override lazy val allJars: Seq[IPath] =
    libraryJar +: compilerJar +: extraJars

  override def scalaInstance: ScalaInstance = {
    // TODO: copied from PlatformScalaInstallation, do we need something different?
    // we use the current classloader since this installation is the same as the one we're running in
    val platformLoader = getClass.getClassLoader
    // TODO: new one everytime?
    new sbt.ScalaInstance(version.unparse, platformLoader, libraryJar.toFile, compilerJar.toFile, extraJars.map(_.toFile).toList, None)
  }
}

object BundledScalaInstallation {

  val ScalaLibraryPath = "target/jars/scala-library.jar"
  val ScalaCompilerPath = "target/jars/scala-compiler.jar"
  val ScalaReflectPath = "target/jars/scala-reflect.jar"
  val ScalaActorPath = "target/jars/scala-actor.jar"
  val ScalaSwingPath = "target/jars/scala-swing.jar"

  def apply(bundle: Bundle): Option[BundledScalaInstallation] = {
    for {
      scalaLibrary <- OSGiUtils.pathInBundle(bundle, ScalaLibraryPath)
      version <- ScalaInstallation.extractVersion(scalaLibrary)
      scalaCompiler <- OSGiUtils.pathInBundle(bundle, ScalaCompilerPath)
    } yield BundledScalaInstallation(version, bundle, scalaLibrary, scalaCompiler)
  }

  val ScalaBundleJarsRegex = "org\\.scala-ide\\.scala[0-9]+\\.jars".r

  /** Find and return the complete bundled Scala installations.
   */
  def detectBundledInstallations(): List[BundledScalaInstallation] = {
    // find the bundles with the right pattern
    val matchingBundles: List[Bundle] =
      ScalaPlugin.plugin.getBundle().getBundleContext().getBundles().to[List]
        .filter { b => ScalaBundleJarsRegex.unapplySeq(b.getSymbolicName()).isDefined }

    matchingBundles.flatMap(BundledScalaInstallation(_))
  }
}

/** Represent a version of Scala installed as a set of bundles, each bundle with an identical version.
 */
case class MultiBundleScalaInstallation(
  override val version: ScalaVersion,
  libraryBundle: Bundle,
  override val compilerJar: IPath) extends ScalaInstallation {

  import MultiBundleScalaInstallation._

  override lazy val libraryJar = bundlePath(libraryBundle)

  lazy val extraJars = {
    val libraryBundleVersion = libraryBundle.getVersion()

    Seq(
      findBundlePath(ScalaReflectBundleId, libraryBundleVersion),
      findBundlePath(ScalaActorsBundleId, libraryBundleVersion),
      findBundlePath(ScalaSwingBundleId, libraryBundleVersion)).flatten
  }

  override def allJars: Seq[IPath] =
    libraryJar +: compilerJar +: extraJars

  override def scalaInstance: ScalaInstance = {
    // TODO: copied from PlatformScalaInstallation, do we need something different?
    // we use the current classloader since this installation is the same as the one we're running in
    val platformLoader = getClass.getClassLoader
    // TODO: new one everytime?
    new sbt.ScalaInstance(version.unparse, platformLoader, libraryJar.toFile, compilerJar.toFile, extraJars.map(_.toFile).toList, None)
  }
}

object MultiBundleScalaInstallation {

  val ScalaLibraryBundleId = "org.scala-lang.scala-library"
  val ScalaCompilerBundleId = "org.scala-lang.scala-compiler"
  val ScalaSwingBundleId = "org.scala-lang.scala-swing"
  val ScalaReflectBundleId = "org.scala-lang.scala-reflect"
  val ScalaActorsBundleId = "org.scala-lang.scala-actors"
  val ScalaXmlBundleId = "org.scala-lang.modules.scala-xml"
  val ScalaParserCombinatorsBundleId = "org.scala-lang.modules.scala-parser-combinators"

  private def bundlePath(bundle: Bundle) =
    Path.fromOSString(FileLocator.getBundleFile(bundle).getAbsolutePath())

  private def findBundle(bundleId: String, version: Version): Option[Bundle] = {
    Option(Platform.getBundles(bundleId, null)).getOrElse(Array()).to[List].find(_.getVersion() == version)
  }

  private def findBundlePath(bundleId: String, version: Version): Option[IPath] =
    findBundle(bundleId, version).map(bundlePath)

  def apply(libraryBundle: Bundle): Option[MultiBundleScalaInstallation] = {
    val libraryBundleVersion = libraryBundle.getVersion()

    for {
      version <- ScalaInstallation.extractVersion(bundlePath(libraryBundle))
      compilerBundle <- findBundle(ScalaCompilerBundleId, libraryBundleVersion)
    } yield MultiBundleScalaInstallation(version, libraryBundle, bundlePath(compilerBundle))
  }

  def detectInstallations(): List[MultiBundleScalaInstallation] = {

    val scalaLibraryBundles = Platform.getBundles(ScalaLibraryBundleId, null).to[List]

    scalaLibraryBundles.flatMap(MultiBundleScalaInstallation(_))
  }
}

object ScalaInstallation {

  /** Return the Scala installation currently running in Eclipse. */
  lazy val platformInstallation: ScalaInstallation = {
    new PlatformScalaInstallation
  }

  lazy val bundledInstallations: List[ScalaInstallation] =
    BundledScalaInstallation.detectBundledInstallations()

  lazy val multiBundleInstallations: List[ScalaInstallation] =
    MultiBundleScalaInstallation.detectInstallations()

  lazy val availableInstallations: List[ScalaInstallation] = {
    multiBundleInstallations ++ bundledInstallations
  }

  val LibraryPropertiesPath = "library.properties"

  def extractVersion(scalaLibrary: IPath): Option[ScalaVersion] = {
    val zipFile = new ZipFile(scalaLibrary.toFile())
    try {
      def getVersion(propertiesEntry: ZipEntry) = {
        val properties = new Properties()
        properties.load(zipFile.getInputStream(propertiesEntry))
        Option(properties.getProperty("version.number"))
      }

      for {
        propertiesEntry <- Option(zipFile.getEntry(LibraryPropertiesPath))
        version <- getVersion(propertiesEntry)
      } yield ScalaVersion(version)
    } finally {
      zipFile.close()
    }

  }

}
