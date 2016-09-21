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
import sbt.internal.inc.CompilerCache
import sbt.internal.inc.CompilerInterfaceProvider
import sbt.internal.inc.Locate
import sbt.internal.inc.classpath.ClasspathUtilities
import xsbti.Logger
import xsbti.Maybe
import xsbti.compile.ClasspathOptions
import xsbti.compile.CompileAnalysis
import xsbti.compile.CompileProgress
import xsbti.compile.DefinesClass
import xsbti.compile.IncOptions
import xsbti.compile.IncOptionsUtil
import xsbti.compile.MultipleOutput

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

  def analysisMap(f: File): Maybe[CompileAnalysis] =
    if (f.isFile)
      Maybe.nothing[CompileAnalysis]
    else {
      val analysis = allProjects.collectFirst {
        case project if project.buildManager.buildManagerOf(f).nonEmpty =>
          project.buildManager.buildManagerOf(f).get.latestAnalysis
      }
      analysis.map { analysis =>
        Maybe.just(analysis)
      }.getOrElse(Maybe.nothing[CompileAnalysis])
    }

  def progress = Maybe.just(scalaProgress)

  def incOptions: IncOptions = {
    // TODO figure out what to do with ClassfileManager
//    import sbt.internal.inc.ClassfileManager
    IncOptionsUtil.defaultIncOptions().
      withApiDebug(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.apiDiff.name))).
      withRelationsDebug(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.relationsDebug.name))).
//      withNewClassfileManager(ClassfileManager.transactional(tempDir, logger)).
      withApiDumpDirectory(Maybe.nothing()).
      withRecompileOnMacroDef(Maybe.just(project.storage.getBoolean(SettingConverterUtil.convertNameToProperty(preferences.ScalaPluginSettings.recompileOnMacroDef.name)))).
      // Turning off name hashing is not supported in class-based dependency tracking
      withNameHashing(true)
  }

  def outputFolders = srcOutputs.map {
    case (_, out) => out.getRawLocation
  }

  def classpath = (project.scalaClasspath.userCp ++ addToClasspath ++ outputFolders)
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

  def javacOptions: Seq[String] = Nil // Not used.

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
          new AnalyzingCompiler(scalaInstance, CompilerInterfaceProvider.constant(compilerBridge.toFile), cpOptions),
          new JavaEclipseCompiler(project.underlying, javaMonitor)
        )
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
