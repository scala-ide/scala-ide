package scala.tools.eclipse
package buildmanager
package sbtintegration

import sbt.{ ScalaInstance, Path }
import xsbt.boot.{ Launcher, Repository }
import java.io.File
import org.eclipse.core.resources.ResourcesPlugin
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IFile
import scala.tools.nsc.Settings
import sbt.compiler.CompilerArguments
import sbt.ClasspathOptions

/** Create an sbt ScalaInstance given the library and compiler jar. An
 *  Sbt ScalaInstance can be used to compile source files, and encapsulates
 *  a classloader used to instantiate the Scala compiler.
 */
object ScalaCompilerConf {
  final val CACHE_SUFFIX = ".cache"

  private val _bootdir = (new File("")).getAbsoluteFile

  def apply(scalaHome: File): ScalaInstance = {
    val launcher = Launcher(_bootdir, Nil)
    ScalaInstance(scalaHome, launcher)
  }

  def apply(libraryJar: File, compilerJar: File): ScalaInstance = {
    val repo: List[xsbti.Repository] = List(Repository.Predefined.apply(xsbti.Predefined.Local))
    val launcher = Launcher(_bootdir, repo)
    ScalaInstance(libraryJar, compilerJar, launcher)
  }

  def apply(version: String, libraryJar: File, compilerJar: File, extraJar: File): ScalaInstance = {
    val repo: List[xsbti.Repository] = List(Repository.Predefined.apply(xsbti.Predefined.Local))
    val launcher = Launcher(_bootdir, repo)
    ScalaInstance(version, libraryJar, compilerJar, launcher, extraJar)
  }

  def deployedInstance(): ScalaInstance = {
    val launcher = Launcher(_bootdir, Nil)
    ScalaInstance(ScalaPlugin.plugin.libClasses.get.toFile, ScalaPlugin.plugin.compilerClasses.get.toFile, launcher)
  }

  def cacheLocation(project: IProject): IFile =
    project.getFile(CACHE_SUFFIX)
}

class BasicConfiguration(val project: ScalaProject, val scalaInstance: ScalaInstance) extends HasLogger {
  import Path._

  private final val outSuffix = "target"

  def cacheLocation: IFile =
    ScalaCompilerConf.cacheLocation(project.underlying)

  def outputDirectories: List[File] = {
    val outDirs = project.outputFolders.toList
    outDirs match {
      case Nil =>
        logger.info("[Warning] No output directory specified")
        List(project.underlying.getLocation().toFile / "default-bin")
      case dirs =>
        val root = ResourcesPlugin.getWorkspace().getRoot()
        dirs.map(dir => root.getFolder(dir).getLocation().toFile())
    }
  }

  /** Return the arguments and Settings object that should be passed to build this project.
   *
   *  The Scala compiler has certain pecularities regarding the classpath. In particular:
   *
   *   - the JRE classpath has to go in `javabootclasspath`
   *   - the Scala library has to go in `bootclasspath`
   *   - setting just the `bootclasspath` without the `javabootclasspath` has no effect,
   *   because the Java runtime bootclasspath will be prepended to the Scala classpath,
   *   causing the Scala library that is used to *run* the compiler to appear on the compilation
   *   classpath *before* the one configured through `bootclasspath`.
   */
  def buildArguments(sources: Seq[File]): Seq[String] = {
    val scalaClassPath = project.scalaClasspath

    // Resolve classpath correctly
    val compArgs = new CompilerArguments(scalaInstance, ClasspathOptions(bootLibrary = true, compiler = false, extra = true, autoBoot = false, filterLibrary = true))
    val jrePath = scalaClassPath.jdkPaths.map(_.toFile)
    val classpathWithoutJVM = scalaClassPath.userCp.map(_.toFile) // no scala library in here!

    val argsWithoutOutput = removeSbtOutputDirs(compArgs(sources, classpathWithoutJVM.toSeq, outputDirectory, Seq()).toList)

    val bootClasspathArgs: String = scalaClassPath.scalaLib.get.toFile.getAbsolutePath
    Seq("-bootclasspath", bootClasspathArgs,
        "-javabootclasspath", CompilerArguments.absString(jrePath)) ++ argsWithoutOutput
  }

  private def removeSbtOutputDirs(args: List[String]) = {
    val (left, right) = args.span(_ != "-d")
    right match {
      case d :: out :: rest =>
        (left ::: rest).toSeq
      case _ =>
        assert(false, "Incorrect configuration for compiler arguments: " + args)
        args.toSeq
    }
  }

  def outputDirectory: File =
    // use projects directory. It doesn't really matter because this configuration
    // contains only a dummy value for sbt (though it needs to be real directory)
    project.underlying.getLocation().toFile

  def classesDirectory: File = {
    outputDirectory
  }
}