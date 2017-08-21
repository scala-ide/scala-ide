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

import sbt.internal.inc.AnalyzingCompiler
import xsbti.compile.CompilerBridgeProvider
import sbt.internal.inc.Locate
import sbt.internal.inc.classpath.ClasspathUtilities
import xsbti.Logger
import xsbti.compile.ClasspathOptions
import xsbti.compile.CompileAnalysis
import xsbti.compile.CompileProgress
import xsbti.compile.DefinesClass
import xsbti.compile.IncOptions
import xsbti.compile.MultipleOutput
import xsbti.compile.TransactionalManagerType
import sbt.internal.inc.FreshCompilerCache
import java.util.Optional
import xsbti.compile.ScalaInstance

/**
 * Inputs-like class, but not implementing xsbti.compile.Inputs.
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
    val base = IncOptions.of()
    base.
      withApiDebug(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.apiDiff.name))).
      withRelationsDebug(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.relationsDebug.name))).
      withClassfileManagerType(Optional.ofNullable(new TransactionalManagerType(tempDir, logger))).
      withApiDumpDirectory(Optional.empty()).
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

  def output = new MultipleOutput {
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
  def compilers: Either[String, Compilers] = {
    val scalaInstance = scalaInstanceForInstallation(installation)
    val store = ScalaPlugin().compilerBridgeStore

    store.compilerBridgeFor(installation)(javaMonitor.newChild(10)).right.map {
      compilerBridge =>
        // prevent zinc from adding things to the (boot)classpath
        val cpOptions = new ClasspathOptions(false, false, false, /* autoBoot = */ false, /* filterLibrary = */ false)
        Compilers(
          new AnalyzingCompiler(scalaInstance,
              new CompilerBridgeProvider {
                def fetchCompiledBridge(si: ScalaInstance, logger: Logger) = si match {
                  case scalaInstance => compilerBridge.toFile
                }
                def fetchScalaInstance(scalaVersion: String, logger: Logger) = scalaInstance
              },
              cpOptions,
              _ ⇒ (),
              None),
          new JavaEclipseCompiler(project.underlying, javaMonitor))
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
        zipFile.entries.asScala.filterNot(_.isDirectory).map { entry =>
          toClassNameFromJarFileName(entry.getName)
        }.toSet
      } finally
        zipFile.close()
    }

    private def toClassNameFromJarFileName(jarFileName: String): String = {
      val noClassAtEnd = if (jarFileName.endsWith(".class"))
        jarFileName.substring(0, jarFileName.lastIndexOf(".class"))
      else
        jarFileName
      noClassAtEnd.replaceAll("/", ".")
    }

    override def apply(className: String): Boolean =
      entries.contains(className)
  }
}
