/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable
import scala.collection.mutable.{ ArrayBuffer, SynchronizedMap }
import scala.concurrent.SyncVar

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.compiler.problem.{ DefaultProblem, ProblemSeverities }
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.{ BatchSourceFile, Position, SourceFile }

import scala.tools.eclipse.javaelements.{
  ScalaCompilationUnit, ScalaIndexBuilder, ScalaJavaMapper, ScalaMatchLocator, ScalaStructureBuilder,
  ScalaOverrideIndicatorBuilder }
import scala.tools.eclipse.util.{ Cached, EclipseResource }

class ScalaPresentationCompiler(settings : Settings)
  extends Global(settings, new ScalaPresentationCompiler.PresentationReporter)
  with ScalaStructureBuilder with ScalaIndexBuilder with ScalaMatchLocator
  with ScalaOverrideIndicatorBuilder with ScalaJavaMapper with ScalaWordFinder with JVMUtils { self =>
  import ScalaPresentationCompiler._
  
  def presentationReporter = reporter.asInstanceOf[PresentationReporter]
  
  presentationReporter.compiler = this
  
  private val results = new mutable.HashMap[ScalaCompilationUnit, CachedCompilerResult] with SynchronizedMap[ScalaCompilationUnit, CachedCompilerResult] {
    override def default(k : ScalaCompilationUnit) = { val v = new CachedCompilerResult(k) ; put(k, v); v } 
  }
  
  private val problems = new mutable.HashMap[IFile, ArrayBuffer[IProblem]] with SynchronizedMap[IFile, ArrayBuffer[IProblem]] {
    override def default(k : IFile) = { val v = new ArrayBuffer[IProblem] ; put(k, v); v }
  }
  
  private def problemsOf(file : IFile) : List[IProblem] = {
    val ps = problems.remove(file)
    ps match {
      case Some(ab) => ab.toList
      case _ => Nil
    }
  }
  
  class CachedCompilerResult(scu : ScalaCompilationUnit)
    extends Cached[CompilerResultHolder] {
    override def create() : CompilerResultHolder = {
      val result = new CompilerResultHolder {
        val compiler = self
        val sourceFile = scu.createSourceFile
        val (body, problems) = {
          val file = scu.getCorrespondingResource.asInstanceOf[IFile]
          val typed = new SyncVar[Either[compiler.Tree, Throwable]]
          compiler.askType(sourceFile, true, typed)
          typed.get match {
            case Left(body0) =>
              val problems0 = if (file != null) problemsOf(file) else Nil
              (body0, problems0)
            case Right(thr) =>
              ScalaPlugin.plugin.logError("Failure in presentation compiler", thr)
              (compiler.EmptyTree, Nil)
          }
        }
      }

      val problemRequestor = scu.getProblemRequestor
      if (problemRequestor != null) {
        try {
          problemRequestor.beginReporting
          result.problems.map(problemRequestor.acceptProblem(_))
        } finally {
          problemRequestor.endReporting
        }
      }
    
      result    
    }
    
    override def destroy(crh : CompilerResultHolder) {}
  }
  
  def withCompilerResult[T](scu : ScalaCompilationUnit)(op : CompilerResultHolder => T) : T =
    results(scu).apply(op)
    
  def invalidateCompilerResult(scu : ScalaCompilationUnit) {
    results.get(scu).map(_.invalidate())
  }
  
  def discardCompilerResult(scu : ScalaCompilationUnit) {
    results.remove(scu)
    removeUnitOf(new BatchSourceFile(scu.getFile, Array[Char](0)))
  }

  override def logError(msg : String, t : Throwable) =
    ScalaPlugin.plugin.logError(msg, t)
    
  def destroy() {
    results.valuesIterator.foreach(_.invalidate)
    askShutdown
  }
}

object ScalaPresentationCompiler {
  
  trait CompilerResultHolder {
    val compiler : ScalaPresentationCompiler
    val sourceFile : SourceFile
    val body : compiler.Tree
    val problems : List[IProblem]
  }

  class PresentationReporter extends Reporter {
    var compiler : ScalaPresentationCompiler = null
    
    override def info0(pos: Position, msg: String, severity: Severity, force: Boolean): Unit = {
      severity.count += 1
      
      try {
        if(pos.isDefined) {
          val source = pos.source
          source.file match {
            case EclipseResource(file : IFile) =>
              val length = source.identifier(pos, compiler).map(_.length).getOrElse(0)
              compiler.problems(file) +=
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
      compiler.problems.clear
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
}
