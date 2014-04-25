/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import scala.util.Failure
import scala.util.Success

import org.junit.Assert._
import org.scalaide.debug.internal.expression.ExpressionEvaluator
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.context.VariableContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.logging.HasLogger

trait HasEvaluator {
  protected object Evaluator extends ExpressionEvaluator(getClass.getClassLoader)
}

class BaseProxyTest extends Mocks with HasEvaluator {

  import scala.reflect.runtime.universe

  private val toolbox: ToolBox[universe.type] = universe.runtimeMirror(classOf[JdiContext].getClassLoader).mkToolBox()

  protected final def baseTest(code: String, variables: Set[MockVariable] = Set.empty,
    objects: Set[MockVariable] = Set.empty)(testFunction: ((MockJdiContext, JdiProxy) => Unit)) {

    val context = MockJdiContext(variables, objects)

    val result = for {
      compiled <- Evaluator.compileExpression(context)(code)
    } yield compiled(context)

    result match {
      case Success(computed) => testFunction(context, computed)
      case Failure(e) => throw e
    }
  }

}

