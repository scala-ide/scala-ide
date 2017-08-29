package org.scalaide.core.internal.builder.zinc

import java.util.function.Supplier

import scala.collection.mutable.ListBuffer
import scala.tools.nsc.settings.ScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion
import scala.util.Properties

import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.SubMonitor
import org.scalaide.core.IScalaInstallation
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.project.ScalaInstallation.scalaInstanceForInstallation
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.util.eclipse.EclipseUtils.RichPath
import org.scalaide.util.eclipse.FileUtils
import org.scalaide.util.eclipse.OSGiUtils

import sbt.internal.inc.AnalyzingCompiler
import sbt.internal.inc.RawCompiler
import xsbti.Logger
import xsbti.compile.ClasspathOptionsUtil

/** This class manages a store of compiler-bridge jars (as consumed by zinc). Each specific
 *  version of Scala needs a compiler-bridge jar compiled against that version.
 *
 *  `base` is used to store compiler-bridges on disk. The cache is based on the Zinc version included in IDE and
 *  Scala version for a given installation. The first time a client requests a compiler-bridge,
 *  the store will instantiate a raw compiler and compile it from the source. This may take some time,
 *  in the order of seconds.
 *
 *  This class is thread safe.
 */
class CompilerBridgeStore(base: IPath, plugin: ScalaPlugin) extends HasLogger {
  private val compilerBridgeName = "compiler-bridge.jar"
  private val compilerBridgesDir = base / "compiler-bridges"

  private val lockObject = new Object

  // raw stats
  private var hits, misses = 0

  private lazy val compilerBridgeSrc = {
    lazy val defaultPath = OSGiUtils.getBundlePath(plugin.zincCompilerBridgeBundle).flatMap(EclipseUtils.computeSourcePath(SdtConstants.ZincCompilerBridgePluginId, _))
    @volatile var path_2_10: Option[IPath] = None
    (scalaVersion: ScalaVersion) => synchronized {
      scalaVersion match {
        case SpecificScalaVersion(2, 10, _, _) =>
          if (path_2_10.isEmpty)
            path_2_10 = OSGiUtils.getBundlePath(plugin.zincCompilerBridgeBundle).flatMap(EclipseUtils.computeSourcePath(SdtConstants.ZincCompilerBridgePluginId, _, scalaVersion))
          path_2_10
        case _ =>
          defaultPath
      }
    }
  }

  private lazy val zincFullJar = OSGiUtils.getBundlePath(plugin.zincCompilerBundle)

  /** Return the location of a compiler-bridge.jar
   *
   *  This method will attempt to reuse bridges for a given Scala version. It
   *  may be long running the first time for a given version (it needs to compile the bridge)
   *
   *  @return An instance of Right(path) if successful, an error message inside `Left` otherwise.
   */
  def compilerBridgeFor(installation: IScalaInstallation)(implicit pm: IProgressMonitor): Either[String, IPath] = {
    val targetJar = bridgeJar(installation)

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

  /** Delete all cached compiler bridges and reset the stats.
   */
  def purgeCache(): Unit = {
    lockObject synchronized {
      hits = 0
      misses = 0
      FileUtils.deleteDir(compilerBridgesDir.toFile)
    }
  }

  /** Return the number of hits and misses in the store. */
  def getStats: (Int, Int) = (hits, misses)

  private def cacheDir(installation: IScalaInstallation): IPath =
    compilerBridgesDir /
    s"jdk-${Properties.javaVersion}" /
    s"zinc-${plugin.zincCompilerBridgeBundle.getVersion}" /
    s"scala-${installation.version.unparse}"

  private def bridgeJar(installation: IScalaInstallation): IPath = {
    cacheDir(installation) / compilerBridgeName
  }

  /** Build the compiler-bridge for the given Scala installation
   *
   *  @return a right-biased `Either`, carrying either the path to the resulting compiler-bridge jar, or
   *          a String with the error message.
   */
  private def buildInterface(installation: IScalaInstallation)(implicit pm: IProgressMonitor): Either[String, IPath] = {
    val name = s"Compiling compiler-bridge for ${installation.version.unparse}"
    val monitor = SubMonitor.convert(pm, name, 2)
    monitor.subTask(name)

    (compilerBridgeSrc(installation.version), zincFullJar) match {
      case (Some(compilerBridge), Some(zincInterface)) =>
        val log = new SbtLogger
        cacheDir(installation).toFile.mkdirs()
        val targetJar = bridgeJar(installation)
        monitor.worked(1)

        val label = installation.version.unparse
        val raw = new RawCompiler(scalaInstanceForInstallation(installation), ClasspathOptionsUtil.auto, log)
        AnalyzingCompiler.compileSources(List(compilerBridge.toFile), targetJar.toFile, List(zincInterface.toFile), label, raw, log)

        monitor.worked(1)

        log.errorMessages match {
          case Seq() => Right(targetJar)
          case errs  => Left(s"Error building compiler-bridge.jar for ${installation.version.unparse}: ${errs.mkString("\n")}")
        }

      case _ =>
        monitor.worked(2)
        Left("Could not find compiler-bridge bundle")
    }
  }

  private class SbtLogger extends Logger {
    private val errors = ListBuffer[String]()

    def errorMessages: Seq[String] = errors.toSeq

    def debug(x: Supplier[String]): Unit = {}
    def trace(x: Supplier[Throwable]): Unit = {}

    def error(x: Supplier[String]): Unit = {
      val msg = x.get // don't force it more than once, in case of side-effects
      logger.debug(msg)
      errors += msg
    }

    def info(x: Supplier[String]): Unit = {
      logger.info(x.get)
    }

    def warn(x: Supplier[String]): Unit = {
      logger.warn(x.get)
    }
  }
}
