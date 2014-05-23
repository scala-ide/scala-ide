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
  extends AstTransformer {

  import toolbox.u._

  private val constantTransformMap = Map(
    "-Infinity" -> "NegativeInfinity",
    "Infinity" -> "PositiveInfinity",
    "NaN" -> "NaN")

  /**
   * Create code to replace literal.
   * Created code is parsed and literal is replaced by it
   */
  private def literalCode(literal: Literal): Tree = {
    import DebuggerSpecific._
    if (constantTransformMap.contains(literal.toString)) {
      literalConstantCode(literal)
    } else if (literal.toString == ScalaOther.unitLiteral) {
      val unitProxy = classOf[UnitJdiProxy].getSimpleName
      Apply(
        SelectApplyMethod(unitProxy),
        List(Ident(newTermName(contextParamName))))
    } else {
      val literalStub = typesContext.treeTypeFromContext(literal).get
      Apply(
        SelectApplyMethod(literalStub),
        List(
          Apply(
            SelectMethod(contextParamName, proxyMethodName),
            List(literal))))
    }
  }

  /**
   * Create code for constant
   * Generate code __context.proxy(Type.Constant)
   * where types are Float and Double and Constant are NegativeInfinity, PositiveInfinity and NaN
   */
  private def literalConstantCode(literal: Literal): Tree = {
    val literalStub = typesContext.treeTypeFromContext(literal).get

    def literalCodeFor(typeName: String): Tree =
      SelectMethod(typeName, constantTransformMap(literal.toString))

    val literalCode =
      if (literalStub.contains("Float")) literalCodeFor("Float")
      else literalCodeFor("Double")

    import DebuggerSpecific._

    Apply(
      SelectApplyMethod(literalStub),
      List(
        Apply(
          SelectMethod(contextParamName, proxyMethodName),
          List(literalCode))))
  }

  /**
   * Checks if given literal should be proxied.
   * Unit literal - '()' and 'null' do not require to be proxied
   */
  private def shouldBeProxied(literal: Literal) = literal.toString != "null"

  /** See `AstTransformer.transformSingleTree`. */
  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case literal: Literal if shouldBeProxied(literal) => literalCode(literal)
    case any => transformFurther(tree)
  }
}
