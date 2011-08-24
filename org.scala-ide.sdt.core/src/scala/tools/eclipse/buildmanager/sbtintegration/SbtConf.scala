package scala.tools.eclipse
package buildmanager
package sbtintegration

import sbt.{ ScalaInstance, Path }
import xsbt.boot.{Launcher, Repository }  // TODO get rid of this dependency
import java.io.File
import org.eclipse.core.resources.ResourcesPlugin

object ScalaCompilerConf {
    val LIBRARY_SUFFIX = "scala-library.jar"
    val COMPILER_SUFFIX = "scala-compiler.jar"
    private val _bootdir = (new File("")).getAbsoluteFile
    	
    def apply(scalaHome: File): ScalaInstance = {
      val launcher = Launcher(_bootdir, Nil)
      ScalaInstance(scalaHome, launcher)
    }
    
    def apply(libraryJar: File, compilerJar: File): ScalaInstance = {
      val repo:List[Repository] = List(Repository.Predefined.apply(Repository.Predefined.Local))
    	val launcher = Launcher(_bootdir, repo)
    	ScalaInstance(libraryJar, compilerJar, launcher)
    }

    def apply(version: String, libraryJar: File, compilerJar: File, extraJar: File): ScalaInstance = {
      val repo:List[Repository] = List(Repository.Predefined.apply(Repository.Predefined.Local))
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
}

class BasicConfiguration(
    val project: ScalaProject,
    val classpath: Seq[File]
    // If I put default None here, I get NPE
    //    outputDir:Option[File]
    ) {
	  import Path._
    
    private final val cacheSuffix = ".cache"
    private final val outSuffix   = "target"
    
    def cacheLocation = project.underlying.getFile(cacheSuffix)
    
    def outputDirectories: List[File] = {
      val outDirs = project.outputFolders.toList
      outDirs match {
        case Nil =>
          println("[Warning] No output directory specified")
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