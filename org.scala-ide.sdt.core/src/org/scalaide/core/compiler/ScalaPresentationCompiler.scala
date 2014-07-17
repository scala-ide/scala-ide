package org.scalaide.core.compiler

import scala.tools.nsc.interactive.FreshRunReq
import scala.collection.concurrent
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.SynchronizedMap
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.interactive.InteractiveReporter
import scala.tools.nsc.interactive.Problem
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.internal.util.Position
import scala.reflect.internal.util.SourceFile
import org.scalaide.core.completion.CompletionContext
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.internal.jdt.search.ScalaIndexBuilder
import org.scalaide.core.internal.jdt.model.ScalaJavaMapper
import org.scalaide.core.internal.jdt.search.ScalaMatchLocator
import org.scalaide.core.internal.jdt.model.ScalaStructureBuilder
import org.scalaide.ui.internal.jdt.model.ScalaOverrideIndicatorBuilder
import org.scalaide.core.resources.EclipseFile
import org.scalaide.core.resources.EclipseResource
import org.scalaide.logging.HasLogger
import scala.tools.nsc.util.FailedInterrupt
import scala.tools.nsc.symtab.Flags
import org.scalaide.core.completion.CompletionProposal
import org.eclipse.jdt.core.IMethod
import scala.tools.nsc.io.VirtualFile
import scala.tools.nsc.interactive.MissingResponse
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.extensions.SourceFileProviderRegistry
import org.eclipse.core.runtime.Path
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.internal.core.util.Util
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.core.ScalaPlugin
import org.scalaide.util.internal.ScalaWordFinder
import scalariform.lexer.{ScalaLexer, ScalaLexerException}

class ScalaPresentationCompiler(project: ScalaProject, settings: Settings) extends {
  /*
   * Lock object for protecting compiler names. Names are cached in a global `Array[Char]`
   * and concurrent access may lead to overwritten names.
   *
   * @note This field is EARLY because `newTermName` is hit during construction of the superclass `Global`,
   *       and the lock object has to be constructed already.
   */
  private val nameLock = new Object

} with Global(settings, new ScalaPresentationCompiler.PresentationReporter, project.underlying.getName)
  with ScalaStructureBuilder
  with ScalaIndexBuilder
  with ScalaMatchLocator
  with ScalaOverrideIndicatorBuilder
  with ScalaJavaMapper
  with JavaSig
  with LocateSymbol
  with CompilerApiExtensions
  with HasLogger { self =>

  def presentationReporter = reporter.asInstanceOf[ScalaPresentationCompiler.PresentationReporter]
  presentationReporter.compiler = this

  def compilationUnits: List[InteractiveCompilationUnit] = {
    val managedFiles = unitOfFile.keySet.toList
    for {
      f <- managedFiles.collect { case ef: EclipseFile => ef }
      icu <- SourceFileProviderRegistry.getProvider(f.workspacePath).createFrom(f.workspacePath)
      if icu.exists
    } yield icu
  }

  /** Ask to reload all units managed by this presentation compiler */
  @deprecated("use askReloadManagedUnits instead", "4.0.0")
  def reconcileOpenUnits() = askReloadManagedUnits()

  def askReloadManagedUnits() {
    askReload(compilationUnits)
  }

  /**
   * The set of compilation units to be reloaded at the next refresh round.
   * Refresh rounds can be triggered by the reconciler, but also interactive requests
   * (e.g. completion)
   */
  private val scheduledUnits = new scala.collection.mutable.HashMap[InteractiveCompilationUnit,Array[Char]]

  /**
   * Add a compilation unit (CU) to the set of CUs to be Reloaded at the next refresh round.
   */
  def scheduleReload(icu : InteractiveCompilationUnit, contents:Array[Char]) : Unit = {
    scheduledUnits.synchronized { scheduledUnits += ((icu, contents)) }
  }

  /**
   * Reload the scheduled compilation units and reset the set of scheduled reloads.
   *  For any CU unknown by the compiler at reload, this is a no-op.
   */
  def flushScheduledReloads(): Response[Unit] = {
    val res = new Response[Unit]
    scheduledUnits.synchronized {
      val reloadees = scheduledUnits.filter{(scu:(InteractiveCompilationUnit, Array[Char])) => compilationUnits.contains(scu._1)}.toList

      if (reloadees.isEmpty) res.set(())
      else {
        val reloadFiles = reloadees map { case (s, c) => s.sourceFile(c) }
        askReload(reloadFiles, res)
        res.get
      }
      scheduledUnits.clear()
    }
    res
  }

  override def askFilesDeleted(sources: List[SourceFile], response: Response[Unit]) = {
    flushScheduledReloads()
    super.askFilesDeleted(sources, response)
  }

  override def askLinkPos(sym: Symbol, source: SourceFile, response: Response[Position]) = {
    flushScheduledReloads()
    super.askLinkPos(sym, source, response)
  }

  override def askParsedEntered(source: SourceFile, keepLoaded: Boolean, response: Response[Tree]) = {
    flushScheduledReloads()
    super.askParsedEntered(source, keepLoaded, response)
  }

  override def askScopeCompletion(pos: Position, response: Response[List[Member]]) = {
    flushScheduledReloads()
    super.askScopeCompletion(pos, response)
  }

  override def askToDoFirst(source: SourceFile) = {
    flushScheduledReloads()
    super.askToDoFirst(source)
  }

  override def askTypeAt(pos: Position, response: Response[Tree]) = {
    flushScheduledReloads()
    super.askTypeAt(pos, response)
  }

  override def askTypeCompletion(pos: Position, response: Response[List[Member]]) = {
    flushScheduledReloads()
    super.askTypeCompletion(pos, response)
  }

  override def askLoadedTyped(sourceFile: SourceFile, keepLoaded: Boolean, response: Response[Tree]) = {
    flushScheduledReloads()
    super.askLoadedTyped(sourceFile, keepLoaded, response)
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

  @deprecated("Use `InteractiveCompilationUnit.withSourceFile` instead", since = "4.0.0")
  def withSourceFile[T](icu: InteractiveCompilationUnit)(op: (SourceFile, ScalaPresentationCompiler) => T): T =
    icu.withSourceFile(op) getOrElse (throw new UnsupportedOperationException("Use `InteractiveCompilationUnit.withSourceFile`"))

  @deprecated("Use loadedType instead.", "4.0.0")
  def body(sourceFile: SourceFile, keepLoaded: Boolean = false): Either[Tree, Throwable] = loadedType(sourceFile, keepLoaded)

  def loadedType(sourceFile: SourceFile, keepLoaded: Boolean = false): Either[Tree, Throwable] = {
    val response = new Response[Tree]
    if (self.onCompilerThread)
      throw ScalaPresentationCompiler.InvalidThread("Tried to execute `askLoadedType` while inside `ask`")
    askLoadedTyped(sourceFile, keepLoaded, response)
    response.get
  }

  def withParseTree[T](sourceFile: SourceFile)(op: Tree => T): T = {
    op(parseTree(sourceFile))
  }

  def withStructure[T](sourceFile: SourceFile, keepLoaded: Boolean = false)(op: Tree => T): Either[T, Throwable] = {
    val response = new Response[Tree]
    askStructure(keepLoaded)(sourceFile, response)
    response.get.left.map(op)
  }

  /** Ask with a default timeout. Keep around for compatibility with the m2 release. */
  def askOption[A](op: () => A): Option[A] = askOption(op, 10000)

  /** Perform `op' on the compiler thread. Catch all exceptions, and return
   *  None if an exception occurred. TypeError and FreshRunReq are printed to
   *  stdout, all the others are logged in the platform error log.
   */
  def askOption[A](op: () => A, timeout: Int): Option[A] = {
    val response = askForResponse(op)

    val res = if (ScalaPlugin.plugin.noTimeoutMode) Some(response.get) else response.get(timeout)

    res match {
      case None =>
        eclipseLog.info("Timeout in askOption", new Throwable) // log a throwable for its stacktrace
        None

      case Some(result) =>
        result match {
          case Right(fi: FailedInterrupt) =>
            fi.getCause() match {
              case e: TypeError                  => logger.info("TypeError in ask:\n" + e)
              case f: FreshRunReq                => logger.info("FreshRunReq in ask:\n" + f)
              case m: MissingResponse            => logger.info("MissingResponse in ask. Called from: " + m.getStackTrace().mkString("\n"))
              // This can happen if you ask long queries of the
              // PC, triggering long sleep() sessions on caller
              // side.
              case i: InterruptedException       => logger.debug("InterruptedException in ask:\n" + i)
              case e                             => eclipseLog.error("Error during askOption", e)
            }
            None

          case Right(m: MissingResponse) =>
            logger.info("MissingResponse in ask. Called from: " + m.getStackTrace().mkString("\n"))
            None

          case Right(e: Throwable) =>
            eclipseLog.error("Error during askOption", e)
            None

          case Left(v) => Some(v)
        }
    }
  }

  /** Ask to put scu in the beginning of the list of files to be typechecked.
   *
   *  If the file has not been 'reloaded' first, it does nothing.
   */
  def askToDoFirst(scu: ScalaCompilationUnit) {
    askToDoFirst(scu.sourceFile())
  }

  /** Reload the given compilation unit. If the unit is not tracked by the presentation
   *  compiler, it will be from now on.
   */
  def askReload(scu: InteractiveCompilationUnit, content: Array[Char]): Response[Unit] = {
    withResponse[Unit] { res => askReload(List(scu.sourceFile(content)), res) }
  }

  /** Atomically load a list of units in the current presentation compiler. */
  def askReload(units: List[InteractiveCompilationUnit]): Response[Unit] = {
    withResponse[Unit] { res => askReload(units.map(_.sourceFile), res) }
  }

  def filesDeleted(units: List[ScalaCompilationUnit]) {
    logger.info("files deleted:\n" + (units map (_.getPath) mkString "\n"))
    if (!units.isEmpty)
      askFilesDeleted(units.map(_.sourceFile), new Response[Unit])
  }

  def discardCompilationUnit(scu: ScalaCompilationUnit) {
    logger.info("discarding " + scu.sourceFile.path)
    askOption { () => removeUnitOf(scu.sourceFile) }
  }

  /** Tell the presentation compiler to refresh the given files,
   *  if they are not managed by the presentation compiler already.
   */
  def refreshChangedFiles(files: List[IFile]) {
    // transform to batch source files
    val freshSources = files.collect {
      // When a compilation unit is moved (e.g. using the Move refactoring) between packages,
      // an ElementChangedEvent is fired but with the old IFile name. Ignoring the file does
      // not seem to cause any bad effects later on, so we simply ignore these files -- Mirko
      // using an Util class from jdt.internal to read the file, Eclipse doesn't seem to
      // provide an API way to do it -- Luc
      case file if file.exists => new BatchSourceFile(EclipseResource(file), Util.getResourceContentsAsCharArray(file))
    }

    // only the files not already managed should be refreshed
    val managedFiles = unitOfFile.keySet
    val notLoadedFiles = freshSources.filter(f => !managedFiles(f.file))

    notLoadedFiles.foreach(file => {
      // call askParsedEntered to force the refresh without loading the file
      val r = withResponse[Tree] { askParsedEntered(file, false, _) }

      r.get
    })

    // reconcile the opened editors if some files have been refreshed
    if (notLoadedFiles.nonEmpty)
      askReloadManagedUnits()
  }

  override def synchronizeNames = true

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
  def mkCompletionProposal(prefix: Array[Char], start: Int, sym: Symbol, tpe: Type,
    inherited: Boolean, viaView: Symbol, context: CompletionContext): CompletionProposal = {

    /** Some strings need to be enclosed in back-ticks to be usable as identifiers in scala
     *  source. This function adds the back-ticks to a given identifier, if necessary.
     */
    def addBackTicksIfNecessary(identifier: String): String = {
      def needsBackTicks(identifier: String) = {
        import scalariform.lexer.Tokens._

        try {
          val tokens = ScalaLexer.tokenise(identifier)  // The last token is always EOF
          tokens.size match {
            case 1 => true    // whitespace
            case 2 => !(IDS contains tokens.head.tokenType)
            case more => true
          }
        } catch {case _: ScalaLexerException => true  /* Illegal chars encountered */}
      }

      if(needsBackTicks(identifier)) s"`$identifier`" else identifier
    }

    import org.scalaide.core.completion.MemberKind._

    val kind = if (sym.isSourceMethod && !sym.hasFlag(Flags.ACCESSOR | Flags.PARAMACCESSOR)) Def
    else if (sym.hasPackageFlag) Package
    else if (sym.isClass) Class
    else if (sym.isTrait) Trait
    else if (sym.isPackageObject) PackageObject
    else if (sym.isModule) Object
    else if (sym.isType) Type
    else Val

    val name = if (sym.isConstructor)
      sym.owner.decodedName
    else if (sym.hasGetter)
      (sym.getter: Symbol).decodedName
    else sym.decodedName

    val signature =
      if (sym.isMethod) {
        name +
          (if (!sym.typeParams.isEmpty) sym.typeParams.map { _.name }.mkString("[", ",", "]") else "") +
          tpe.paramss.map(_.map(_.tpe.toString).mkString("(", ", ", ")")).mkString +
          ": " + tpe.finalResultType.toString
      } else name
    val container = sym.owner.enclClass.fullName

    // rudimentary relevance, place own members before inherited ones, and before view-provided ones
    var relevance = 100
    if (!sym.isLocalToBlock) relevance -= 10 // non-local symbols are less relevant than local ones
    if (inherited) relevance -= 10
    if (viaView != NoSymbol) relevance -= 20
    if (sym.hasPackageFlag) relevance -= 30
    // theoretically we'd need an 'ask' around this code, but given that
    // Any and AnyRef are definitely loaded, we call directly to definitions.
    if (sym.owner == definitions.AnyClass
      || sym.owner == definitions.AnyRefClass
      || sym.owner == definitions.ObjectClass) {
      relevance -= 40
    }
    val casePenalty = if (name.take(prefix.length) != prefix.mkString) 50 else 0
    relevance -= casePenalty

    val namesAndTypes = for {
      section <- sym.paramss
      if section.isEmpty || !section.head.isImplicit
    } yield for (param <- section) yield (param.name.toString, param.tpe.toString)

    val (scalaParamNames, paramTypes) = namesAndTypes.map(_.unzip).unzip

    // we save this value to make sure it's evaluated in the PC thread
    // the closure below can be evaluated in any thread
    val isJavaMethod = sym.isJavaDefined && sym.isMethod
    val getParamNames = () => {
      if (isJavaMethod) {
        getJavaElement(sym, project.javaProject) collect {
          case method: IMethod => List(method.getParameterNames.toList)
        } getOrElse scalaParamNames
      } else scalaParamNames
    }

    CompletionProposal(
      kind,
      context,
      start,
      addBackTicksIfNecessary(name),
      signature,
      container,
      relevance,
      sym.isJavaDefined,
      getParamNames,
      paramTypes,
      sym.fullName,
      false)
  }

  override def inform(msg: String): Unit =
    logger.debug("[%s]: %s".format(project, msg))
}

object ScalaPresentationCompiler {
  case class InvalidThread(msg: String) extends RuntimeException(msg)

  class PresentationReporter extends InteractiveReporter {
    var compiler: ScalaPresentationCompiler = null

    def nscSeverityToEclipse(severityLevel: Int) =
      severityLevel match {
        case ERROR.id   => ProblemSeverities.Error
        case WARNING.id => ProblemSeverities.Warning
        case INFO.id    => ProblemSeverities.Ignore
      }

    def eclipseProblem(prob: Problem): Option[IProblem] = {
      import prob._
      if (pos.isDefined) {
        val source = pos.source
        val start = pos.point
        val end = start + ScalaWordFinder.findWord(source.content, start).getLength() - 1
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
          start,
          end,
          pos.line,
          pos.column))
      } else None
    }

    def formatMessage(msg: String) = msg.map {
      case '\n' | '\r' => ' '
      case c           => c
    }
  }
}
