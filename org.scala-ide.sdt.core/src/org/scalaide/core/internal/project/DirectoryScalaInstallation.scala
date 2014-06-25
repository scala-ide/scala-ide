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

/**
 * This class tries to collect a valid scala installation (library, compiler jars) from a directory.
 * The currently supported format is Just a Bunch of Jars: jars should be at the root of the directory,
 * there should be at least a scala-library and scala compiler, and the library should have a valid library.properties file.
 * The source jars and other jars (reflect, swing ...) are collected as best effort.
 *
 * TODO: All of this is oblivious to multiple suitable jars in the directory, and shouldn't.
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
    if (f.exists() && f.isDirectory()) Some(f) else None
  }

  private val extantJars: Option[Array[File]] = dirAsValidFile.map { f =>
    val fF = new FileFilter() { override def accept(p: File) = p.isFile && p.getName().endsWith(".jar") }
    f.listFiles(fF)
  }

  /**
   * Returns a Option[ScalaModule] for the given prefix
   * @see [[findScalaJars(List[String]): List[ScalaModule]]
   */
  private def findScalaJars(prefix: String): (Option[ScalaModule]) = {
    val res = findScalaJars(List(prefix))
    if (res.nonEmpty) Some(res.head) else None // no sense in returning a candidate version if we haven't found valid files
  }

  /**
   * Returns a List of whichever ScalaModule elements it could build from a string prefix in this instance's directory,
   * usually provided from the prefix constants defined in this class.
   *
   * @param prefix The intended jar prefix
   * @return A list of ScalaModule elements where class and source jars exist and start with the `prefix`
   */
  private def findScalaJars(prefixes: List[String]): List[ScalaModule] = {
    // for now this means we return whatever we could find: it may not be enough (missing scala-reflect, etc)
    prefixes flatMap { p =>
      val optionalVersion = """(?:.2\.\d+(?:\.\d*)?(?:-.*)?)?"""
      val versionedString = s"$p$optionalVersion\.jar"
      val versionedRegex = versionedString.r
      val versionedSrcString = s"$p-src$optionalVersion\.jar"
      val versionedSrcRegex = versionedSrcString.r

      def jarLookup(r: scala.util.matching.Regex): Option[File] = extantJars flatMap (_.find { (f: File) => r.pattern.matcher(f.getName()).matches })
      val jarResult = jarLookup(versionedRegex)
      jarResult map { j =>
        ScalaModule(new Path(j.getCanonicalPath()), jarLookup(versionedSrcRegex) map { f => new Path(f.getCanonicalPath()) })
      }
    }
  }

  private val compilerCandidate = findScalaJars(scalaCompilerPrefix)
  private val libraryCandidate = findScalaJars(scalaLibraryPrefix)
  //note only scala-library has a library.properties file
  private val versionCandidate: Option[ScalaVersion] = {
    libraryCandidate.flatMap(l => ScalaInstallation.extractVersion(l.classJar))
  }

  /* initialization checks*/
  if (!dirAsValidFile.isDefined) throw new IllegalArgumentException("The provided path does not point to a valid directory.")
  if (!extantJars.isDefined) throw new IllegalArgumentException("No jar files found. Please place Scala library, compiler jar at the root of the directory.")
  if (!compilerCandidate.isDefined) throw new IllegalArgumentException("Can not recognize a Scala compiler jar in this directory.")
  if (!libraryCandidate.isDefined) throw new IllegalArgumentException("Can not recogize a Scala library jar in this directory.")
  if (!versionCandidate.isDefined) throw new IllegalArgumentException("The Scala library jar in this directory has incorrect or missing version information, aborting.")

  override lazy val extraJars = findScalaJars(List(scalaActorPrefix, scalaReflectPrefix, scalaSwingPrefix, scalaReflectPrefix))
  override lazy val compiler = compilerCandidate.get
  override lazy val library = libraryCandidate.get
  override lazy val version = versionCandidate.get

  override def scalaInstance: ScalaInstance = {
    val store = ScalaPlugin.plugin.classLoaderStore
    val scalaLoader = store.getOrUpdate(this)(new URLClassLoader(allJars.map(_.classJar.toFile.toURI.toURL).toArray, ClassLoader.getSystemClassLoader))

    new sbt.ScalaInstance(version.unparse, scalaLoader, library.classJar.toFile, compiler.classJar.toFile, extraJars.map(_.classJar.toFile).toList, None)
  }

}