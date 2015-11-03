/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import scala.util.Try

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.context.VariableContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.phases._
import org.scalaide.debug.internal.preferences.ExpressionEvaluatorPreferences.shouldAddImportsFromCurrentFile
import org.scalaide.logging.HasLogger

object ExpressionEvaluator {
  type ExpressionFunc = JdiContext => JdiProxy

  /**
   * Class representing compiled expression.
   * expression is triggered on apply function
   * On first run all new class are classload to debugged JVM - so first run might by slower
   * @param compiledFunc expression code
   * @param newClasses new classes that must be loaded to debugged JVM
   */
  case class JdiExpression(compiledFunc: ExpressionFunc, newClasses: Iterable[ClassData], code: universe.Tree) extends ExpressionFunc {
    //class are loaded?
    private var classLoaded: Boolean = false

    private def loadClasses(context: JdiContext): Unit =
      newClasses.foreach(classData => context.loadClass(classData.className, classData.code))

    override final def apply(context: JdiContext): JdiProxy = {
      newClasses.synchronized {
        if (!classLoaded) {
          classLoaded = true
          loadClasses(context)
        }
      }
      compiledFunc.apply(context)
    }
  }

  private[expression] case class Context(
    toolbox: ToolBox[universe.type],
    context: VariableContext)

  type Phases[A <: TypecheckRelation] = Seq[Context => TransformationPhase[A]]

  private[expression] def phases: Phases[TypecheckRelation] = {

    val beforeTypeCheck: Phases[BeforeTypecheck] = Seq(
      ctx => new FailFast,
      ctx => new SingleValDefWorkaround,
      ctx => new SearchForUnboundVariables(ctx.toolbox, ctx.context.localVariablesNames()),
      ctx => new MockAssignment,
      ctx => new MockUnboundValuesAndAddImportsFromThis(ctx.toolbox, ctx.context),
      ctx => new AddImports[BeforeTypecheck](ctx.toolbox, ctx.context.thisPackage, shouldAddImportsFromCurrentFile),
      ctx => new MockThis,
      ctx => new MockSuper(ctx.toolbox),
      ctx => MockTypedLambda(ctx.toolbox))

    val typecheck: Phases[IsTypecheck] = Seq(
      ctx => TypeCheck(ctx.toolbox))

    val afterTypecheck: Phases[AfterTypecheck] = Seq(
      ctx => FixClassTags(ctx.toolbox),
      ctx => new RemoveImports,
      // function should be first because this transformer needs tree as clean as possible
      ctx => MockLambdas(ctx.toolbox),
      ctx => ImplementTypedLambda(ctx.toolbox),
      ctx => new AfterTypecheckFailFast,
      ctx => new ImplementMockedNestedMethods(ctx.context),
      ctx => new MockLiteralsAndConstants,
      ctx => new MockPrimitivesOperations,
      ctx => new MockToString,
      ctx => new MockLocalAssignment,
      ctx => new MockIsInstanceOf,
      ctx => new RemoveAsInstanceOf,
      ctx => new MockHashCode,
      ctx => new MockMethodsCalls,
      ctx => new MockObjectsAndStaticCalls,
      ctx => new MockNewOperator,
      ctx => new FlattenMultiArgListMethods,
      ctx => new RemoveTypeArgumentsFromMethods,
      ctx => ImplementValues(ctx.toolbox, ctx.context.implementValue),
      ctx => new ResetTypeInformation,
      ctx => new AddImports[AfterTypecheck](ctx.toolbox, ctx.context.thisPackage),
      ctx => new PackInFunction(ctx.toolbox))

    beforeTypeCheck ++ typecheck ++ afterTypecheck
  }
}

/**
 * Main component responsible for compiling user expressions to evaluate.
 *
 * @param projectClassLoader classloader for debugged project
 */
abstract class ExpressionEvaluator(protected val projectClassLoader: ClassLoader,
  monitor: ProgressMonitor,
  compilerOptions: Seq[String] = Seq.empty)
    extends HasLogger {

  import ExpressionEvaluator._

  private def toolboxCompilerOptions = (AnonymousFunctionSupport.toolboxOptions +: compilerOptions) mkString (" ")

  lazy val toolbox: ToolBox[universe.type] = universe.runtimeMirror(projectClassLoader)
    .mkToolBox(options = toolboxCompilerOptions)

  import toolbox.u

  /** Parses given code to a Tree */
  def parse(code: String): u.Tree = toolbox.parse(code)

  /**
   * Main entry point for this class, compiles given expression in given context to `JdiExpression`.
   *
   * @return tuple (expression, tree) with compiled expression and tree used for compilation
   */
  final def compileExpression(context: VariableContext)(code: String): Try[JdiExpression] = Try {
    val parsed = parse(code)

    val phases = genPhases(context)

    val transformed = transform(parsed, phases)

    try {
      val (compiled, time) = measure {
        compile(transformed.tree, transformed.classesToLoad)
      }
      logTimesToFile(transformed.withTime("Compilation", time))
      compiled
    } catch {
      case exception: Throwable =>
        val codeAfterPhases = stringifyTreesAfterPhases(transformed.history)
        val message =
          s"Reflective compilation of: '$code' failed, full transformation history logged at 'DEBUG' level"
        val allHistory =
          s"""Trees transformation history:
             |$codeAfterPhases
             |""".stripMargin
        logger.error(message)
        logger.debug(allHistory, exception)
        throw exception
    }
  }

  // measure execution time in microseconds
  private def measure[A](block: => A): (A, Long) = {
    val now = System.nanoTime()
    val result = block
    val micros = (System.nanoTime - now) / 1000
    (result, micros)
  }

  // Writes data about execution times to file. Use for debugging.
  // Runs only when environment property `scalaide.ee-time-log` is defined.
  private def logTimesToFile(data: TransformationPhaseData): Unit =
    if (Option(System.getProperty("scalaide.eelogtimes")).isDefined) {
      val logFile = new java.io.File(".ee-time-log.csv")
      val pw = new java.io.FileWriter(logFile, /* append = */ true)
      try {
        pw.write("Phases:\t" + data.times.map(_.name).mkString("\t") + "\n")
        val code = data.history.head.code.toString.replace("\n", " ").replace("\r", " ")
        pw.write("Expression: " + code + "\t" + data.times.map(_.time).mkString("\t") + "\n")
      } finally pw.close()
    }

  private def recompileFromStrigifiedTree(tree: u.Tree): () => Any = {
    toolbox.compile(toolbox.parse(tree.toString()))
  }

  /** Compiles a Tree to Expression */
  private def compile(tree: u.Tree, newClasses: Iterable[ClassData]): JdiExpression = {
    (try {
      toolbox.compile(tree)
    } catch {
      case e: UnsupportedOperationException =>
        // Workaround for "No position error"
        // Reset type information is buggy so in some cases to compile expression we have to make "hard reset" (stringify, parse, compile)
        logger.warn("Compilation failed - stringifying and recompilig whole tree.")
        recompileFromStrigifiedTree(tree)
    }).apply() match {
      case function: ExpressionFunc @unchecked =>
        JdiExpression(function, newClasses, tree)
      case other =>
        throw new IllegalArgumentException(s"Bad compilation result: '$other'")
    }
  }

  private def genPhases(context: VariableContext) = {
    val ctx = Context(toolbox, context)
    phases.map(_(ctx))
  }

  private def transform(code: universe.Tree, phases: Seq[TransformationPhase[TypecheckRelation]]): TransformationPhaseData = {
    val parse = PhaseCode("Parse", code)
    val data = TransformationPhaseData(tree = code, history = Vector(parse))
    phases.foldLeft(data) {
      case (lastData, phase) =>
        monitor.startNamedSubTask("Applying transformation phase: " + phase.phaseName)
        try {
          val (newData, time) = measure {
            phase.transform(lastData)
          }
          monitor.reportProgress(1)
          newData.withTime(phase.phaseName, time)
        } catch {
          case e: Throwable =>
            val codeAfterPhases = stringifyTreesAfterPhases(lastData.history)
            val message =
              s"Applying phase: ${phase.phaseName} failed, full transformation history logged at 'DEBUG' level"
            val allHistory =
              s"""Trees before current transformation:
                 |$codeAfterPhases
                 |""".stripMargin
            logger.error(message)
            logger.debug(allHistory, e)
            throw e
        }
    }
  }

  private def stringifyTreesAfterPhases(treesAfterPhases: Vector[PhaseCode]): String =
    treesAfterPhases.map {
      case PhaseCode(phaseName, tree) => s" After phase '$phaseName': ------------\n\n$tree"
    } mkString ("------------", "\n\n------------ ", "\n------------")
}
