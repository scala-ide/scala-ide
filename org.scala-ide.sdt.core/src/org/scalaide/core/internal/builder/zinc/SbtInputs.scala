package org.scalaide.core.internal.builder.zinc

import java.io.File
import java.util.Optional

import org.eclipse.core.resources.IContainer
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.SubMonitor
import org.scalaide.core.IScalaInstallation
import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.ui.internal.preferences
import org.scalaide.util.internal.SettingConverterUtil

import sbt.internal.inc.FreshCompilerCache
import xsbti.Logger
import xsbti.compile.CompileAnalysis
import xsbti.compile.CompileProgress
import xsbti.compile.IncOptions
import xsbti.compile.TransactionalManagerType

/**
 * Inputs-like class, but not implementing xsbti.compile.Inputs.
 *
 *  We return a real IncOptions instance, instead of relying on the Java interface,
 *  based on String maps. This allows us to use the transactional classfile writer.
 */
class SbtInputs(
  installation: IScalaInstallation,
  sourceFiles: Seq[File],
  project: IScalaProject,
  javaMonitor: SubMonitor,
  scalaProgress: CompileProgress,
  tempDir: File, // used to store classfiles between compilation runs to implement all-or-nothing semantics
  logger: Logger,
  addToClasspath: Seq[IPath] = Seq.empty,
  srcOutputs: Seq[(IContainer, IContainer)] = Seq.empty) {

  def cache = new FreshCompilerCache // May want to explore caching possibilities.

  private val allProjects = project +: project.transitiveDependencies.flatMap(ScalaPlugin().asScalaProject)

  def analysisMap(f: File): Optional[CompileAnalysis] =
    if (f.isFile)
      Optional.empty[CompileAnalysis]
    else {
      val analysis = allProjects.collectFirst {
        case project if project.buildManager.buildManagerOf(f).nonEmpty =>
          project.buildManager.buildManagerOf(f).get.latestAnalysis
      }
      analysis.map { analysis =>
        Optional.ofNullable(analysis)
      }.getOrElse(Optional.empty[CompileAnalysis])
    }

  def progress = Optional.ofNullable(scalaProgress)

  def incOptions: IncOptions = {
    import xsbti.compile.IncOptions._
    val base = of()
    base.
      withApiDebug(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.apiDiff.name))).
      withRelationsDebug(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.relationsDebug.name))).
      withClassfileManagerType(Optional.ofNullable(TransactionalManagerType.create(tempDir, logger))).
      withRecompileOnMacroDef(Optional.ofNullable(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.recompileOnMacroDef.name))))
  }

  def outputFolders = srcOutputs.map {
    case (_, out) => out.getRawLocation
  }

  def classpath = (project.scalaClasspath.jdkPaths ++ project.scalaClasspath.userCp ++ addToClasspath ++ outputFolders)
    .distinct
    .map { cp ⇒
      val location = Option(cp.toFile).flatMap(f ⇒ Option(f.getAbsoluteFile))
      location getOrElse (throw new IllegalStateException(s"The classpath location `$cp` is invalid."))
    }.toArray

  def sources = sourceFiles.toArray

  private def srcOutDirs = (if (srcOutputs.nonEmpty) srcOutputs else project.sourceOutputFolders).map {
    case (src, out) => (Option(src.getLocation).map(_.toFile()), Option(out.getLocation).map(_.toFile()))
  }.collect {
    case (Some(src), Some(out)) => (src, out)
    case (Some(src), None) => throw new IllegalStateException(s"Not found output folder for source $src folder. Check build configuration.")
    case (None, Some(out)) => throw new IllegalStateException(s"Not found source folder for output $out folder. Check build configuration.")
    // case (None, None) is correct for some project building states. Ignore it.
  }

  def output = new EclipseMultipleOutput(srcOutDirs)

  // remove arguments not understood by build compiler
  def scalacOptions =
    if (project.isUsingCompatibilityMode())
      project.scalacArguments.filter(buildCompilerOption).toArray
    else
      project.scalacArguments.toArray

  /** Remove the source-level related arguments */
  private def buildCompilerOption(arg: String): Boolean =
    !arg.startsWith("-Xsource") && !(arg == "-Ymacro-expand:none")

  def javacOptions: Array[String] = Array() // Not used.

  import org.scalaide.ui.internal.preferences.ScalaPluginSettings.compileOrder
  import org.scalaide.util.internal.SettingConverterUtil.convertNameToProperty
  import xsbti.compile.CompileOrder._

  def order = project.storage.getString(convertNameToProperty(compileOrder.name)) match {
    case "JavaThenScala" => JavaThenScala
    case "ScalaThenJava" => ScalaThenJava
    case _ => Mixed
  }

  /**
   * @return Right-biased instance of Either (error message in Left, value in Right)
   */
  import org.scalaide.core.internal.builder.zinc.{ compilers => zincCompilers }
  def compilers: Either[String, Compilers] =
    zincCompilers(
      installation,
      javaMonitor,
      () => new JavaEclipseCompiler(project.underlying, javaMonitor))
}
