package scala.tools.eclipse
package buildmanager
package sbtintegration

import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.io.AbstractFile
import scala.collection.mutable
import org.eclipse.core.resources.{ IFile, IMarker }
import org.eclipse.core.runtime.{ IProgressMonitor, IPath, SubMonitor, Path}
import java.io.File
import xsbti.Controller
import sbt.compiler.{JavaCompiler}
import sbt.{Process, ClasspathOptions}
import scala.tools.eclipse.util.{ EclipseResource, FileUtils }
import org.eclipse.core.resources.IResource
import scala.tools.eclipse.logging.HasLogger
import sbt.inc.{ AnalysisFormats, AnalysisStore, Analysis, FileBasedStore }
import org.eclipse.core.resources.IProject
import sbinary.DefaultProtocol.{ immutableMapFormat, immutableSetFormat, StringFormat }

// The following code is based on sbt.AggressiveCompile
// Copyright 2010 Mark Harrah

object CompileOrderMapper {
  import sbt.CompileOrder
  import CompileOrder.{JavaThenScala, Mixed, ScalaThenJava}
  def apply(order: String): CompileOrder.Value = 
    order match {
      case "Mixed"         => Mixed
      case "JavaThenScala" => JavaThenScala
      case "ScalaThenJava" => ScalaThenJava
      case _               => Mixed
  }
}

class EclipseSbtBuildManager(val project: ScalaProject, settings0: Settings)
  extends EclipseBuildManager with HasLogger {
  
  var monitor: SubMonitor = _
  
  class SbtProgress extends Controller {
	  private var lastWorked = 0
	  private var savedTotal = 0
	  private var throttledMessages = 0
	
	  // Direct copy of the mechanism in the refined build managers
	  def runInformUnitStarting(phaseName: String, unitPath: String) {
	    throttledMessages += 1
	    if (throttledMessages == 10) {
	      throttledMessages = 0
	      val unitIPath: IPath = Path.fromOSString(unitPath)
	      val projectPath = project.javaProject.getProject.getLocation
	      monitor.subTask("phase " + phaseName + " for " + unitIPath.makeRelativeTo(projectPath))
	    }
	  }
	  
	  def runProgress(current: Int, total: Int): Boolean = 
	    if (monitor.isCanceled) {
	      false
	    } else {
	      if (savedTotal != total) {
	        monitor.setWorkRemaining(total - savedTotal)
	        savedTotal = total
	      }
	
	      if (lastWorked < current) {
	        monitor.worked(current - lastWorked)
	        lastWorked = current
	      }
	      true
	    }
  }
	
	/** Not supported */
	val compiler = null
	var depFile: IFile = null
	
	private[sbtintegration] lazy val _buildReporter = new BuildReporter(project, settings0) {
		val buildManager = EclipseSbtBuildManager.this
	}
	
	lazy val reporter: xsbti.Reporter = new SbtBuildReporter(_buildReporter)
	
	val pendingSources = new mutable.HashSet[IFile]

	/** Filter the classpath. Return the original classpath without the Scala library jar.
	 *  The second element of the tuple contains the Scala library jar.
	 */
	private def filterOutScalaLibrary(l: Seq[IPath]): (Seq[IPath], Option[IPath]) = {
		val jars = l.partition(p => p.lastSegment() == ScalaCompilerConf.LIBRARY_SUFFIX)
		
		// make sure the library file exists on disk. You can have several scala-library.jar entries in 
		// the classpath, coming from MANIFEST.MF (ClassPath: entry) expansion of other jars.
		// Such jars may not exist on disk, though.
		(jars._2, jars._1.find(p => p.lastSegment() == ScalaCompilerConf.LIBRARY_SUFFIX && p.toFile().exists()))
	}
	
	lazy val scalaVersion = {
	  // For the moment fixed to 2.9.0
	  ScalaPlugin.plugin.scalaVer
	}
	
  def compilers(libJar: File, compJar:File, compInterfaceJar: File): (ScalaSbtCompiler, JavaEclipseCompiler) = {
    val scalacInstance = ScalaCompilerConf(scalaVersion, libJar, compJar, compInterfaceJar)
    val scalac = new ScalaSbtCompiler(scalacInstance, reporter)
    val javac = new JavaEclipseCompiler(project.underlying, monitor)
    (scalac, javac)
  }
  
  //implicit val conLogger = sbt.ConsoleLogger()
  val sbtBuildLogger = new SbtBuildLogger(_buildReporter)
  implicit def toFile(files: mutable.Set[AbstractFile]): Seq[File] = files.map(_.file).toSeq
  implicit def toFile(files: scala.collection.Set[AbstractFile]): Seq[File] = files.map(_.file).toSeq
  
  
  private val sources: mutable.Set[AbstractFile] = mutable.Set.empty

  /** Add the given source files to the managed build process. */
  def addSourceFiles(files: scala.collection.Set[AbstractFile]) {
  	if (!files.isEmpty) {
  		sources ++= files
  	  runCompiler(sources)
  	}
  }

  /** Remove the given files from the managed build process. */
  def removeFiles(files: scala.collection.Set[AbstractFile]) {
  	if (!files.isEmpty)
  	  sources --= files
  }

  /** The given files have been modified by the user. Recompile
   *  them and their dependent files.
   */
  def update(added: scala.collection.Set[AbstractFile], removed: scala.collection.Set[AbstractFile]) {
    logger.info("update files: " + added)
    if (!added.isEmpty || !removed.isEmpty) {
      buildingFiles(added)
      removeFiles(removed)
      sources ++= added
      runCompiler(sources)
    }
  }

  private def runCompiler(sources: Seq[File]) {
    // setup the settings
    val allJarsAndLibrary = filterOutScalaLibrary(project.classpath)
    // Fixed 2.9 for now
    val libJar = allJarsAndLibrary match {
      case (_, Some(lib)) =>
        lib.toFile()
      case (_, None) =>
        logger.info("Cannot find Scala library on the classpath. Verify your build path! Using default library corresponding to the compiler")
        //ScalaPlugin.plugin.sbtScalaLib.get.toFile
        val e = new Exception("Cannot find Scala library on the classpath. Verify your build path!")
        project.buildError(IMarker.SEVERITY_ERROR, e.getMessage(), null)
        logger.error("Error in Scala SBT builder", e)
        return
    }
    //val compJar = ScalaPlugin.plugin.sbtScalaCompiler
    val compJar = ScalaPlugin.plugin.compilerClasses
    // TODO pull the actual version from properties and select the correct one
    val compInterfaceJar = ScalaPlugin.plugin.sbtCompilerInterface

    val (scalac, javac) = compilers(libJar, compJar.get.toFile, compInterfaceJar.get.toFile)
    // read settings properly
    //val cp = disintegrateClasspath(settings.classpath.value)
    val cp = allJarsAndLibrary._1.map(_.toFile)
    val conf = new BasicConfiguration(project, Seq(scalac.scalaInstance.libraryJar/*, compInterfaceJar.get.toFile*/) ++ cp)

    val analysisComp = new AnalysisCompile(conf, this, new SbtProgress())

    val extraAnalysis = upstreamAnalysis(project)

    logger.debug("Retrieved the following upstream analysis: " + extraAnalysis)

    val order = project.storage.getString(SettingConverterUtil.convertNameToProperty(properties.ScalaPluginSettings.compileOrder.name))
    analysisComp.doCompile(
      scalac, javac, sources, reporter, settings0, CompileOrderMapper(order), analysisMap = extraAnalysis)(sbtBuildLogger)
  }

  /** Return the Analysis for all the dependencies that are Scala projects, and that 
   *  have associated Analysis information, transitively.
   *  
   *  This works only if they are also built using the Sbt build manager
   */
  private def upstreamAnalysis(project: ScalaProject): Map[File, Analysis] = {
    val projectsWithBuilders = for {
      p <- project.transitiveDependencies
      dep <- ScalaPlugin.plugin.asScalaProject(p)
    } yield (dep, dep.buildManager)

    Map.empty ++ projectsWithBuilders.collect {
      case (dep, sbtBM: EclipseSbtBuildManager) 
        if dep.outputFolders.nonEmpty && sbtBM.latestAnalysis.isDefined =>
        val output = dep.outputFolders.head
        
        (ScalaPlugin.plugin.workspaceRoot.getFolder(output).getLocation.toFile, sbtBM.latestAnalysis.get)
    }
  }
  
  import AnalysisFormats._
  private val store = AnalysisStore.sync(
      new WeaklyCachedStore(
          FileBasedStore(
              EclipseResource(ScalaCompilerConf.cacheLocation(project.underlying)).file) ))

  /** Get the store where this builder keeps the API analysis. */
  def analysisStore: AnalysisStore = store
  
  /** Return the latest sbt Analysis for this builder. */
  def latestAnalysis: Option[Analysis] = analysisStore.get() match {
    case Some((analysis, _)) => Option(analysis)
    case _ => None
  }
  
  /** Not supported */
  def loadFrom(file: AbstractFile, toFile: String => AbstractFile) : Boolean = true
  
  /** Not supported */
  def saveTo(file: AbstractFile, fromFile: AbstractFile => String) {}
	
  def clean(implicit monitor: IProgressMonitor) {
    val dummy = new BasicConfiguration(project, Seq())
    dummy.cacheLocation.refreshLocal(IResource.DEPTH_ZERO, null)
    dummy.cacheLocation.delete(true, false, monitor)
    // refresh explorer
  }
  def invalidateAfterLoad: Boolean = true

  
  private def unbuilt: Set[AbstractFile] = Set.empty // TODO: this should be taken care of
  
  def build(addedOrUpdated : Set[IFile], removed : Set[IFile], pm: SubMonitor) {
    _buildReporter.reset()
    pendingSources ++= addedOrUpdated
    val removedFiles = removed.map(EclipseResource(_) : AbstractFile)
    val toBuild = pendingSources.map(EclipseResource(_)) ++ unbuilt -- removedFiles
    monitor = pm
    hasErrors = false
    try {
      update(toBuild, removedFiles)
    } catch {
      case e =>
        hasErrors = true
        project.buildError(IMarker.SEVERITY_ERROR, "Error in Scala compiler: " + e.getMessage, null)
        eclipseLog.error("Error in Scala compiler", e)
    }
    
    hasErrors = reporter.hasErrors || hasErrors
    if (!hasErrors)
      pendingSources.clear
  }
  
  override def buildingFiles(included: scala.collection.Set[AbstractFile]) {
    for(file <- included) {
      file match {
        case EclipseResource(f : IFile) =>
          FileUtils.clearBuildErrors(f, null)
          FileUtils.clearTasks(f, null)
        case _ =>
      }
    }
  }
}
