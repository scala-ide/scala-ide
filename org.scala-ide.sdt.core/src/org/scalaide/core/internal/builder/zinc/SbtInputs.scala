package org.scalaide.core.internal.builder.zinc

import java.io.File
import java.util.zip.ZipFile

import org.eclipse.core.resources.IContainer
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.SubMonitor
import org.scalaide.core.IScalaInstallation
import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.project.ScalaInstallation.scalaInstanceForInstallation
import org.scalaide.ui.internal.preferences
import org.scalaide.util.internal.SettingConverterUtil

import sbt.ClasspathOptions
import sbt.Logger.xlog2Log
import sbt.classpath.ClasspathUtilities
import sbt.compiler.AnalyzingCompiler
import sbt.compiler.CompilerCache
import sbt.compiler.IC
import sbt.inc.Analysis
import sbt.inc.ClassfileManager
import sbt.inc.Locate
import xsbti.Logger
import xsbti.Maybe
import xsbti.compile._

/** Inputs-like class, but not implementing xsbti.compile.Inputs.
 *
 *  We return a real IncOptions instance, instead of relying on the Java interface,
 *  based on String maps. This allows us to use the transactional classfile writer.
 */
class SbtInputs(installation: IScalaInstallation,
    sourceFiles: Seq[File],
    project: IScalaProject,
    javaMonitor: SubMonitor,
    scalaProgress: CompileProgress,
    tempDir: File, // used to store classfiles between compilation runs to implement all-or-nothing semantics
    logger: Logger,
    addToClasspath: Seq[IPath] = Seq.empty,
    srcOutputs: Seq[(IContainer, IContainer)] = Seq.empty) {

  def cache = CompilerCache.fresh // May want to explore caching possibilities.

  private val allProjects = project +: project.transitiveDependencies.flatMap(ScalaPlugin().asScalaProject)

  def analysisMap(f: File): Maybe[Analysis] =
    if (f.isFile)
      Maybe.just(Analysis.Empty)
    else {
      val analysis = allProjects.collectFirst {
        case project if project.buildManager.buildManagerOf(f).nonEmpty =>
          project.buildManager.buildManagerOf(f).get.latestAnalysis(incOptions)
      }
      Maybe.just(analysis.getOrElse(Analysis.Empty))
    }

  def progress = Maybe.just(scalaProgress)

  def incOptions: sbt.inc.IncOptions = {
    sbt.inc.IncOptions.Default.
      withApiDebug(apiDebug = project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.apiDiff.name))).
      withRelationsDebug(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.relationsDebug.name))).
      withNewClassfileManager(ClassfileManager.transactional(tempDir, logger)).
      withApiDumpDirectory(None).
      withRecompileOnMacroDef(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.recompileOnMacroDef.name))).
      withNameHashing(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.nameHashing.name)))
  }

  def options = new Options {
    def outputFolders = srcOutputs.map {
      case (_, out) => out.getRawLocation
    }

    override def classpath = (project.scalaClasspath.userCp ++ addToClasspath ++ outputFolders)
      .distinct
      .map(_.toFile.getAbsoluteFile).toArray

    override def sources = sourceFiles.toArray

    override def output = new MultipleOutput {
      private def sourceOutputFolders =
        if (srcOutputs.nonEmpty) srcOutputs else project.sourceOutputFolders

      override def outputGroups = sourceOutputFolders.map {
        case (src, out) => new MultipleOutput.OutputGroup {
          override def sourceDirectory = {
            val loc = src.getLocation
            if (loc != null)
              loc.toFile()
            else
              throw new IllegalStateException(s"The source folder location `$src` is invalid.")
          }
          override def outputDirectory = {
            val loc = out.getLocation
            if (loc != null)
              loc.toFile()
            else
              throw new IllegalStateException(s"The output folder location `$out` is invalid.")
          }
        }
      }.toArray
    }

    // remove arguments not understood by build compiler
    override def options =
      if (project.isUsingCompatibilityMode())
        project.scalacArguments.filter(buildCompilerOption).toArray
      else
        project.scalacArguments.toArray

    /** Remove the source-level related arguments */
    private def buildCompilerOption(arg: String): Boolean =
      !arg.startsWith("-Xsource") && !(arg == "-Ymacro-expand:none")

    override def javacOptions = Array() // Not used.

    import CompileOrder._
    import SettingConverterUtil.convertNameToProperty
    import preferences.ScalaPluginSettings.compileOrder

    override def order = project.storage.getString(convertNameToProperty(compileOrder.name)) match {
      case "JavaThenScala" => JavaThenScala
      case "ScalaThenJava" => ScalaThenJava
      case _ => Mixed
    }
  }

  /**
   * @return Right-biased instance of Either (error message in Left, value in Right)
   */
  def compilers: Either[String, Compilers[sbt.compiler.AnalyzingCompiler]] = {
    val scalaInstance = scalaInstanceForInstallation(installation)
    val store = ScalaPlugin().compilerInterfaceStore

    store.compilerInterfaceFor(installation)(javaMonitor.newChild(10)).right.map {
      compilerInterface =>
        // prevent Sbt from adding things to the (boot)classpath
        val cpOptions = new ClasspathOptions(false, false, false, autoBoot = false, filterLibrary = false)
        new Compilers[AnalyzingCompiler] {
          override def javac = new JavaEclipseCompiler(project.underlying, javaMonitor)
          override def scalac = IC.newScalaCompiler(scalaInstance, compilerInterface.toFile, cpOptions)
        }
    }
  }
}

private[zinc] object Locator {
  val NoClass = new DefinesClass {
    override def apply(className: String) = false
  }

  def apply(f: File): DefinesClass =
    if (f.isDirectory)
      new DirectoryLocator(f)
    else if (f.exists && ClasspathUtilities.isArchive(f))
      new JarLocator(f)
    else
      NoClass

  class DirectoryLocator(dir: File) extends DefinesClass {
    override def apply(className: String): Boolean = Locate.classFile(dir, className).isFile
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

    override def apply(className: String): Boolean = entries.contains(className)
  }
}
