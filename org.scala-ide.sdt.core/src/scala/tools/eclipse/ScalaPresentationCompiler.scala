/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.nsc.interactive.FreshRunReq
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
import scala.tools.nsc.util.FailedInterrupt
import scala.tools.nsc.symtab.Flags
import scala.tools.eclipse.completion.CompletionProposal

class ScalaPresentationCompiler(project : ScalaProject, settings : Settings)
  extends Global(settings, new ScalaPresentationCompiler.PresentationReporter, project.underlying.getName)
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
      }
    } 
  }
  
  private def problemsOf(file : AbstractFile) : List[IProblem] = {
    unitOfFile get file match {
      case Some(unit) => 
        val response = new Response[Tree]
        askLoadedTyped(unit.source, response)
        response.get
        unit.problems.toList flatMap presentationReporter.eclipseProblem
      case None => 
        Nil
    }
  }
  
  def problemsOf(scu : ScalaCompilationUnit) : List[IProblem] = problemsOf(scu.file)
  
  def withSourceFile[T](scu : ScalaCompilationUnit)(op : (SourceFile, ScalaPresentationCompiler) => T) : T =
    op(sourceFiles(scu), this)
    
  def body(sourceFile : SourceFile) = {
    val response = new Response[Tree]
    askType(sourceFile, false, response)
    response.get match {
      case Left(tree) => tree
      case Right(exc) => throw exc
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

  /** Perform `op' on the compiler thread. Catch all exceptions, and return 
   *  None if an exception occured. TypeError and FreshRunReq are printed to
   *  stdout, all the others are logged in the platform error log.
   */
  def askOption[A](op: () => A): Option[A] =
    try Some(ask(op))
    catch {
      case fi: FailedInterrupt =>
        fi.getCause() match {
          case e: TypeError =>
            println("TypeError in ask:\n" + e)
            None
          case f: FreshRunReq =>
            println("FreshRunReq in ask:\n" + f)
             None
          case e @ InvalidCompanions(c1, c2) =>
            reporter.warning(c1.pos, e.getMessage)
            None
          case e: InterruptedException =>
            Thread.currentThread().interrupt()
            println("interrupted exception in askOption")
            None
            
          case e =>
            ScalaPlugin.plugin.logError("Error during askOption", e)
            None
        }
      case e =>
        ScalaPlugin.plugin.logError("Error during askOption", e)
        None
    }
  
  def askReload(scu : ScalaCompilationUnit, content : Array[Char]) {
    sourceFiles.get(scu) foreach { f =>
      val newF = new BatchSourceFile(f.file, content)
      synchronized { sourceFiles(scu) = newF } 
      askReload(List(newF), new Response[Unit])
    }
  }
  
  def filesDeleted(files : List[ScalaCompilationUnit]) {
    println("files deleted:\n" + (files map (_.getPath) mkString "\n"))
    synchronized {
      val srcs = files.map(sourceFiles remove _).foldLeft(List[SourceFile]()) {
        case (acc, None) => acc
        case (acc, Some(f)) => f::acc
      }
      if (!srcs.isEmpty)
        askFilesDeleted(srcs, new Response[Unit])
    }
  }

 def discardSourceFile(scu : ScalaCompilationUnit) {
   println("discarding " + scu.getPath)
   synchronized {
     sourceFiles.get(scu) foreach { source =>
       removeUnitOf(source)
       sourceFiles.remove(scu)
     }
   }
 }

  override def logError(msg : String, t : Throwable) =
    ScalaPlugin.plugin.logError(msg, t)
    
  def destroy() {
    println("shutting down presentation compiler on project: " + project)
    // TODO: Why is this needed? (ID)
    sourceFiles.keysIterator.foreach(_.scheduleReconcile)
    askShutdown
  }
  

  /** Add a new completion proposal to the buffer. Skip constructors and accessors.
   * 
   *  Computes a very basic relevance metric based on where the symbol comes from 
   *  (in decreasing order of relevance):
   *    - members defined by the owner
   *    - inherited members
   *    - members added by views
   *    - packages
   *    - members coming from Any/AnyRef/Object
   *    
   *  TODO We should have a more refined strategy based on the context (inside an import, case
   *       pattern, 'new' call, etc.)
   */
  def mkCompletionProposal(start: Int, sym: Symbol, tpe: Type, inherited: Boolean, viaView: Symbol): CompletionProposal = {
    import scala.tools.eclipse.completion.MemberKind._
    
     val kind = if (sym.isSourceMethod && !sym.hasFlag(Flags.ACCESSOR | Flags.PARAMACCESSOR)) Def
                 else if (sym.isPackage) Package
                 else if (sym.isClass) Class
                 else if (sym.isTrait) Trait
                 else if (sym.isPackageObject) PackageObject
                 else if (sym.isModule) Object
                 else if (sym.isType) Type
                 else Val
     val name = sym.decodedName
     val signature = 
       if (sym.isMethod) { name +
           (if(!sym.typeParams.isEmpty) sym.typeParams.map{_.name}.mkString("[", ",", "]") else "") +
           tpe.paramss.map(_.map(_.tpe.toString).mkString("(", ", ", ")")).mkString +
           ": " + tpe.finalResultType.toString}
       else name
     val container = sym.owner.enclClass.fullName
     
     // rudimentary relevance, place own members before ineherited ones, and before view-provided ones
     var relevance = 100
     if (inherited) relevance -= 10
     if (viaView != NoSymbol) relevance -= 20
     if (sym.isPackage) relevance -= 30
     // theoretically we'd need an 'ask' around this code, but given that
     // Any and AnyRef are definitely loaded, we call directly to definitions.
     if (sym.owner == definitions.AnyClass
         || sym.owner == definitions.AnyRefClass
         || sym.owner == definitions.ObjectClass) { 
       relevance -= 40
     }
     
     val contextString = sym.paramss.map(_.map(p => "%s: %s".format(p.decodedName, p.tpe)).mkString("(", ", ", ")")).mkString("")
     
     import scala.tools.eclipse.completion.HasArgs
     CompletionProposal(kind,
         start, 
         name, 
         signature, 
         contextString, 
         container,
         relevance,
         HasArgs.from(sym.paramss),
         sym.isJavaDefined)
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


