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
import scala.collection.mutable.Set
import org.scalaide.util.internal.eclipse.EclipseUtils
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.runtime.IStatus
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import org.eclipse.core.runtime.CoreException
import java.io.File
import org.eclipse.core.runtime.Status
import org.scalaide.logging.HasLogger
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import org.scalaide.util.internal.CompilerUtils.isBinarySame

sealed trait ScalaInstallationLabel extends Serializable
case class BundledScalaInstallationLabel() extends ScalaInstallationLabel
case class MultiBundleScalaInstallationLabel() extends ScalaInstallationLabel
case class CustomScalaInstallationLabel(label: String) extends ScalaInstallationLabel

/**
 *  A type that marks the choice of a Labeled Scala Installation : either a Scala Version,
 *  which will dereference to the latest available bundle with the same binary version, or
 *  a scala installation hashcode, which will dereference to the Labeled installation which
 *  hashes to it, if available.
 *
 *  @see ScalaInstallation.resolve
 */
case class ScalaInstallationChoice (marker: Either[ScalaVersion, Int]) extends Serializable{
  override def toString() = marker match {
    case Left(version) => version.unparse
    case Right(hash) => hash.toString
  }
}

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
  def scalaInstance: ScalaInstance = {
    val store = ScalaPlugin.plugin.classLoaderStore
    val scalaLoader = store.getOrUpdate(this)(new URLClassLoader(allJars.map(_.classJar.toFile.toURI.toURL).toArray, ClassLoader.getSystemClassLoader))

    new sbt.ScalaInstance(version.unparse, scalaLoader, library.classJar.toFile, compiler.classJar.toFile, extraJars.map(_.classJar.toFile).toList, None)
  }

  override def toString() =
    s"Scala $version: \n\t${allJars.mkString("\n\t")})"

  def isValid(): Boolean = {
    allJars forall (_.isValid())
  }
}

/**
 *  A tag for serializable tagging of Scala Installations
 */
trait LabeledScalaInstallation extends ScalaInstallation {
      def label: ScalaInstallationLabel
      // to recover bundle-less Bundle values from de-serialized Scala Installations
      // this should be relaxed for bundles : our bundles are safe, having one with just the same version should be enough
      def similar(that: LabeledScalaInstallation): Boolean =
        this.label == that.label && this.compiler == that.compiler && this.library == that.library && this.extraJars.toSet == that.extraJars.toSet

      def getName():Option[String] = PartialFunction.condOpt(label) {case CustomScalaInstallationLabel(tag) => tag}
      def getHashString(): String = getName().fold(allJars map (_.getHashString()))(str => str +: (allJars map (_.getHashString()))).mkString
}

case class ScalaModule(classJar: IPath, sourceJar: Option[IPath]) {

  def isValid(): Boolean = {
    sourceJar.fold(List(classJar))(List(_, classJar)) forall {path => path.toFile().isFile()}
  }

  def libraryEntries(): IClasspathEntry = {
    JavaCore.newLibraryEntry(classJar, sourceJar.orNull, null)
  }

  def getHashString(): String = sourceJar.fold(classJar.toPortableString())(s => classJar.toPortableString() + s.toPortableString())
}

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
  override val compiler: ScalaModule) extends LabeledScalaInstallation {

  import BundledScalaInstallation._

  override val label =  BundledScalaInstallationLabel()
  def osgiVersion = bundle.getVersion()

  override lazy val extraJars =
    Seq(
      findExtraJar(bundle, ScalaReflectPath, ScalaReflectSourcesPath),
      findExtraJar(bundle, ScalaActorPath, ScalaActorSourcesPath),
      findExtraJar(bundle, ScalaSwingPath, ScalaSwingSourcesPath)).flatten

  private def findExtraJar(bundle: Bundle, classPath: String, sourcePath: String): Option[ScalaModule] = {
    OSGiUtils.pathInBundle(bundle, classPath).map { p =>
      ScalaModule(p, OSGiUtils.pathInBundle(bundle, sourcePath))
    }
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
 *
 *  TODO: We SHOULD reuse the current class loader if this installation is the platform installation.
 *
 *  @note We don't reuse it because of weird interactions between the OSGi classloader and the compiler-interface.jar,
 *        resulting in AbstractMethodErrors. The `Reporter` interface is defined in scala-reflect, but implemented in
 *        compiler-interface.jar (which is NOT a bundle), and `info0` is not seen.
 *
 *        See ticket #1002175
 */
case class MultiBundleScalaInstallation(
  override val version: ScalaVersion,
  libraryBundleVersion: Version,
  override val library: ScalaModule,
  override val compiler: ScalaModule) extends LabeledScalaInstallation {

  import MultiBundleScalaInstallation._

  override val label =  MultiBundleScalaInstallationLabel()
  def osgiVersion = libraryBundleVersion

  override lazy val extraJars = Seq(
    findLibraryForBundle(ScalaReflectBundleId, libraryBundleVersion),
    findLibraryForBundle(ScalaActorsBundleId, libraryBundleVersion),
    findLibraryForBundle(ScalaSwingBundleId, libraryBundleVersion)).flatten
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

  private def findLibraryForBundle(bundleId: String, version: Version): Option[ScalaModule] = {
    val classPath = findBundle(bundleId, version).map(bundlePath)
    classPath.map(cp => ScalaModule(cp, EclipseUtils.computeSourcePath(bundleId, cp)))
  }

  def apply(libraryBundle: Bundle): Option[MultiBundleScalaInstallation] = {
    val libraryBundleVersion = libraryBundle.getVersion()

    for {
      version <- ScalaInstallation.extractVersion(bundlePath(libraryBundle))
      library = bundlePath(libraryBundle)
      compiler <- findLibraryForBundle(ScalaCompilerBundleId, libraryBundleVersion)
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

  val installationsTracker = new ScalaInstallationSaver()
  def savedScalaInstallations() = Try(installationsTracker.getSavedInstallations())
  lazy val initialScalaInstallations = savedScalaInstallations() match {
    case Success(sis) => sis filter (_.isValid()) filter {deserial => !(bundledInstallations ++ multiBundleInstallations exists (_.similar(deserial)))}
    // we need to silently fail, as this happens early in initialization
    case Failure(throwable) => Nil
  }

  // This lets you see installs retrieved from serialized bundles as newly-defined custom installations
  def customize(install: LabeledScalaInstallation) = install.label match {
    case CustomScalaInstallationLabel(tag) => install
    case BundledScalaInstallationLabel() | MultiBundleScalaInstallationLabel() => new LabeledScalaInstallation() {
      override def label = new CustomScalaInstallationLabel(s"legacy bundle ${install.hashCode()}")
      override def compiler = install.compiler
      override def library = install.library
      override def extraJars = install.extraJars
      override def scalaInstance = install.scalaInstance
      override def version = install.version
    }
  }

  lazy val customInstallations: Set[LabeledScalaInstallation] = Set() ++ initialScalaInstallations.map(customize(_))

  /** Return the Scala installation currently running in Eclipse. */
  lazy val platformInstallation: ScalaInstallation =
    multiBundleInstallations.find(_.version == ScalaVersion.current).get

  lazy val bundledInstallations: List[LabeledScalaInstallation] =
    BundledScalaInstallation.detectBundledInstallations()

  lazy val multiBundleInstallations: List[LabeledScalaInstallation] =
    MultiBundleScalaInstallation.detectInstallations()

  def availableBundledInstallations : List[LabeledScalaInstallation] = {
    multiBundleInstallations ++ bundledInstallations
  }

  def availableInstallations: List[LabeledScalaInstallation] = {
    multiBundleInstallations ++ bundledInstallations ++ customInstallations
  }

  val LibraryPropertiesPath = "library.properties"

  def labelInFile(scalaPath: IPath) : Option[String] = {
    val scalaJarRegex = """scala-(\w+)(?:.2\.\d+(?:\.\d*)?(?:-.*)?)?.jar""".r
    scalaPath.toFile().getName() match {
      case scalaJarRegex(qualifier) => Some(qualifier + ".properties")
      case _ => None
    }
  }

  def extractVersion(scalaLibrary: IPath): Option[ScalaVersion] = {
    val propertiesPath = labelInFile(scalaLibrary).getOrElse(LibraryPropertiesPath)
    val zipFile = new ZipFile(scalaLibrary.toFile())
    try {
      def getVersion(propertiesEntry: ZipEntry) = {
        val properties = new Properties()
        properties.load(zipFile.getInputStream(propertiesEntry))
        Option(properties.getProperty("version.number"))
      }

      for {
        propertiesEntry <- Option(zipFile.getEntry(propertiesPath))
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

  def resolve(choice: ScalaInstallationChoice): Option[ScalaInstallation] = choice.marker match{
    case Left(version) => availableBundledInstallations.filter { si => isBinarySame(version, si.version) }.sortBy(_.version).lastOption
    case Right(hash) => availableInstallations.find(si => si.getHashString().hashCode() == hash)
  }

}
