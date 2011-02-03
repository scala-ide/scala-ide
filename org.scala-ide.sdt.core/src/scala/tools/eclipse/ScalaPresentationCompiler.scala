/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable
import scala.collection.mutable.{ ArrayBuffer, SynchronizedMap }

import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.compiler.problem.{ DefaultProblem, ProblemSeverities }
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.{ BatchSourceFile, Position, SourceFile }

import scala.tools.eclipse.javaelements.{
  ScalaCompilationUnit, ScalaIndexBuilder, ScalaJavaMapper, ScalaMatchLocator, ScalaStructureBuilder,
  ScalaOverrideIndicatorBuilder }
import scala.tools.eclipse.util.{ Cached, EclipseFile, EclipseResource }

class ScalaPresentationCompiler(project : ScalaProject, settings : Settings)
  extends Global(settings, new ScalaPresentationCompiler.PresentationReporter)
  with ScalaStructureBuilder with ScalaIndexBuilder with ScalaMatchLocator
  with ScalaOverrideIndicatorBuilder with ScalaJavaMapper with JVMUtils with LocateSymbol { self =>
  import ScalaPresentationCompiler._
  
  def presentationReporter = reporter.asInstanceOf[PresentationReporter]
  
  presentationReporter.compiler = this
  
  private val sourceFiles = new mutable.HashMap[ScalaCompilationUnit, BatchSourceFile] {
    override def default(k : ScalaCompilationUnit) = { 
      val v = k.createSourceFile
      ScalaPresentationCompiler.this.synchronized {
        get(k) match {
          case Some(v) => v
          case None => put(k, v); v
  	   }
      }} 
  }
  
  private val problems = new mutable.HashMap[AbstractFile, ArrayBuffer[IProblem]] with SynchronizedMap[AbstractFile, ArrayBuffer[IProblem]] {
    override def default(k : AbstractFile) = { val v = new ArrayBuffer[IProblem] ; put(k, v); v }
  }
  
  private def problemsOf(file : AbstractFile) : List[IProblem] = {
    val ps = problems.remove(file)
    ps match {
      case Some(ab) => ab.toList
      case _ => Nil
    }
  }
  
  def problemsOf(scu : ScalaCompilationUnit) : List[IProblem] = problemsOf(scu.file)
  
  private def clearProblemsOf(scu : ScalaCompilationUnit) {
    problems.remove(scu.file)
  }
  
  def withSourceFile[T](scu : ScalaCompilationUnit)(op : (SourceFile, ScalaPresentationCompiler) => T) : T =
    op(sourceFiles(scu), this)

  override def ask[A](op: () => A): A = if (Thread.currentThread == compileRunner) op() else super.ask(op)
  
  override def askTypeAt(pos: Position, response: Response[Tree]) = {
    if (Thread.currentThread == compileRunner) getTypedTreeAt(pos, response) else super.askTypeAt(pos, response)
  }

  override def askParsedEntered(source: SourceFile, keepLoaded: Boolean, response: Response[Tree]) {
    if (Thread.currentThread == compileRunner)
      getParsedEntered(source, keepLoaded, response)
    else
      super.askParsedEntered(source, keepLoaded, response)
  }
    
  def body(sourceFile : SourceFile) = {
    val tree = new Response[Tree]
    if (Thread.currentThread == compileRunner)
      getTypedTree(sourceFile, false, tree) else askType(sourceFile, false, tree)
    tree.get match {
      case Left(l) => l
      case Right(r) => throw r
    }
  }
  
  def withParseTree[T](sourceFile : SourceFile)(op : Tree => T) : T = {
    op(parseTree(sourceFile))
  }

  def withUntypedTree[T](sourceFile : SourceFile)(op : Tree => T) : T = {
    val tree = {
      val response = new Response[Tree]
      askParsedEntered(sourceFile, true, response)
      response.get match {
        case Left(tree) => tree 
        case Right(thr) => throw thr
      }
    }
    op(tree)
  }
    
  def askReload(scu : ScalaCompilationUnit, content : Array[Char]) {
    sourceFiles.get(scu) match {
      case None =>
      case Some(f) =>
        val newF = new BatchSourceFile(f.file, content)
        synchronized { sourceFiles(scu) = newF } 
        askReload(List(newF), new Response[Unit])
    }
    clearProblemsOf(scu)
  }
  
  def discardSourceFile(scu : ScalaCompilationUnit) {
    synchronized {
      sourceFiles.get(scu) match {
        case None =>
        case Some(source) =>
          removeUnitOf(source)
          sourceFiles.remove(scu)
      }
    }
    clearProblemsOf(scu)
  }

  override def logError(msg : String, t : Throwable) =
    ScalaPlugin.plugin.logError(msg, t)
    
  def destroy() {
    sourceFiles.keysIterator.foreach(_.scheduleReconcile)
    askShutdown
  }
}

object ScalaPresentationCompiler {
  class PresentationReporter extends Reporter {
    var compiler : ScalaPresentationCompiler = null
    
    override def info0(pos: Position, msg: String, severity: Severity, force: Boolean): Unit = {
      severity.count += 1
      
      try {
        if(pos.isDefined) {
          val source = pos.source
          source.file match {
            case ef@EclipseFile(file) =>
              val length = source.identifier(pos, compiler).map(_.length).getOrElse(0)
              compiler.debugLog(source.file.name + ":" + pos.line + ": " + msg)
              compiler.problems(ef) +=
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
              compiler.debugLog("WARNING: error coming from a file outside Eclipse: %s[%s]: %s".format(source.file.name, source.file.getClass, msg))
          }
        } else 
          if (compiler ne null) // compiler is null during the constructor, but info may be called already
            compiler.debugLog("[reporter] INFO: " + msg)
          else 
            println("[reporter] INFO: " + msg)
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
