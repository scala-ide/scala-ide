/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.util.Tracer
import scala.collection.mutable
import scala.collection.mutable.{ ArrayBuffer, SynchronizedMap }

import org.eclipse.core.resources.IFile
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
import scala.tools.eclipse.util.{ Cached, EclipseFile, EclipseResource, IDESettings }

class ScalaPresentationCompiler(settings : Settings)
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
  
  private val problems = new mutable.HashMap[IFile, ArrayBuffer[IProblem]] with SynchronizedMap[IFile, ArrayBuffer[IProblem]] {
    override def default(k : IFile) = { val v = new ArrayBuffer[IProblem] ; put(k, v); v }
  }
  
  private def fileOf(scu : ScalaCompilationUnit) =
    try { Some(scu.getCorrespondingResource.asInstanceOf[IFile]) } catch { case _ => None } 
  
  private def problemsOf(file : IFile) : List[IProblem] = {
    val ps = problems.remove(file)
    ps match {
      case Some(ab) => ab.toList
      case _ => Nil
    }
  }
  
  def problemsOf(scu : ScalaCompilationUnit) : List[IProblem] = fileOf(scu) match {
    case Some(file) => problemsOf(file)
    case None => Nil
  }
  
  private def clearProblemsOf(file : IFile) : Unit = problems.remove(file)
  
  private def clearProblemsOf(scu : ScalaCompilationUnit) : Unit = fileOf(scu) match {
    case Some(file) => clearProblemsOf(file)
    case None =>
  }
  
  def withSourceFile[T](scu : ScalaCompilationUnit)(op : (SourceFile, ScalaPresentationCompiler) => T) : T =
    op(sourceFiles(scu), this)

  override def ask[A](op: () => A): A = {
    Tracer.println("ask " + op)
    //Thread.dumpStack
    if (Thread.currentThread == compileRunner) op() else super.ask(op)
  }
  
  override def askTypeAt(pos: Position, response: Response[Tree]) {
    Tracer.println("askTypeAt")
    if (Thread.currentThread == compileRunner) getTypedTreeAt(pos, response) else super.askTypeAt(pos, response)
  }
    
  def body(sourceFile : SourceFile) = {
    val tree = new Response[Tree]
    if (Thread.currentThread == compileRunner)
      getTypedTree(sourceFile, false, tree) else askType(sourceFile, false, tree)
    val timeout = IDESettings.timeOutBodyReq.value //Defensive use a timeout see issue_0003 issue_0004
    tree.get(timeout) match {
      case None => throw new AsyncGetTimeoutException(timeout, "body(" + sourceFile + ")")
      case Some(x) => x match {
        case Left(l) => l
        case Right(r) => throw new AsyncGetException(r, "body(" + sourceFile + ")")
      }
    }
  }

  def withUntypedTree[T](sourceFile : SourceFile)(op : Tree => T) : T = {
    val tree = ask { () => 
      val u = unitOf(sourceFile)
      if (u.status < JustParsed) parse(u)
      u.body
    }
    op(tree)
  }
  
  def askReload(scu : ScalaCompilationUnit, content : Array[Char]) {
    Tracer.println("askReload")
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
            case EclipseResource(file : IFile) =>
              val length = source.identifier(pos, compiler).map(_.length).getOrElse(0)
              compiler.debugLog(source.file.name + ":" + pos.line + ": " + msg)
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
              Tracer.println("WARNING: error coming from a file outside Eclipse: %s[%s]: %s".format(source.file.name, source.file.getClass, msg))
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

/**
 * Wrapping exception for Exception raise in an other thread (from async call).
 * Help to find where is the cause on caller/context (ask+get).
 * Message of the exception include the hashCode of the cause, because a cause Exception can be wrapped several time.
 */
class AsyncGetException(cause : Throwable, contextInfo : String = "") extends Exception("origin (" + cause.hashCode + ") : " + cause.getMessage + " [" + contextInfo + "]", cause)
class AsyncGetTimeoutException(timeout : Int, contextInfo : String = "") extends Exception("timeout (" + timeout + " ms) expired [" + contextInfo + "]")
