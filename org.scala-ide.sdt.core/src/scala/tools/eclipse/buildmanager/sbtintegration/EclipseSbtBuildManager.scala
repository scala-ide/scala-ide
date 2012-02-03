package scala.tools.eclipse
package buildmanager
package sbtintegration

import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.{ Position, NoPosition, FakePos }
import scala.tools.nsc.reporters.Reporter
import scala.collection.mutable
import org.eclipse.core.resources.{ IFile, IMarker }
import org.eclipse.core.runtime.{ IProgressMonitor, IPath, SubMonitor, Path}
import java.io.File
import xsbti.Maybe
import xsbti.Controller
import sbt.compiler.{JavaCompiler}
import sbt.{Process, ClasspathOptions}
import scala.tools.eclipse.util.{ EclipseResource, FileUtils }
import org.eclipse.core.resources.IResource
import scala.tools.eclipse.util.HasLogger

// The following code is based on sbt.AggressiveCompile
// Copyright 2010 Mark Harrah

private object SbtConverter {
	// This piece of code is directly copied from sbt sources. There
  // doesn't seem to be other way atm to convert between sbt and scala compiler reporting
	private[this] def o[T](t: Option[T]): Option[T] = t
	private[this] def o[T](t: T): Option[T] = Some(t)

	
	def convertToSbt(posIn: Position): xsbti.Position =
	{
		val pos =
			posIn match
			{
				case null | NoPosition => NoPosition
				case x: FakePos => x
				case x =>
					posIn.inUltimateSource(o(posIn.source).get)
			}
		pos match
		{
			case NoPosition | FakePos(_) => position(None, None, None, "", None, None, None)
			case _ => makePosition(pos)
		}
	}
	def makePosition(pos: Position): xsbti.Position =
	{
		val srcO = o(pos.source)
		val opt(sourcePath, sourceFile) = for(src <- srcO) yield (src.file.path, src.file.file)
		val line = o(pos.line)
		if(!line.isEmpty)
		{
			val lineContent = pos.lineContent.stripLineEnd
			val offsetO = o(pos.offset)
			val opt(pointer, pointerSpace) =
				for(offset <- offsetO; src <- srcO) yield
				{
					val pointer = offset - src.lineToOffset(src.offsetToLine(offset))
					val pointerSpace = ((lineContent: Seq[Char]).take(pointer).map { case '\t' => '\t'; case x => ' ' }).mkString
					(pointer, pointerSpace)
				}
			position(sourcePath, sourceFile, line, lineContent, offsetO, pointer, pointerSpace)
		}
		else
			position(sourcePath, sourceFile, line, "", None, None, None)
	}
	private[this] object opt
	{
		def unapply[A,B](o: Option[(A,B)]): Some[(Option[A], Option[B])] =
			Some(o match
			{
				case Some((a,b)) => (Some(a), Some(b))
				case None => (None, None)
			})
	}
	private[this] def position(sourcePath0: Option[String], sourceFile0: Option[File], line0: Option[Int], lineContent0: String, offset0: Option[Int], pointer0: Option[Int], pointerSpace0: Option[String]) =
		new xsbti.Position
		{
			val line = o2mi(line0)
			val lineContent = lineContent0
			val offset = o2mi(offset0)
			val sourcePath = o2m(sourcePath0)
			val sourceFile = o2m(sourceFile0)
			val pointer = o2mi(pointer0)
			val pointerSpace = o2m(pointerSpace0)
		}

	import xsbti.Severity.{Info, Warn, Error}
	def convertToSbt(sev: Reporter#Severity, reporter: Reporter): xsbti.Severity = {
		import reporter. { INFO, WARNING, ERROR }
		sev match
		{
			case INFO => Info
			case WARNING => Warn
			case ERROR => Error
		}
	}

  import java.lang.{Integer => I}
  private[this] def o2mi(opt: Option[Int]): Maybe[I] = opt match { case None => Maybe.nothing[I]; case Some(s) => Maybe.just[I](s) }
  private[this] def o2m[S](opt: Option[S]): Maybe[S] = opt match { case None => Maybe.nothing[S]; case Some(s) => Maybe.just(s) }
}

private class SbtBuildReporter(underlying: BuildReporter) extends xsbti.Reporter {
  import scala.tools.nsc.util.{BatchSourceFile, OffsetPosition}
  import scala.tools.nsc.io.AbstractFile
  
  def toXsbtProblem(p: BuildProblem): xsbti.Problem =
    new xsbti.Problem {
      def severity() = SbtConverter.convertToSbt(p.severity, underlying)
      def message() = p.msg
      def position() = SbtConverter.convertToSbt(p.pos)
    }
	
	implicit def toScalaPosition(pos0: xsbti.Position): Position = {
	  val srcpath0 = pos0.sourcePath()
	  val srcfile0 = pos0.sourceFile()
	  val offset0 = pos0.offset()
	  (srcpath0.isDefined(), srcfile0.isDefined(), offset0.isDefined()) match {
	    case (false, false, false) => 
	      NoPosition
	    case _ =>
	      val ifile = EclipseResource.fromString(srcpath0.get)
	      ifile match {
	        case None =>
	          NoPosition
	        case Some(ifile0) =>
	          val sourceFile = new BatchSourceFile(ifile0)
	          val offset = offset0.get.intValue
	          new OffsetPosition(sourceFile, offset)	          
	      }
	  }
	}
	
	def reset() =	underlying.reset
	def hasErrors() = underlying.hasErrors
	def hasWarnings() = underlying.hasWarnings
  def printSummary() {} //TODO
	def problems: Array[xsbti.Problem] = underlying.prob.map(toXsbtProblem).toArray

	def log(pos: xsbti.Position, msg: String, sev: xsbti.Severity) {
		import xsbti.Severity.{Info, Warn, Error}
		sev match
		{
			case Info  => underlying.info(pos, msg, false)
			case Warn  => underlying.warning(pos, msg)
			case Error => underlying.error(pos, msg)
		}
	}
	
	def comment(pos: xsbti.Position, msg: String) {
	  underlying.comment(pos, msg)
	}
}

trait EclipseLogger extends sbt.Logger {
  def flush(): Unit
}

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

class SbtBuildLogger(underlying: BuildReporter) extends EclipseLogger {
  // This needs to be improved but works for most of the java errors
  val javaErrorBegin = ".*\\.java:(\\d+):.*".r
  val buff = mutable.ListBuffer[Tuple2[sbt.Level.Value, String]]()
	def trace(t: => Throwable) {
    // ignore for now?
	}
	def success(message: => String) { }
	def log(level: sbt.Level.Value, message: => String) {
	  import sbt.Level.{Info, Warn, Error, Debug}
	  level match {
	    case Info => ()
	    case Warn => buff += ((level, message))  //underlying.warning(NoPosition, message)
	    case Error => buff += ((level, message)) //underlying.error(NoPosition, message)
	    case Debug => ()
	  }
	}
	
		
	// This will at least ensure that we print log in the order required by eclipse for java problems
	// This is a temporary solution until the parsing of java error/warning messages is done correctly
	def flush() {
	  def publishMsg(level: sbt.Level.Value, msg: String) = {
	    level match {
	        case sbt.Level.Warn  =>
	          underlying.warning(NoPosition, msg)
	        case sbt.Level.Error =>
	          underlying.error(NoPosition, msg)
	      }
	  }
	  import scala.collection
	  val localBuff = new mutable.ListBuffer[String]()
	  val buff0 = buff.dropRight(1) // remove number of error message
	  var lastLevel: sbt.Level.Value = null
	  buff0.foreach(msg => {
	    val res = msg._2 match { case javaErrorBegin(_) => true; case _ => false }
	    
	    if ((msg._1 != lastLevel || res) && !localBuff.isEmpty) {
	      assert(lastLevel != null)
	      publishMsg(lastLevel, localBuff.mkString("\n"))
	      localBuff.clear()
	    }
	    lastLevel = msg._1
	    localBuff.append(msg._2)
	  })
	  if (!localBuff.isEmpty)
	    publishMsg(lastLevel, localBuff.mkString("\n"))
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
	
  def compilers(settings: Settings, libJar: File, compJar:File, compInterfaceJar: File): (ScalaSbtCompiler, JavaEclipseCompiler) = {
    val scalacInstance = ScalaCompilerConf(scalaVersion, libJar, compJar, compInterfaceJar)
    val scalac = new ScalaSbtCompiler(scalacInstance, reporter)
    val javac = new JavaEclipseCompiler(project.underlying, monitor)
    (scalac, javac)
  }
  
  //implicit val conLogger = sbt.ConsoleLogger()
  implicit val sbtBuildLogger = new SbtBuildLogger(_buildReporter)
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
  	  
      val (scalac, javac) = compilers(settings0, libJar, compJar.get.toFile, compInterfaceJar.get.toFile)
      // read settings properly
      //val cp = disintegrateClasspath(settings.classpath.value)
      val cp = allJarsAndLibrary._1.map(_.toFile)
      val conf = new BasicConfiguration(
              project, Seq(scalac.scalaInstance.libraryJar, compInterfaceJar.get.toFile) ++ cp)
      
      val analysisComp = new AnalysisCompile(conf, this, new SbtProgress())
  	  val order = project.storage.getString(SettingConverterUtil.convertNameToProperty(properties.ScalaPluginSettings.compileOrder.name))
      val result = analysisComp.doCompile(
              scalac, javac, sources, reporter, settings0, CompileOrderMapper(order))
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
        logger.error("Error in Scala compiler", e)
    }
    
    hasBuildErrors = reporter.hasErrors || hasErrors
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
