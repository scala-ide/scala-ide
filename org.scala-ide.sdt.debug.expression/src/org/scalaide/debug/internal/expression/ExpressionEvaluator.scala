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
    context: VariableContext,
    typesContext: TypesContext)

  private[expression] def phases: Seq[Context => TransformationPhase] = Seq(
    ctx => FailFast(ctx.toolbox),
    ctx => new SearchForUnboundVariables(ctx.toolbox, ctx.typesContext, ctx.context.localVariablesNames()),
    ctx => new MockAssignment(ctx.toolbox, ctx.typesContext.unboundVariables),
    ctx => new MockUnboundValuesAndAddImportsFromThis(ctx.toolbox, ctx.context, ctx.typesContext.unboundVariables),
    ctx => new AddImports(ctx.toolbox, ctx.context.thisPackage),
    ctx => MockThis(ctx.toolbox),
    ctx => MockTypedLambda(ctx.toolbox, ctx.typesContext),
    ctx => TypeCheck(ctx.toolbox),
    ctx => FixClassTags(ctx.toolbox),
    ctx => DetectNothingTypedExpression(ctx.toolbox),
    ctx => RemoveImports(ctx.toolbox),
    // function should be first because this transformer needs tree as clean as possible
    ctx => MockLambdas(ctx.toolbox, ctx.typesContext),
    ctx => ImplementTypedLambda(ctx.toolbox, ctx.typesContext),
    ctx => new ImplementMockedNestedMethods(ctx.toolbox, ctx.context),
    ctx => MockLiteralsAndConstants(ctx.toolbox, ctx.typesContext),
    ctx => MockPrimitivesOperations(ctx.toolbox),
    ctx => MockToString(ctx.toolbox),
    ctx => new MockLocalAssignment(ctx.toolbox, ctx.typesContext.unboundVariables),
    ctx => new MockIsInstanceOf(ctx.toolbox),
    ctx => new RemoveAsInstanceOf(ctx.toolbox),
    ctx => MockHashCode(ctx.toolbox),
    ctx => MockMethodsCalls(ctx.toolbox),
    ctx => MockObjectsAndStaticCalls(ctx.toolbox, ctx.typesContext),
    ctx => MockNewOperator(ctx.toolbox),
    ctx => FlattenFunctions(ctx.toolbox),
    ctx => ImplementValues(ctx.toolbox, ctx.context.implementValue),
    ctx => CleanUpValDefs(ctx.toolbox),
    ctx => ResetTypeInformation(ctx.toolbox),
    ctx => new AddImports(ctx.toolbox, ctx.context.thisPackage),
    ctx => new PackInFunction(ctx.toolbox))
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

    val typesContext = new TypesContext()

    val phases = genPhases(context, typesContext)

    val transformed = transform(parsed, phases)

    try {
      compile(transformed, typesContext.classesToLoad)
    } catch {
      case exception: Throwable =>
        val message =
          s"""Reflective compilation failed
             |Tree to compile:
             |$transformed
             |""".stripMargin
        logger.error(message, exception)
        throw exception
    }
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
        //Workaround for "No position error"
        //Reset type information is buggy so in some cases to compile expression we have to make "hard reset" (stringify, parse, compile)
        recompileFromStrigifiedTree(tree)
    }).apply() match {
      case function: ExpressionFunc @unchecked =>
        JdiExpression(function, newClasses, tree)
      case other =>
        throw new IllegalArgumentException(s"Bad compilation result: '$other'")
    }
  }

  private def genPhases(context: VariableContext, typesContext: TypesContext) = {
    val ctx = Context(toolbox, context, typesContext)
    phases.map(_(ctx))
  }

  private def transform(code: universe.Tree, phases: Seq[TransformationPhase]): universe.Tree = {
    val (_, finalTree) = phases.foldLeft(Vector(("Initial code", code))) {
      case (treesAfterPhases, phase) =>
        val phaseName = phase.getClass.getSimpleName
        monitor.startNamedSubTask(s"Applying transformation phase:")
        try {
          val (_, previousTree) = treesAfterPhases.last
          val current = (phaseName, phase.transform(previousTree))
          val result = treesAfterPhases :+ current
          monitor.reportProgress(1)
          result
        } catch {
          case e: Throwable =>
            val codeAfterPhases = stringifyTreesAfterPhases(treesAfterPhases)
            val message =
              s"""Applying phase: $phaseName failed
               |Trees before current transformation:
               |$codeAfterPhases
               |""".stripMargin
            logger.error(message, e)
            throw e
        }
    }.last
    finalTree
  }

  private def stringifyTreesAfterPhases(treesAfterPhases: Seq[(String, universe.Tree)]): String =
    treesAfterPhases.map {
      case (phaseName, tree) => s" After phase '$phaseName': ------------\n\n$tree"
    } mkString ("------------", "\n\n------------ ", "\n------------")
}
