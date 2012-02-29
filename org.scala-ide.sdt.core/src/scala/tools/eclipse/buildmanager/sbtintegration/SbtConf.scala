package scala.tools.eclipse
package buildmanager
package sbtintegration

import sbt.{ ScalaInstance, Path }
import xsbt.boot.{Launcher, Repository }
import java.io.File
import org.eclipse.core.resources.ResourcesPlugin
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IFile

object ScalaCompilerConf {
    final val LIBRARY_SUFFIX = "scala-library.jar"
    final val COMPILER_SUFFIX = "scala-compiler.jar"
      
    final val CACHE_SUFFIX = ".cache"

    private val _bootdir = (new File("")).getAbsoluteFile
    	
    def apply(scalaHome: File): ScalaInstance = {
      val launcher = Launcher(_bootdir, Nil)
      ScalaInstance(scalaHome, launcher)
    }
    
    def apply(libraryJar: File, compilerJar: File): ScalaInstance = {
      val repo:List[xsbti.Repository] = List(Repository.Predefined.apply(xsbti.Predefined.Local))
    	val launcher = Launcher(_bootdir, repo)
    	ScalaInstance(libraryJar, compilerJar, launcher)
    }

    def apply(version: String, libraryJar: File, compilerJar: File, extraJar: File): ScalaInstance = {
      val repo:List[xsbti.Repository] = List(Repository.Predefined.apply(xsbti.Predefined.Local))
    	val launcher = Launcher(_bootdir, repo)
    	ScalaInstance(version, libraryJar, compilerJar, launcher, extraJar)      
    }
    
    def apply(version: String, eclipsePluginDir: File): ScalaInstance = {
      val launcher = Launcher(_bootdir, Nil)
      val libraryJar = findJar(eclipsePluginDir, LIBRARY_SUFFIX, version)
      val compilerJar = findJar(eclipsePluginDir, COMPILER_SUFFIX, version)
      //val libraryJar = ScalaPlugin.plugin.sbtScalaLib
      //val compilerJar = ScalaPlugin.plugin.sbtScalaCompiler
      ScalaInstance(libraryJar, compilerJar, launcher)
    }
    
    private def findJar(dir: File, prefix: String, version: String):File = {
        new File(dir, prefix + version + ".jar")
    }
    
    def cacheLocation(project: IProject): IFile =
      project.getFile(CACHE_SUFFIX)
}

class BasicConfiguration(
    val project: ScalaProject,
    val classpath: Seq[File]
    // If I put default None here, I get NPE
    //    outputDir:Option[File]
    ) extends HasLogger {
	  import Path._
    
    private final val outSuffix   = "target"
    
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
    
    def outputDirectory: File =
      // use projects directory. It doesn't really matter because this configuration
      // contains only a dummy value for sbt (though it needs to be real directory)
      project.underlying.getLocation().toFile

    def classesDirectory: File = {
        outputDirectory
    }
    
    def fullClasspath: Seq[File] = {
        Seq(classesDirectory) ++ classpath
    }
}