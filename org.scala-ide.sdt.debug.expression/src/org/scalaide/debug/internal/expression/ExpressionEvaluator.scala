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
   *
   * @return tuple (expression, tree) with compiled expression and tree used for compilation
   */
  final def compileExpression(context: VariableContext)(code: String): Try[(JdiExpression, u.Tree)] = Try {
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

  private def recompileFromStrigifiedTree(tree: u.Tree): () => Any ={
    toolbox.compile(toolbox.parse(tree.toString()))
  }

  /** Compiles a Tree to Expression */
  private def compile(tree: u.Tree, newClasses: Iterable[ClassData]): (JdiExpression, u.Tree) = {
    (try {
      toolbox.compile(tree)
    } catch {
      case e: UnsupportedOperationException =>
        //Workaround for "No position error"
        //Reset type information is buggy so in some cases to compile expression we have to make "hard reset" (stringify, parse, compile)
        recompileFromStrigifiedTree(tree)
    }).apply() match {
      case function: ExpressionFunc @unchecked =>
        (JdiExpression(function, newClasses), tree)
      case other =>
        throw new IllegalArgumentException(s"Bad compilation result: '$other'")
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
    DetectNothingTypedExpression(toolbox),
    RemoveImports(toolbox),
    // function should be first cos this transformer needs tree as clean as possible
    MockLambdas(toolbox, typesContext),
    ImplementTypedLambda(toolbox, typesContext),
    MockLiteralsAndConstants(toolbox, typesContext),
    MockPrimitivesOperations(toolbox),
    MockToString(toolbox),
    MockHashCode(toolbox),
    MockObjectsAndStaticCalls(toolbox, typesContext),
    MockNewOperator(toolbox),
    FlattenFunctions(toolbox),
    ImplementValues(toolbox, context.implementValue),
    CleanUpValDefs(toolbox),
    ResetTypeInformation(toolbox),
    new AddImports(toolbox, context.thisPackage),
    new PackInFunction(toolbox))

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
