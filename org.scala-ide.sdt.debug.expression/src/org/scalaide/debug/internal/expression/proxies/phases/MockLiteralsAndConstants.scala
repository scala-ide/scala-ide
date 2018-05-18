/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import Names.Debugger
import Names.Scala
import proxies.primitives.UnitJdiProxy
import proxies.primitives.NullJdiProxy

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
class MockLiteralsAndConstants
    extends AstTransformer[AfterTypecheck]
    with PrimitivesCommons {

  import universe._
  import Debugger._

  private val constantTransformMap = Map(
    "-Infinity" -> "NegativeInfinity",
    "Infinity" -> "PositiveInfinity",
    "NaN" -> "NaN")

  private val ClassOf = """classOf\[(.*?)\]""".r

  // A naive fix of regression introduced by https://github.com/scala/scala/pull/6131/
  // As of Scala 2.12.5 TypeTags can contain references to existential types after TypeCheck phase
  // The transformation performed by this class prevents them from being erased properly, for example
  // Array[_ >: Double with Int <: AnyVal] gets erased to [Lscala.AnyVal;
  private def fixErasure(clazz: String): String =
    if(clazz.contains("scala.Any")) "java.lang.Object"
    else clazz

  private def classOfCode(literal: Literal) = {
    val ClassOf(className) = literal.toString()
    Apply(
      SelectMethod(contextParamName, classOfProxyMethodName),
      List(Literal(Constant(fixErasure(className)))))
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
