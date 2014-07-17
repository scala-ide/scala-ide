package org.scalaide.core.internal.project

import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.io.InputStream
import java.net.URLClassLoader
import java.util.Properties
import java.util.zip.ZipFile
import scala.tools.nsc.settings.ScalaVersion
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.ScalaPlugin.plugin
import sbt.ScalaInstance
import java.util.zip.ZipEntry
import org.scalaide.util.internal.CompilerUtils.isBinarySame
import org.scalaide.core.internal.project.ScalaInstallation.extractVersion
import scala.util.Try

/**
 * This class tries to collect a valid scala installation (library, compiler jars) from a directory.
 * The currently supported format is Just a Bunch of Jars: jars should be at the root of the directory,
 * there should be at least a scala-library and scala compiler, and the library should have a valid library.properties file.
 * The source jars and other jars (reflect, swing ...) are collected as best effort.
 *
 * TODO: support a more thorough lookup, such as when the jars are in lib/, maven, ivy ...
 * @param directory The directory in which to look for scala compiler & library jars.
 *
 */
class DirectoryScalaInstallation(val directory: IPath) extends ScalaInstallation {

  final val scalaLibraryPrefix = "scala-library"
  final val scalaReflectPrefix = "scala-reflect"
  final val scalaCompilerPrefix = "scala-compiler"
  final val scalaSwingPrefix = "scala-swing"
  final val scalaActorPrefix = "scala-actor"

  private val dirAsValidFile: Option[File] = {
    val f = directory.toFile()
    if (f.isDirectory()) Some(f) else None
  }

  private val extantJars: Option[Array[File]] = dirAsValidFile.map { f =>
    val fF = new FileFilter() { override def accept(p: File) = p.isFile && p.getName().endsWith(".jar") }
    f.listFiles(fF)
  }

  private def versionOfFileName(f:File): Option[String] = {
    val versionedRegex = """scala-\w+(.2\.\d+(?:\.\d*)?(?:-.*)?).jar""".r
    f.getName() match {
      case versionedRegex(version) => Some(version)
      case _ => None
    }
  }

  private def looksBinaryCompatible(version: ScalaVersion, module: ScalaModule) = {
        extractVersion(module.classJar) forall (isBinarySame(version, _))
  }

  /**
   * Returns a Option[ScalaModule] for the given prefix
   * @see [[findScalaJars(List[String]): List[ScalaModule]]
   */
  private def findScalaJars(prefix: String, presumedVersion: Option[String]): (Option[ScalaModule]) = {
    val res = findScalaJars(List(prefix), presumedVersion)
    if (res.nonEmpty) Some(res.head) else None
  }

  /**
   * Returns a List of whichever ScalaModule elements it could build from a string prefix in this instance's directory,
   * usually provided from the prefix constants defined in this class.
   *
   * @param prefix The intended jar prefix
   * @param presumedVersion a version string which will be preferred in filenames
   *        It should match """.2\.\d+(?:\.\d*)?(?:-.*)?""". If None, any version will be accepted.
   * @return A list of ScalaModule elements where class and source jars exist and start with the `prefix`
   */
  private def findScalaJars(prefixes: List[String], presumedVersion: Option[String]): List[ScalaModule] = {
    presumedVersion foreach { s => require(""".2\.\d+(?:\.\d*)?(?:-.*)?""".r.pattern.matcher(s).matches) }
    // for now this means we return whatever we could find: it may not be enough (missing scala-reflect, etc)
    prefixes flatMap { p =>
      val optionalVersion = """(?:.2\.\d+(?:\.\d*)?(?:-.*)?)?"""
      val requiredVersion = presumedVersion.fold(optionalVersion)(s => s.replaceAll("""\.""", """\\."""))
      val versionedString = s"$p$requiredVersion\\.jar"
      val versionedRegex = versionedString.r

      // Beware : the 'find' below indicates we're returning for the first matching option
      def jarLookup(r: scala.util.matching.Regex): Option[File] =
        (extantJars flatMap (_.find { f => r.pattern.matcher(f.getName()).matches}))

      // Try with any version if the presumed String can't be matched
      val classJarResult = jarLookup(versionedRegex) match {
        case s@Some(_) => s
        case None => jarLookup((s"$p$optionalVersion\\.jar").r)
      }
      val foundVersion = classJarResult flatMap versionOfFileName
      val requiredSrcVersion = foundVersion getOrElse ""

      val versionedSrcString = s"$p-src$requiredSrcVersion.jar"
      val versionedSrcRegex = versionedSrcString.replaceAll("""\.""", """\\.""").r

      classJarResult map { j =>
        ScalaModule(new Path(j.getCanonicalPath()), jarLookup(versionedSrcRegex) map { f => new Path(f.getCanonicalPath()) })
      }
    }
  }

  private val libraryCandidate = findScalaJars(scalaLibraryPrefix, None)
  private val presumedLibraryVersionString = libraryCandidate flatMap (l => versionOfFileName(l.classJar.toFile))
  private val versionCandidate: Option[ScalaVersion] = libraryCandidate.flatMap(l => extractVersion(l.classJar))
  private val compilerCandidate = findScalaJars(scalaCompilerPrefix, presumedLibraryVersionString)  filter {
    module => (versionCandidate forall (looksBinaryCompatible(_, module)))
    }

  /* initialization checks*/
  if (!dirAsValidFile.isDefined) throw new IllegalArgumentException("The provided path does not point to a valid directory.")
  if (!extantJars.isDefined) throw new IllegalArgumentException("No jar files found. Please place Scala library, compiler jar at the root of the directory.")
  if (!libraryCandidate.isDefined) throw new IllegalArgumentException("Can not recognize a valid Scala library jar in this directory.")
  if (!compilerCandidate.isDefined) throw new IllegalArgumentException("Can not recognize a valid Scala compiler jar in this directory.")
  if (!versionCandidate.isDefined) throw new IllegalArgumentException("The Scala library jar in this directory has incorrect or missing version information, aborting.")
  // TODO : this hard-coded hook will need changing
  if (versionCandidate.isDefined && versionCandidate.get < ScalaVersion("2.10.0")) throw new IllegalArgumentException("This Scala version is too old for the presentation compiler to use. Please provide a 2.10 scala (or later).")

  override lazy val extraJars = findScalaJars(List(scalaActorPrefix,
      scalaReflectPrefix,
      scalaSwingPrefix), presumedLibraryVersionString).filter {
    module => versionCandidate forall (looksBinaryCompatible(_, module))
    }
  override lazy val compiler = compilerCandidate.get
  override lazy val library = libraryCandidate.get
  override lazy val version = versionCandidate.get

  override def scalaInstance: ScalaInstance = {
    val store = ScalaPlugin.plugin.classLoaderStore
    val scalaLoader = store.getOrUpdate(this)(new URLClassLoader(allJars.map(_.classJar.toFile.toURI.toURL).toArray, ClassLoader.getSystemClassLoader))

    new sbt.ScalaInstance(version.unparse, scalaLoader, library.classJar.toFile, compiler.classJar.toFile, extraJars.map(_.classJar.toFile), None)
  }

}

object DirectoryScalaInstallation {

  def directoryScalaInstallationFactory(dir: IPath): Try[DirectoryScalaInstallation] = Try(new DirectoryScalaInstallation(dir))

}

class LabeledDirectoryScalaInstallation(name: String, directory: IPath) extends DirectoryScalaInstallation(directory) with LabeledScalaInstallation {
  override val label = CustomScalaInstallationLabel(name)

  def this(name: String, dsi: DirectoryScalaInstallation) = this(name, dsi.directory)
}
