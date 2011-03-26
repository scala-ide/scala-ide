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
import scala.tools.nsc.interactive.{Global, InteractiveReporter, Problem}
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.{ BatchSourceFile, Position, SourceFile }

import scala.tools.eclipse.javaelements.{
  ScalaCompilationUnit, ScalaIndexBuilder, ScalaJavaMapper, ScalaMatchLocator, ScalaStructureBuilder,
  ScalaOverrideIndicatorBuilder }
import scala.tools.eclipse.util.{ Cached, EclipseFile, EclipseResource }

class ScalaPresentationCompiler(project : ScalaProject, settings : Settings)
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
  
  private def problemsOf(file : AbstractFile) : List[IProblem] = {
    unitOfFile get file match {
      case Some(unit) => 
        val response = new Response[Tree]
        askLoadedTyped(unit.source, response)
        response.get
        val result = unit.problems.toList flatMap presentationReporter.eclipseProblem
        //unit.problems.clear()
        result
      case None => 
        Nil
    }
  }
  
  def problemsOf(scu : ScalaCompilationUnit) : List[IProblem] = problemsOf(scu.file)
  
  def withSourceFile[T](scu : ScalaCompilationUnit)(op : (SourceFile, ScalaPresentationCompiler) => T) : T =
    op(sourceFiles(scu), this)

  def body(sourceFile : SourceFile) = {
    val tree = new Response[Tree]
    askType(sourceFile, false, tree)
    tree.get match {
      case Left(l) => l
      case Right(r) => throw r
    }
  }
  
  def withParseTree[T](sourceFile : SourceFile)(op : Tree => T) : T = {
    op(parseTree(sourceFile))
  }

  def withStructure[T](sourceFile : SourceFile)(op : Tree => T) : T = {
    val tree = {
      val response = new Response[Tree]
      askStructure(sourceFile, response)
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
  }
  
  def discardSourceFile(scu : ScalaCompilationUnit) {
    println("discarding " + scu.getPath)
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
    println("shutting down presentation compiler on project: " + project)
    sourceFiles.keysIterator.foreach(_.scheduleReconcile)
    askShutdown
  }
}

object ScalaPresentationCompiler {
  class PresentationReporter extends InteractiveReporter {
    var compiler : ScalaPresentationCompiler = null
      
    def nscSeverityToEclipse(severityLevel: Int) = 
      severityLevel match {
        case ERROR.id => ProblemSeverities.Error
        case WARNING.id => ProblemSeverities.Warning
        case INFO.id => ProblemSeverities.Ignore
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


