/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import scala.util.Try
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.phases._
import org.scalaide.logging.HasLogger
import com.sun.jdi.ThreadReference
import org.scalaide.debug.internal.expression.proxies.phases.MockLambdas
import org.scalaide.debug.internal.expression.proxies.phases.MockTypes
import org.scalaide.debug.internal.expression.proxies.phases.PackInFunction
import org.scalaide.debug.internal.expression.proxies.phases.GenerateStubs
import org.scalaide.debug.internal.expression.proxies.phases.MockObjects
import org.scalaide.debug.internal.expression.proxies.phases.MockConditionalExpressions
import org.scalaide.debug.internal.expression.proxies.phases.MockNewOperator
import org.scalaide.debug.internal.expression.proxies.phases.TypeCheck
import org.scalaide.debug.internal.expression.proxies.phases.MockThis
import org.scalaide.debug.internal.expression.proxies.phases.TypeSearch
import org.scalaide.debug.internal.expression.proxies.phases.MockToString
import org.scalaide.debug.internal.expression.proxies.phases.ImplementValues
import org.scalaide.debug.internal.expression.proxies.phases.MockVariables
import org.scalaide.debug.internal.expression.proxies.phases.MockLiteralsAndConstants
import org.scalaide.debug.internal.expression.context.VariableContext

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
 */
abstract class ExpressionEvaluator(val classLoader: ClassLoader)
  extends HasLogger {

  import ExpressionEvaluator._

  lazy val toolbox: ToolBox[universe.type] = universe.runtimeMirror(classLoader).mkToolBox(options = ClassListener.options)

  import toolbox.u

  /** Parses given code to a Tree */
  def parse(code: String): u.Tree = toolbox.parse(code)

  /**
   * Main entry point for this class, compiles given expression in given context to scala `Expression`.
   */
  final def compileExpression(context: VariableContext)(code: String): Try[JdiExpression] = Try {
    val parsed = parse(code)
    val typesContext = new TypesContext()

    val phases = genPhases(context, typesContext)

    val transformed = transform(parsed, phases)

    val message = s"""
                      |Compiling tree:
                      |$transformed
                      |""".stripMargin
    logger.debug(message)

    compile(transformed, typesContext.classesToLoad)
  }

  /** Compiles a Tree to Expression */
  private def compile(tree: u.Tree, newClasses: Iterable[ClassData]): JdiExpression =
    toolbox.compile(tree).apply() match {
      case function: ExpressionFunc @unchecked => JdiExpression(function, newClasses)
      case other => throw new IllegalArgumentException("Bad compilation result!")
    }

  private def genPhases(context: VariableContext, typesContext: TypesContext): Seq[TransformationPhase] = Seq(
    MockVariables(toolbox, context),
    PackInFunction(toolbox, context.getThisPackage),
    MockThis(toolbox, typesContext),
    MockTypedLambda(toolbox, typesContext),
    TypeCheck(toolbox),
    TypeSearch(toolbox, typesContext),
    // function should be first cos this transformer needs tree as clean as possible
    MockLambdas(toolbox, typesContext),
    ImplementTypedLambda(toolbox, typesContext),
    MockTypes(toolbox, typesContext),
    MockLiteralsAndConstants(toolbox, typesContext),
    MockToString(toolbox, typesContext),
    MockObjects(toolbox, typesContext),
    MockNewOperator(toolbox, typesContext),
    MockConditionalExpressions(toolbox, typesContext),
    GenerateStubs(toolbox, typesContext),
    ImplementValues(toolbox, typesContext),
    ResetTypeInformation(toolbox))

  private def transform(code: universe.Tree, phases: Seq[TransformationPhase]) = phases.foldLeft(code) {
    case (tree, phase) =>
      val phaseName = phase.getClass.getSimpleName
      val message = s"""
                        |Applying phase: $phaseName
                        |Tree before transformation:
                        |$tree
                        |""".stripMargin

      logger.debug(message)

      try {
        phase.transform(tree)
      } catch {
        case e: Throwable =>
          logger.error(s"Phase $phaseName failed")
          throw e
      }
  }
}
