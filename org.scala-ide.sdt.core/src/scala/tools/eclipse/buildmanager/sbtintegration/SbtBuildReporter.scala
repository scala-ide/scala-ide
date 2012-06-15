package scala.tools.eclipse.buildmanager.sbtintegration

import java.io.File

import xsbti.Maybe

import scala.tools.eclipse.buildmanager.BuildReporter
import scala.tools.eclipse.buildmanager.BuildProblem
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.{ Position, NoPosition, FakePos }
import scala.tools.nsc.reporters.Reporter
import scala.tools.eclipse.util.{ EclipseResource, FileUtils }

/**  An Sbt Reporter that forwards to an underlying [[BuildReporter]]
 */
private[sbtintegration] class SbtBuildReporter(underlying: BuildReporter) extends xsbti.Reporter {
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
        val ifile = EclipseResource.fromString(srcpath0.get, underlying.project0.underlying.getFullPath)
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
  
  def reset() = underlying.reset
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

/** Helper object to convert between Sbt and Scala positions and reporter
 *  severities.
 */
private object SbtConverter {
  // This piece of code is directly copied from sbt sources. There
  // doesn't seem to be other way atm to convert between sbt and scala compiler reporting
  private[this] def o[T](t: Option[T]): Option[T] = t
  private[this] def o[T](t: T): Option[T] = Option(t)

  
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
      val offsetO = o(pos.point)
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