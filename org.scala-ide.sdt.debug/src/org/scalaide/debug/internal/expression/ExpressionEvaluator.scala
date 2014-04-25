/*
 * Copyright (c) 2014 Contributor. All rights reserved.
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
  case class JdiExpression(compiledFunc: ExpressionFunc, newClasses: Iterable[ClassData]) extends ExpressionFunc {
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

  /** Compiles a Tree to Expression */
  private def compile(tree: u.Tree, newClasses: Iterable[ClassData]): JdiExpression = {
    toolbox.compile(tree).apply() match {
      case function: ExpressionFunc @unchecked => JdiExpression(function, newClasses)
      case other => throw new IllegalArgumentException(s"Bad compilation result: '$other'")
    }
  }

  // WARNING if you add/remove phase here, remember to update ExpressionManager.numberOfPhases with correct count
  private def genPhases(context: VariableContext, typesContext: TypesContext): Seq[TransformationPhase] = Seq(
    FailFast(toolbox),
    SearchForUnboundVariables(toolbox, typesContext),
    new MockAssignment(toolbox, typesContext.unboundVariables),
    new MockVariables(toolbox, projectClassLoader, context, typesContext.unboundVariables),
    new AddImports(toolbox, context.thisPackage),
    MockThis(toolbox),
    MockTypedLambda(toolbox, typesContext),
    TypeCheck(toolbox),
    RemoveImports(toolbox),
    TypeSearch(toolbox, typesContext),
    // function should be first cos this transformer needs tree as clean as possible
    MockLambdas(toolbox, typesContext),
    ImplementTypedLambda(toolbox, typesContext),
    MockTypes(toolbox, typesContext),
    MockLiteralsAndConstants(toolbox, typesContext),
    MockToString(toolbox),
    MockObjects(toolbox, typesContext),
    MockNewOperator(toolbox),
    MockConditionalExpressions(toolbox),
    new GenerateStubs(toolbox, typesContext.typesStubCode),
    ImplementValues(toolbox, context.implementValue),
    ResetTypeInformation(toolbox),
    new AddImports(toolbox, context.thisPackage),
    new PackInFunction(toolbox))

  private def transform(code: universe.Tree, phases: Seq[TransformationPhase]): universe.Tree = {
    phases.foldLeft(Vector(code)) {
      case (trees, phase) =>
        val phaseName = phase.getClass.getSimpleName
        monitor.startNamedSubTask(s"Applying transformation phase: $phaseName")
        try {
          val result = trees :+ phase.transform(trees.last)
          monitor.reportProgress(1)
          result
        } catch {
          case e: Throwable =>
            val treeCode = trees.mkString("\n--- next phase ---\n")
            val message =
              s"""Applying phase: $phaseName failed
               |Trees before transformation:
               |$treeCode
               |""".stripMargin
            logger.error(message, e)
            throw e
        }
    }.last
  }
}
