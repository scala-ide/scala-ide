/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.util.Tracer
import scala.collection.mutable
import scala.collection.mutable.{ ArrayBuffer, SynchronizedMap }
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.compiler.problem.{ DefaultProblem, ProblemSeverities }
import scala.tools.nsc.interactive.{Global, InteractiveReporter, Problem}
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.{ BatchSourceFile, Position, SourceFile }
import scala.tools.eclipse.javaelements.{
  ScalaIndexBuilder, ScalaJavaMapper, ScalaMatchLocator, ScalaStructureBuilder,
  ScalaOverrideIndicatorBuilder }
import scala.tools.eclipse.util.{ Cached, EclipseFile, EclipseResource, IDESettings }
import scala.tools.nsc.interactive.compat.Settings
import scala.tools.nsc.interactive.compat.conversions._

class ScalaPresentationCompiler(settings : Settings)
  extends Global(settings, new ScalaPresentationCompiler.PresentationReporter)
  with ScalaStructureBuilder 
  with ScalaIndexBuilder 
  with ScalaMatchLocator
  with ScalaOverrideIndicatorBuilder 
  with ScalaJavaMapper 
  with JVMUtils 
  with LocateSymbol { self =>
  
  def presentationReporter = reporter.asInstanceOf[ScalaPresentationCompiler.PresentationReporter]
  presentationReporter.compiler = this
  
  private val sourceFiles = new mutable.HashMap[AbstractFile, BatchSourceFile]{
    override def default(k : AbstractFile) = { 
      val v = new BatchSourceFile(k)
      ScalaPresentationCompiler.this.synchronized {
        get(k) match {
          case Some(v) => v
          case None => put(k, v); v
        }
      }
    }
  }
  
  def askRunLoadedTyped(file : AbstractFile) = {
    for (source <- (sourceFiles get file)) {
      val response = new Response[Tree]
      askLoadedTyped(source, response)
      val timeout = IDESettings.timeOutBodyReq.value //Defensive use a timeout
      response.get(timeout) orElse { throw new AsyncGetTimeoutException(timeout, "askRunLoadedTyped(" + file + ")") }
    }
  }
        
  def askProblemsOf(file : AbstractFile) : List[IProblem] = ask{() =>
    val b = unitOfFile get file match {
      case Some(unit) => 
        val result = unit.problems.toList flatMap presentationReporter.eclipseProblem
        //unit.problems.clear()
        result
      case None => 
        Tracer.println("no unit for " + file)
        Nil
    }
    Tracer.println("problems of " + file + " : " + b.size)
    b
  }
  
  //def problemsOf(scu : IFile) : List[IProblem] = problemsOf(FileUtils.toAbstractFile(scu))
  
  def withSourceFile[T](scu : AbstractFile)(op : (SourceFile, ScalaPresentationCompiler) => T) : T =
    op(sourceFiles(scu), this)

  override def ask[A](op: () => A): A = {
    Tracer.println("ask " + op)
    if (Thread.currentThread == compileRunner) op() else super.ask(op)
  }
  
  override def askTypeAt(pos: Position, response: Response[Tree]) = {
    Tracer.println("askTypeAt")
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
    val timeout = IDESettings.timeOutBodyReq.value //Defensive use a timeout see issue_0003 issue_0004
    tree.get(timeout) match {
      case None => throw new AsyncGetTimeoutException(timeout, "body(" + sourceFile + ")")
      case Some(x) => x match {
        case Left(l) => l
        case Right(r) => throw new AsyncGetException(r, "body(" + sourceFile + ")")
      }
    }
  }
  
  def withParseTree[T](sourceFile : SourceFile)(op : Tree => T) : T = {
    op(parseTree(sourceFile))
  }

  def withStructure[T](sourceFile : SourceFile)(op : Tree => T) : T = {
    val tree = {
      val response = new Response[Tree]
      askStructure(sourceFile, response)
      val timeout = IDESettings.timeOutBodyReq.value //Defensive use a timeout see issue_0003 issue_0004
      response.get(timeout) match {
        case None => throw new AsyncGetTimeoutException(timeout, "withStructure(" + sourceFile + ")")
        case Some(x) => x match {
          case Left(tree) => tree
          case Right(r) => throw new AsyncGetException(r, "withStructure(" + sourceFile + ")")
        }
      }      
    }
    op(tree)
  }
  
  def askReload(scu : AbstractFile, content : Array[Char]) {
    Tracer.println("askReload 3: " + scu)
//    sourceFiles.get(scu) match {
//      case None =>
//      case Some(f) => {
        val newF = new BatchSourceFile(scu, content) 
        Tracer.println("content length :" + content.length)
        synchronized { sourceFiles(scu) = newF } 
        askReload(List(newF), new Response[Unit])
//      }
//    }
  }
  
  def discardSourceFile(scu : AbstractFile) {
    Tracer.println("discarding " + scu)
	synchronized {
      sourceFiles.get(scu) match {
        case None =>
        case Some(source) =>
          removeUnitOf(source)
          sourceFiles.remove(scu)
      }
    }
  }

  override def logError(msg : String, t : Throwable) =
    ScalaPlugin.plugin.logError(msg, t)
    
  def destroy() {
    askShutdown
  }
}

object ScalaPresentationCompiler {
  class PresentationReporter extends InteractiveReporter {
    var compiler : ScalaPresentationCompiler = null
      
    def nscSeverityToEclipse(severityLevel: Int) = 
      severityLevel match {
        case v if v == ERROR.id => ProblemSeverities.Error
        case v if v == WARNING.id => ProblemSeverities.Warning
        case v if v == INFO.id => ProblemSeverities.Ignore
      }
    
    def eclipseProblem(prob: Problem): Option[IProblem] = {
      import prob._
      if (pos.isDefined) {
          val source = pos.source
          val pos1 = pos.toSingleLine
          source.file match {
            case ef@EclipseFile(file) =>
              Some(
                new DefaultProblem(
                  file.getFullPath.toString.toCharArray,
                  formatMessage(msg),
                  0,
                  new Array[String](0),
                  nscSeverityToEclipse(severityLevel),
                  pos1.startOrPoint,
                  pos1.endOrPoint,
                  pos1.line,
                  pos1.column
                ))
            case af : AbstractFile =>
              Some(
                new DefaultProblem(
                  af.path.toCharArray,
                  formatMessage(msg),
                  0,
                  new Array[String](0),
                  nscSeverityToEclipse(severityLevel),
                  pos1.startOrPoint,
                  pos1.endOrPoint,
                  pos1.line,
                  pos1.column
                ))
            case _ => None
          }
        } else None
      }   

      def formatMessage(msg : String) = msg.map{
        case '\n' => ' '
        case '\r' => ' '
        case c => c
      }
  }
}

/**
 * Wrapping exception for Exception raise in an other thread (from async call).
 * Help to find where is the cause on caller/context (ask+get).
 * Message of the exception include the hashCode of the cause, because a cause Exception can be wrapped several time.
 */
class AsyncGetException(cause : Throwable, contextInfo : String = "") extends Exception("origin (" + cause.hashCode + ") : " + cause.getMessage + " [" + contextInfo + "]", cause)
class AsyncGetTimeoutException(timeout : Int, contextInfo : String = "") extends Exception("timeout (" + timeout + " ms) expired [" + contextInfo + "]")
