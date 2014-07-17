package org.scalaide.core.internal.builder.zinc

import org.eclipse.core.runtime.IPath
import scala.tools.nsc.settings.ScalaVersion
import org.eclipse.core.runtime.IProgressMonitor
import org.scalaide.core.internal.project.ScalaInstallation
import sbt.compiler.IC
import org.scalaide.core.ScalaPlugin
import org.eclipse.core.runtime.Platform
import org.scalaide.util.internal.eclipse.OSGiUtils
import java.io.File
import xsbti.Logger
import org.scalaide.logging.HasLogger
import scala.collection.mutable.ListBuffer
import org.eclipse.core.runtime.SubMonitor
import org.scalaide.util.internal.eclipse.EclipseUtils._
import org.scalaide.util.internal.eclipse.FileUtils

/** This class manages a store of compiler-interface jars (as consumed by Sbt). Each specific
 *  version of Scala needs a compiler-interface jar compiled against that version.
 *
 *  `base` is used to store compiler interfaces on disk. The cache is based on the
 *  Scala version for a given installation. The first time a client requests a compiler-interface,
 *  the store will instantiate a raw compiler and compile it from the source. This may take some time,
 *  in the order of seconds.
 *
 *  This class is thread safe.
 */
class CompilerInterfaceStore(base: IPath, plugin: ScalaPlugin) extends HasLogger {
  private val compilerInterfaceName = "compiler-interface.jar"
  private val compilerInterfacesDir = base / "compiler-interfaces"

  private val lockObject = new Object

  // raw stats
  private var hits, misses = 0

  private lazy val compilerInterfaceSrc =
    OSGiUtils.getBundlePath(plugin.sbtCompilerInterfaceBundle).flatMap(computeSourcePath(plugin.sbtCompilerInterfaceId, _))

  private lazy val sbtFullJar = OSGiUtils.getBundlePath(plugin.sbtCompilerBundle)

  /** Return the location of a compiler-interface.jar
   *
   *  This method will attempt to reuse interfaces for a given Scala version. It
   *  may be long running the first time for a given version (it needs to compile the interface)
   *
   *  @retur An instance of Right(path) if successful, an error message inside `Left` otherwise.
   */
  def compilerInterfaceFor(installation: ScalaInstallation)(implicit pm: IProgressMonitor): Either[String, IPath] = {
    val targetJar = interfaceJar(installation)

    lockObject synchronized {
      if (targetJar.toFile.exists()) {
        hits += 1
        Right(targetJar)
      } else {
        misses += 1
        buildInterface(installation)
      }
    }
  }

  /** Delete all cached compiler interfaces and reset the stats.
   */
  def purgeCache(): Unit = {
    lockObject synchronized {
      hits = 0
      misses = 0
      FileUtils.deleteDir(compilerInterfacesDir.toFile)
    }
  }

  /** Return the number of hits and misses in the store. */
  def getStats: (Int, Int) = (hits, misses)

  private def cacheDir(installation: ScalaInstallation): IPath =
    compilerInterfacesDir / installation.version.unparse

  private def interfaceJar(installation: ScalaInstallation): IPath = {
    cacheDir(installation) / compilerInterfaceName
  }

  /** Build the compiler-interface for the given Scala installation
   *
   *  @return a right-biased `Either`, carrying either the path to the resulting compiler-interface jar, or
   *          a String with the error message.
   */
  private def buildInterface(installation: ScalaInstallation)(implicit pm: IProgressMonitor): Either[String, IPath] = {
    val monitor = SubMonitor.convert(pm, s"Compiling compiler-interface for ${installation.version.unparse}", 2)

    (compilerInterfaceSrc, sbtFullJar) match {
      case (Some(compilerInterface), Some(sbtInterface)) =>
        val log = new SbtLogger
        cacheDir(installation).toFile.mkdirs()
        val targetJar = interfaceJar(installation)
        monitor.worked(1)

        IC.compileInterfaceJar(installation.version.unparse,
          compilerInterface.toFile,
          targetJar.toFile,
          sbtInterface.toFile,
          installation.scalaInstance,
          log)

        monitor.worked(1)

        log.errorMessages match {
          case Seq() => Right(targetJar)
          case errs  => Left(s"Error building compiler-interface.jar for ${installation.version.unparse}: ${errs.mkString("\n")}")
        }

      case _ =>
        monitor.worked(2)
        Left("Could not find compiler-interface/sbt bundle")
    }
  }

  private class SbtLogger extends Logger {
    private val errors = ListBuffer[String]()

    def errorMessages: Seq[String] = errors.toSeq

    def debug(x: xsbti.F0[String]): Unit = {}
    def trace(x: xsbti.F0[Throwable]): Unit = {}

    def error(x: xsbti.F0[String]): Unit = {
      val msg = x() // don't force it more than once, in case of side-effects
      logger.debug(msg)
      errors += msg
    }

    def info(x: xsbti.F0[String]): Unit = {
      logger.info(x())
    }

    def warn(x: xsbti.F0[String]): Unit = {
      logger.warn(x())
    }
  }
}