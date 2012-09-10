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
import scala.tools.nsc.interactive.{ Global, InteractiveReporter, Problem }
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.{ BatchSourceFile, Position, SourceFile }
import scala.tools.eclipse.javaelements.{
  ScalaCompilationUnit,
  ScalaIndexBuilder,
  ScalaJavaMapper,
  ScalaMatchLocator,
  ScalaStructureBuilder,
  ScalaOverrideIndicatorBuilder
}
import scala.tools.eclipse.util.{ Cached, EclipseFile, EclipseResource }
import scala.tools.eclipse.logging.HasLogger
import scala.tools.nsc.util.FailedInterrupt
import scala.tools.nsc.symtab.Flags
import scala.tools.eclipse.completion.CompletionProposal
import org.eclipse.jdt.core.IMethod
import scala.tools.nsc.io.VirtualFile

class ScalaPresentationCompiler(project: ScalaProject, settings: Settings)
  extends Global(settings, new ScalaPresentationCompiler.PresentationReporter, project.underlying.getName)
  with ScalaStructureBuilder
  with ScalaIndexBuilder
  with ScalaMatchLocator
  with ScalaOverrideIndicatorBuilder
  with ScalaJavaMapper
  with JavaSig
  with JVMUtils
  with LocateSymbol
  with HasLogger
  with SymbolsCompatibility { self =>

  def presentationReporter = reporter.asInstanceOf[ScalaPresentationCompiler.PresentationReporter]
  presentationReporter.compiler = this

  /**
   * A map from compilation units to the BatchSourceFile that the compiler understands.
   *
   *  This map is populated by having a default source file created when calling 'apply',
   *  which currently happens in 'withSourceFile'.
   */
  private val sourceFiles = new mutable.HashMap[InteractiveCompilationUnit, SourceFile] {
    override def default(k: InteractiveCompilationUnit) = {
      val v = k.sourceFile()
      ScalaPresentationCompiler.this.synchronized {
        get(k) match {
          case Some(v) => v
          case None => put(k, v); v
        }
      }
    }
  }

  /**
   * Return the Scala compilation units that are currently maintained by this presentation compiler.
   */
  def compilationUnits: Seq[InteractiveCompilationUnit] = {
    val managedFiles = unitOfFile.keySet
    (for {
      (cu, sourceFile) <- sourceFiles
      if managedFiles(sourceFile.file)
    } yield cu).toSeq
  }

  def problemsOf(file: AbstractFile): List[IProblem] = {
    unitOfFile get file match {
      case Some(unit) =>
        val response = new Response[Tree]
        askLoadedTyped(unit.source, response)
        response.get
        unit.problems.toList flatMap presentationReporter.eclipseProblem
      case None =>
        logger.info("Missing unit for file %s when retrieving errors. Errors will not be shown in this file".format(file))
        logger.info(unitOfFile.toString)
        Nil
    }
  }

  def problemsOf(scu: ScalaCompilationUnit): List[IProblem] = problemsOf(scu.file)

  /**
   * Run the operation on the given compilation unit. If the source file is not yet tracked by
   *  the presentation compiler, a new BatchSourceFile is created and kept for future reference.
   */
  def withSourceFile[T](icu: InteractiveCompilationUnit)(op: (SourceFile, ScalaPresentationCompiler) => T): T =
    op(sourceFiles(icu), this)

  def body(sourceFile: SourceFile) = {
    val response = new Response[Tree]
    askType(sourceFile, false, response)
    response.get match {
      case Left(tree) => tree
      case Right(exc) => throw exc
    }
  }

  def loadedType(sourceFile: SourceFile) = {
    val response = new Response[Tree]
    askLoadedTyped(sourceFile, response)
    response.get match {
      case Left(tree) => tree
      case Right(exc) => throw exc
    }
  }

  def withParseTree[T](sourceFile: SourceFile)(op: Tree => T): T = {
    op(parseTree(sourceFile))
  }

  def withStructure[T](sourceFile: SourceFile, keepLoaded: Boolean = false)(op: Tree => T): T = {
    val tree = {
      val response = new Response[Tree]
      askStructure(keepLoaded)(sourceFile, response)
      response.get match {
        case Left(tree) => tree
        case Right(thr) => throw thr
      }
    }
    op(tree)
  }

  /**
   * Perform `op' on the compiler thread. Catch all exceptions, and return
   *  None if an exception occured. TypeError and FreshRunReq are printed to
   *  stdout, all the others are logged in the platform error log.
   */
  def askOption[A](op: () => A): Option[A] =
    try Some(ask(op))
    catch {
      case fi: FailedInterrupt =>
        fi.getCause() match {
          case e: TypeError =>
            logger.info("TypeError in ask:\n" + e)
            None
          case f: FreshRunReq =>
            logger.info("FreshRunReq in ask:\n" + f)
            None
          case e @ InvalidCompanions(c1, c2) =>
            reporter.warning(c1.pos, e.getMessage)
            None
          case e: InterruptedException =>
            Thread.currentThread().interrupt()
            logger.info("interrupted exception in askOption")
            None

          case e =>
            eclipseLog.error("Error during askOption", e)
            None
        }
      case e: Throwable =>
        eclipseLog.error("Error during askOption", e)
        None
    }

  /**
   * Ask to put scu in the beginning of the list of files to be typechecked.
   *
   *  If the file has not been 'reloaded' first, it does nothing.
   */
  def askToDoFirst(scu: ScalaCompilationUnit) {
    sourceFiles.get(scu) foreach askToDoFirst
  }

  /**
   * Reload the given compilation unit. If this CU is not tracked by the presentation
   *  compiler, it's a no-op.
   *
   *  TODO: This logic seems broken: the only way to add a source file to the sourceFiles
   *        map is by a call to 'withSourceFile', which creates a default batch source file.
   *        Come back to this and make it more explicit.
   */
  def askReload(scu: ScalaCompilationUnit, content: Array[Char]): Response[Unit] = {
    val res = new Response[Unit]

    sourceFiles.get(scu) match {
      case Some(f) =>
        val newF = new BatchSourceFile(f.file, content)
        synchronized { sourceFiles(scu) = newF }

        // avoid race condition by looking up the source file, as someone else 
        // might have swapped it in the meantime
        askReload(List(sourceFiles(scu)), res)
      case None =>
        res.set(()) // make sure nobody blocks waiting indefinitely
    }
    res
  }

  def filesDeleted(files: List[ScalaCompilationUnit]) {
    logger.info("files deleted:\n" + (files map (_.getPath) mkString "\n"))
    synchronized {
      val srcs = files.map(sourceFiles remove _).foldLeft(List[SourceFile]()) {
        case (acc, None) => acc
        case (acc, Some(f)) => f :: acc
      }
      if (!srcs.isEmpty)
        askFilesDeleted(srcs, new Response[Unit])
    }
  }

  def discardSourceFile(scu: ScalaCompilationUnit) {
    logger.info("discarding " + scu.getPath)
    synchronized {
      sourceFiles.get(scu) foreach { source =>
        removeUnitOf(source)
        sourceFiles.remove(scu)
      }
    }
  }

  def withResponse[A](op: Response[A] => Any): Response[A] = {
    val response = new Response[A]
    op(response)
    response
  }

  override def logError(msg: String, t: Throwable) =
    eclipseLog.error(msg, t)

  def destroy() {
    logger.info("shutting down presentation compiler on project: " + project)
    askShutdown()
  }

  /**
   * Add a new completion proposal to the buffer. Skip constructors and accessors.
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
      if (sym.isMethod) {
        name +
          (if (!sym.typeParams.isEmpty) sym.typeParams.map { _.name }.mkString("[", ",", "]") else "") +
          tpe.paramss.map(_.map(_.tpe.toString).mkString("(", ", ", ")")).mkString +
          ": " + tpe.finalResultType.toString
      } else name
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

    val scalaParamNames = for {
      section <- sym.paramss
      if section.nonEmpty && !section.head.isImplicit
    } yield for (param <- section) yield param.name.toString

    val paramNames = if (sym.isJavaDefined) {
      getJavaElement(sym) collect {
        case method: IMethod => List(method.getParameterNames.toList)
      } getOrElse scalaParamNames
    } else scalaParamNames

    val contextInfo = for {
      (names, syms) <- paramNames.zip(sym.paramss)
    } yield for { (name, sym) <- names.zip(syms) } yield "%s: %s".format(name, sym.tpe)

    val contextString = contextInfo.map(_.mkString("(", ", ", ")")).mkString("")

    import scala.tools.eclipse.completion.HasArgs
    CompletionProposal(kind,
      start,
      name,
      signature,
      contextString,
      container,
      relevance,
      HasArgs.from(sym.paramss),
      sym.isJavaDefined,
      paramNames,
      sym.fullName,
      false)
  }

  override def inform(msg: String): Unit =
    logger.debug("[%s]: %s".format(project, msg))
}

object ScalaPresentationCompiler {
  class PresentationReporter extends InteractiveReporter {
    var compiler: ScalaPresentationCompiler = null

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
        val fileName =
          source.file match {
            case EclipseFile(file) =>
              Some(file.getFullPath().toString.toCharArray)
            case vf: VirtualFile =>
              Some(vf.path.toCharArray)
            case _ =>
              None
          }
        fileName.map(new DefaultProblem(
          _,
          formatMessage(msg),
          0,
          new Array[String](0),
          nscSeverityToEclipse(severityLevel),
          pos1.startOrPoint,
          math.max(pos1.startOrPoint, pos1.endOrPoint - 1),
          pos1.line,
          pos1.column))
      } else None
    }

    def formatMessage(msg: String) = msg.map {
      case '\n' => ' '
      case '\r' => ' '
      case c => c
    }
  }
}


