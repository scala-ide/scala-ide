package scala.tools.eclipse
package buildmanager
package sbtintegration

import java.io.File
import java.net.{URL, URLClassLoader}
import java.util.zip.{ZipEntry, ZipFile}
import org.eclipse.core.runtime.SubMonitor
import xsbti.{Maybe, Logger, Reporter}
import xsbti.compile._
import sbt.inc.{Analysis, Locate}
import sbt.compiler.{AnalyzingCompiler, IC, CompilerCache}
import sbt.classpath.ClasspathUtilities
import sbt.{ScalaInstance, ClasspathOptions}
import ScalaPlugin.plugin

class SbtInputs(sourceFiles: Seq[File], project: ScalaProject, javaMonitor: SubMonitor, scalaProgress: CompileProgress, cacheFile: File, scalaReporter: Reporter, logger: Logger) extends Inputs[Analysis, AnalyzingCompiler] {
  def setup = new Setup[Analysis] {
    def skip = false
    def definesClass(f: File): DefinesClass = Locator(f)
    def cacheFile = SbtInputs.this.cacheFile
    def cache = CompilerCache.fresh  // May want to explore caching possibilities.

    private val allProjects = project +: project.transitiveDependencies.flatMap(plugin.asScalaProject)
    def analysisMap(f: File): Maybe[Analysis] = 
      if (f.isFile)
        Maybe.just(Analysis.Empty)
      else
        allProjects.find(_.sourceOutputFolders.map(_._2.getLocation.toFile) contains f) map (_.buildManager) match { 
          case Some(sbtManager: EclipseSbtBuildManager) => Maybe.just(sbtManager.latestAnalysis)
          case _ => Maybe.just(Analysis.Empty)
        }
    def progress = Maybe.just(scalaProgress)
    def reporter = scalaReporter
  }

  def options = new Options {
    def classpath = project.scalaClasspath.fullClasspath.toArray
    def sources = sourceFiles.toArray
    def output = new MultipleOutput {
      def outputGroups = project.sourceOutputFolders.map {
        case (src, out) => new MultipleOutput.OutputGroup {
          def sourceDirectory = src.getLocation.toFile
          def outputDirectory = out.getLocation.toFile
        }
      }.toArray
    }
    def options = project.scalacArguments.toArray
    def javacOptions = Array() // Not used.

    import CompileOrder._
    import SettingConverterUtil.convertNameToProperty
    import properties.ScalaPluginSettings.compileOrder
    def order = project.storage.getString(convertNameToProperty(compileOrder.name)) match {
      case "JavaThenScala" => JavaThenScala
      case "ScalaThenJava" => ScalaThenJava
      case _ => Mixed
    }
  }

  def compilers = new Compilers[AnalyzingCompiler] {
    def javac = new JavaEclipseCompiler(project.underlying, javaMonitor)
    def scalac = {
      val libClasses = plugin.libClasses map (_.toFile) getOrElse (throw new RuntimeException("scala-library not found"))
      val compilerClasses = plugin.compilerClasses map (_.toFile) getOrElse (throw new RuntimeException("scala-compiler not found"))
      val reflectClasses = plugin.reflectClasses map (_.toFile)
      val scalaLoader = getClass.getClassLoader
      val scalaInstance = new ScalaInstance(plugin.scalaVer, scalaLoader, libClasses, compilerClasses, reflectClasses.toList, None)
      val compilerInterface = plugin.sbtCompilerInterface map (_.toFile) getOrElse (throw new RuntimeException("compiler-interface not found"))
      IC.newScalaCompiler(scalaInstance, compilerInterface, ClasspathOptions.boot, logger)
    }
  }
}

private[sbtintegration] object Locator {
  val NoClass = new DefinesClass {
    def apply(className: String) = false
  }

  def apply(f: File): DefinesClass =
    if (f.isDirectory)
      new DirectoryLocator(f)
    else if (f.exists && ClasspathUtilities.isArchive(f))
      new JarLocator(f)
    else
      NoClass

  class DirectoryLocator(dir: File) extends DefinesClass {
    def apply(className: String): Boolean = Locate.classFile(dir, className).isFile
  }

  class JarLocator(jar: File) extends DefinesClass {
    lazy val entries: Set[String] = {
      val zipFile = new ZipFile(jar, ZipFile.OPEN_READ)
      try {
        import scala.collection.JavaConverters._
        zipFile.entries.asScala.filterNot(_.isDirectory).map(_.getName).toSet
      } finally
        zipFile.close()
    }

    def apply(className: String): Boolean = entries.contains(className)
  }
}
