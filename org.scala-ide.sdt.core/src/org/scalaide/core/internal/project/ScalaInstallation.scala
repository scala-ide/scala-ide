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
import java.net.URLClassLoader
import scala.tools.nsc.settings.SpecificScalaVersion
import org.scalaide.util.internal.eclipse.EclipseUtils

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

  def compiler: ScalaModule

  def library: ScalaModule

  def extraJars: Seq[ScalaModule]

  /** All jars provided by Scala (including the compiler) */
  def allJars: Seq[ScalaModule] =
    library +: compiler +: extraJars

  /** Create an Sbt-compatible ScalaInstance */
  def scalaInstance: ScalaInstance

  override def toString() =
    s"Scala $version: ${allJars.mkString(", ")})"
}

case class ScalaModule(classJar: IPath, sourceJar: Option[IPath])

object ScalaModule {
  def apply(bundleId: String, classJar: IPath): ScalaModule = {
    ScalaModule(classJar, EclipseUtils.computeSourcePath(bundleId, classJar))
  }
}

/** Represent a version of Scala installed as a bundle containing the necessary jars.
 */
case class BundledScalaInstallation(
  override val version: ScalaVersion,
  bundle: Bundle,
  override val library: ScalaModule,
  override val compiler: ScalaModule) extends ScalaInstallation {

  import BundledScalaInstallation._

  override lazy val extraJars =
    Seq(
      findExtraJar(bundle, ScalaReflectPath, ScalaReflectSourcesPath),
      findExtraJar(bundle, ScalaActorPath, ScalaActorSourcesPath),
      findExtraJar(bundle, ScalaSwingPath, ScalaSwingSourcesPath)).flatten

  private def findExtraJar(bundle: Bundle, classPath: String, sourcePath: String): Option[ScalaModule] = {
    OSGiUtils.pathInBundle(bundle, classPath).map {p =>
      ScalaModule(p, OSGiUtils.pathInBundle(bundle, sourcePath))
    }
  }

  override def scalaInstance: ScalaInstance = {
    val store = ScalaPlugin.plugin.classLoaderStore
    val scalaLoader = store.getOrUpdate(this)(new URLClassLoader(allJars.map(_.classJar.toFile.toURI.toURL).toArray, ClassLoader.getSystemClassLoader))

    new sbt.ScalaInstance(version.unparse, scalaLoader, library.classJar.toFile, compiler.classJar.toFile, extraJars.map(_.classJar.toFile).toList, None)
  }
}

object BundledScalaInstallation {

  val ScalaLibraryPath = "target/jars/scala-library.jar"
  val ScalaLibrarySourcesPath = "target/jars/scala-library-src.jar"
  val ScalaCompilerPath = "target/jars/scala-compiler.jar"
  val ScalaCompilerSourcesPath = "target/jars/scala-compiler-src.jar"
  val ScalaReflectPath = "target/jars/scala-reflect.jar"
  val ScalaReflectSourcesPath = "target/jars/scala-reflect-src.jar"
  val ScalaActorPath = "target/jars/scala-actor.jar"
  val ScalaActorSourcesPath = "target/jars/scala-actor-src.jar"
  val ScalaSwingPath = "target/jars/scala-swing.jar"
  val ScalaSwingSourcesPath = "target/jars/scala-swing-src.jar"

  def apply(bundle: Bundle): Option[BundledScalaInstallation] = {
    for {
      scalaLibrary <- OSGiUtils.pathInBundle(bundle, ScalaLibraryPath)
      version <- ScalaInstallation.extractVersion(scalaLibrary)
      scalaCompiler <- OSGiUtils.pathInBundle(bundle, ScalaCompilerPath)
    } yield BundledScalaInstallation(
        version,
        bundle,
        ScalaModule(scalaLibrary, OSGiUtils.pathInBundle(bundle, ScalaLibrarySourcesPath)),
        ScalaModule(scalaCompiler, OSGiUtils.pathInBundle(bundle, ScalaCompilerSourcesPath)))
  }

  val ScalaBundleJarsRegex = "org\\.scala-ide\\.scala[0-9]{3}\\.jars".r

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
  libraryBundleVersion: Version,
  override val library: ScalaModule,
  override val compiler: ScalaModule) extends ScalaInstallation {

  import MultiBundleScalaInstallation._

  override lazy val extraJars = Seq(
      findLibrayForBundle(ScalaReflectBundleId, libraryBundleVersion),
      findLibrayForBundle(ScalaActorsBundleId, libraryBundleVersion),
      findLibrayForBundle(ScalaSwingBundleId, libraryBundleVersion)).flatten

  override def scalaInstance: ScalaInstance = {
    val store = ScalaPlugin.plugin.classLoaderStore
    val scalaLoader = store.getOrUpdate(this)(classLoader)

    new sbt.ScalaInstance(version.unparse, scalaLoader, library.classJar.toFile, compiler.classJar.toFile, extraJars.map(_.classJar.toFile).toList, None)
  }

  /** We reuse the current class loader if this installation is the platform installation */
  private def classLoader: ClassLoader = {
    import ScalaPlugin.plugin

    if (ScalaInstallation.platformInstallation.library == library && ScalaInstallation.platformInstallation.compiler == compiler)
      ScalaInstallation.getClass.getClassLoader()
    else
      new URLClassLoader(allJars.map(_.classJar.toFile.toURI.toURL).toArray, ClassLoader.getSystemClassLoader)
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

  private def findLibrayForBundle(bundleId: String, version: Version): Option[ScalaModule] = {
    val classPath = findBundle(bundleId, version).map(bundlePath)
    classPath.map(cp => ScalaModule(cp, EclipseUtils.computeSourcePath(bundleId, cp)))
  }

  def apply(libraryBundle: Bundle): Option[MultiBundleScalaInstallation] = {
    val libraryBundleVersion = libraryBundle.getVersion()

    for {
      version <- ScalaInstallation.extractVersion(bundlePath(libraryBundle))
      library = bundlePath(libraryBundle)
      compiler <- findLibrayForBundle(ScalaCompilerBundleId, libraryBundleVersion)
    } yield MultiBundleScalaInstallation(
        version,
        libraryBundleVersion,
        ScalaModule(bundlePath(libraryBundle), EclipseUtils.computeSourcePath(ScalaLibraryBundleId, library)),
        compiler)
  }

  def detectInstallations(): List[MultiBundleScalaInstallation] = {

    val scalaLibraryBundles = Platform.getBundles(ScalaLibraryBundleId, null).to[List]

    scalaLibraryBundles.flatMap(MultiBundleScalaInstallation(_))
  }
}

object ScalaInstallation {

  /** Return the Scala installation currently running in Eclipse. */
  lazy val platformInstallation: ScalaInstallation =
    multiBundleInstallations.find(_.version == ScalaVersion.current).get

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

  /** Return the closest installation to the given Scala version.
   *
   *  @return An existing ScalaInstallation.
   */
  def findBestMatch(desiredVersion: SpecificScalaVersion, available: Seq[ScalaInstallation] = ScalaInstallation.availableInstallations): ScalaInstallation = {
    def versionDistance(v: ScalaVersion) = v match {
      case SpecificScalaVersion(major, minor, micro, build) =>
        import Math._
        abs(major - desiredVersion.major) * 1000000 +
          abs(minor - desiredVersion.minor) * 10000 +
          abs(micro - desiredVersion.rev)     * 100 +
          abs(build.compare(desiredVersion.build))

      case _ =>
        0
    }
    available.minBy(i => versionDistance(i.version))
  }
}
