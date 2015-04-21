/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy

/**
 * Transformer for literals and constants:
 *  - `NaN`
 *  - `Infinity`
 *  - `-Infinity`
 *  - `null`
 *  - `()`
 *
 * Transforms:
 * {{{
 *   <literal>
 * }}}
 * into:
 * {{{
 *   __context.proxy(<literal>)
 * }}}
 */
case class MockLiteralsAndConstants(typesContext: TypesContext)
    extends AstTransformer[AfterTypecheck]
    with PrimitivesCommons {

  import universe._
  import Debugger._

  private val constantTransformMap = Map(
    "-Infinity" -> "NegativeInfinity",
    "Infinity" -> "PositiveInfinity",
    "NaN" -> "NaN")

  private val ClassOf = """classOf\[(.*?)\]""".r

  private def classOfCode(literal: Literal) = {
    val ClassOf(className) = literal.toString()
    Apply(
      SelectMethod(contextParamName, classOfProxyMethodName),
      List(Literal(Constant(className))))
  }

  private def nullLiteralCode = {
    val nullProxy = classOf[NullJdiProxy].getSimpleName
    Apply(
      SelectApplyMethod(nullProxy),
      List(Ident(TermName(contextParamName))))
  }

  private def unitLiteralCode = {
    val unitProxy = classOf[UnitJdiProxy].getSimpleName
    Apply(
      SelectApplyMethod(unitProxy),
      List(Ident(TermName(contextParamName))))
  }

  /**
   * Create code to replace literal.
   * Created code is parsed and literal is replaced by it
   */
  private def literalCode(literal: Literal): Tree = {
    import Debugger._
    if (ClassOf.findFirstIn(literal.toString()).isDefined) classOfCode(literal)
    else if (constantTransformMap.contains(literal.toString)) literalConstantCode(literal)
    else if (literal.toString == Scala.nullLiteral) nullLiteralCode
    else if (literal.toString == Scala.unitLiteral) unitLiteralCode
    else packPrimitive(literal)
  }

  private def literalConstantCode(literal: Literal): Tree =
    packPrimitive(literal)

  /** See `AstTransformer.transformSingleTree`. */
  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case literal: Literal => literalCode(literal)
    case any => transformFurther(any)
  }
}
