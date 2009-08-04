/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable
import scala.collection.mutable.{ ArrayBuffer, SynchronizedMap }

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.compiler.problem.{ DefaultProblem, ProblemSeverities }
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.reporters.{ ConsoleReporter, Reporter }
import scala.tools.nsc.util.Position

import scala.tools.eclipse.javaelements.{ ScalaCompilationUnit, ScalaIndexBuilder, ScalaJavaMapper, ScalaStructureBuilder }
import scala.tools.eclipse.util.EclipseResource

class ScalaPresentationCompiler(settings : Settings)
  extends Global(settings, new ConsoleReporter(settings))
  with ScalaStructureBuilder with ScalaIndexBuilder with ScalaJavaMapper with ScalaWordFinder with JVMUtils {
  
  val problems = new mutable.HashMap[IFile, ArrayBuffer[IProblem]] with SynchronizedMap[IFile, ArrayBuffer[IProblem]] {
    override def default(k : IFile) = { val v = new ArrayBuffer[IProblem] ; put(k, v); v }
  }
  
  def problemsOf(file : IFile) : List[IProblem] = {
    val ps = problems.remove(file)
    ps match {
      case Some(ab) => ab.toList
      case _ => Nil
    }
  }

  reporter = new Reporter {
    
    override def info0(pos: Position, msg: String, severity: Severity, force: Boolean): Unit = {
      severity.count += 1
      
      try {
        if(pos.isDefined) {
          val source = pos.source
          source.file match {
            case EclipseResource(file : IFile) =>
              val length = source.identifier(pos, ScalaPresentationCompiler.this).map(_.length).getOrElse(0)
              problems(file) +=
                new DefaultProblem(
                  file.getFullPath.toString.toCharArray,
                  formatMessage(msg),
                  0,
                  new Array[String](0),
                  nscSeverityToEclipse(severity),
                  pos.startOrPoint,
                  pos.endOrPoint,
                  pos.line,
                  pos.column
                )
            case _ =>  
          }
        }
      } catch {
        case ex : UnsupportedOperationException => 
      }
    }
    
    override def reset {
      super.reset
      problems.clear
    }

    def nscSeverityToEclipse(severity : Severity) = 
      severity.id match {
        case 2 => ProblemSeverities.Error
        case 1 => ProblemSeverities.Warning
        case 0 => ProblemSeverities.Ignore
      }
    
    def formatMessage(msg : String) =
      msg.map{
        case '\n' => ' '
        case '\r' => ' '
        case c => c
      }.mkString("","","")
  }

  override def logError(msg : String, t : Throwable) =
    ScalaPlugin.plugin.logError(msg, t)
}
