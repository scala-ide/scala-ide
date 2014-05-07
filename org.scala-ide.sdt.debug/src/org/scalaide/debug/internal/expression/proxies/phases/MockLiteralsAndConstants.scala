/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.DebuggerSpecific
import org.scalaide.debug.internal.expression.ExpressionEvaluator
import org.scalaide.debug.internal.expression.TypesContext
import org.scalaide.debug.internal.expression.ScalaOther
import org.scalaide.debug.internal.expression.proxies.UnitJdiProxy

/**
 * Transformer for literals and constants (`NaN` and `Infinity`)
 *
 * All found ones are transformed to `__context.proxy(literal)`
 */
case class MockLiteralsAndConstants(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer(typesContext) {

  import toolbox.u._

  private val constantTransformMap = Map(
    "-Infinity" -> "NegativeInfinity",
    "Infinity" -> "PositiveInfinity",
    "NaN" -> "NaN")

  /**
   * Create code to replace literal.
   * Created code is parsed and literal is replaced by it
   */
  private def literalCode(literal: Literal): String = {
    if (constantTransformMap.contains(literal.toString)) {
      literalConstantCode(literal)
    } else if (literal.toString == ScalaOther.unitLiteral) {
      import DebuggerSpecific._
      val unitProxy = classOf[UnitJdiProxy].getSimpleName
      s"$unitProxy($contextParamName)"
    } else {
      val literalStub = typesContext.treeTypeFromContext(literal).get
      import DebuggerSpecific._
      s"$literalStub($contextParamName.$proxyMethodName($literal))"
    }
  }

  /**
   * Create code for constant
   * Generate code __context.proxy(Type.Constant)
   * where types are Float and Double and Constant are NegativeInfinity, PositiveInfinity and NaN
   */
  private def literalConstantCode(literal: Literal): String = {
    val literalStub = typesContext.treeTypeFromContext(literal).get

    val literalCode = if (literalStub.contains("Float"))
      s"Float.${constantTransformMap(literal.toString)}"
    else
      s"Double.${constantTransformMap(literal.toString)}"

    import DebuggerSpecific._
    s"$literalStub($contextParamName.$proxyMethodName($literalCode))"
  }

  /**
   * Checks if given literal should be proxied.
   * Unit literal - '()' and 'null' do not require to be proxied
   */
  private def shouldBeProxied(literal: Literal) = literal.toString != "null"

  /** See `AstTransformer.transformSingleTree`. */
  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case literal: Literal if shouldBeProxied(literal) => toolbox.parse(literalCode(literal))
    case any => transformFurther(tree)
  }
}
