/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TypesContext
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy

/**
 * Transformer for literals and constants (`NaN` and `Infinity`)
 *
 * All found ones are transformed to `__context.proxy(literal)`
 */
case class MockLiteralsAndConstants(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer with PrimitivesCommons {

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
    import Debugger._
    if (constantTransformMap.contains(literal.toString)) {
      literalConstantCode(literal)
    } else if (literal.toString == Scala.nullLiteral) {
      val nullProxy = classOf[NullJdiProxy].getSimpleName
      Apply(
        SelectApplyMethod(nullProxy),
        List(Ident(TermName(contextParamName))))
    } else if (literal.toString == Scala.unitLiteral) {
      val unitProxy = classOf[UnitJdiProxy].getSimpleName
      Apply(
        SelectApplyMethod(unitProxy),
        List(Ident(TermName(contextParamName))))
    } else {
      packPrimitive(literal)
    }
  }

  /**
   * Create code for constant
   * Generate code __context.proxy(Type.Constant)
   * where types are Float and Double and Constant are NegativeInfinity, PositiveInfinity and NaN
   */
  private def literalConstantCode(literal: Literal): Tree =
    packPrimitive(literal)


  /** See `AstTransformer.transformSingleTree`. */
  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case literal: Literal => literalCode(literal)
    case any => transformFurther(any)
  }
}
