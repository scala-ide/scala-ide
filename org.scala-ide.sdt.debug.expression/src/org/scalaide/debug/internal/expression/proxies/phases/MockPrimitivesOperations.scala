/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import Names.Debugger

import scala.reflect.NameTransformer
import scala.reflect.runtime.universe
import scala.util.Success

import org.scalaide.debug.internal.expression.Names.Debugger

/**
 * Transforms all operations on primitives (both unary and binary) into method calls on newly created proxies.
 *
 * Also takes care of if-then-else control expression.
 *
 * Transforms:
 * {{{
 *   int.unary_-.^(2)./(1).+(double).-(float)
 * }}}
 * into:
 * {{{
 *   __context.proxy(
 *     __context.proxy(
 *       __context.proxy(
 *         __context.proxy(
 *           __context.proxy(int.__value[Int].unary_$minus).__value[Int]
 *             .$up(2)
 *         ).__value[Int]
 *           .$div(1)
 *       ).__value[Int]
 *         .$plus(double.__value[Double])
 *     ).__value[Double]
 *       .$minus(float.__value[Float])
 *   ).__value[Double]
 * }}}
 */
class MockPrimitivesOperations
    extends AstTransformer[AfterTypecheck]
    with PrimitivesCommons {

  import universe._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Apply(Select(on, name), List(arg)) if isPrimitiveOperation(name) && isPrimitive(on) && isPrimitive(arg) =>
      repackTwoPrimitives(on, name, arg, transformFurther)
    case Select(on, name) if unaryOperations.contains(name.toString) && isPrimitive(on) =>
      repackUnaryOperation(on, name, transformFurther)
    case If(nested, thenExpr, elseExpr) => If(
      obtainPrimitive(nested, transformFurther),
      transformSingleTree(thenExpr, transformFurther),
      transformSingleTree(elseExpr, transformFurther))
    case other => transformFurther(other)
  }

  object ProxifiedPrimitive {
    import Debugger._
    def unapply(tree: Tree): Option[Literal] = tree match {
      case Apply(contextFun, List(literal: Literal)) if contextFun.toString() == s"$contextParamName.$proxyMethodName" => Some(literal)
      case literal: Literal => Some(literal)
      case _ => None
    }
  }

  private def obtainPrimitive(on: Tree, transformFurther: Tree => Tree): Tree =
    on match {
      case ProxifiedPrimitive(literal) => literal
      case _ =>
        val typeForPrimitiveGetter = mirrorMethodType(on)
        TypeApply(
          Select(transformSingleTree(on, transformFurther), TermName(Debugger.primitiveValueOfProxyMethodName)),
          List(typeForPrimitiveGetter))
    }

  private def mirrorMethodType(on: Tree): Tree = {
    val typeName = TypeNames.fromTree(on)
      .getOrElse(throw new RuntimeException("Primitives mocking must have type!"))
    Ident(TypeName(typeName))
  }

  private def repackTwoPrimitives(on: Tree, name: Name, arg: Tree, transformFurther: Tree => Tree): Tree = {
    val l = obtainPrimitive(on, transformFurther)
    val r = obtainPrimitive(arg, transformFurther)
    packPrimitive(Apply(Select(l, name), List(r)))
  }

  private def repackUnaryOperation(on: Tree, operationName: Name, transformFurther: Tree => Tree) =
    packPrimitive(Select(obtainPrimitive(on, transformFurther), operationName))

  private val notPrimitiveOperation = Set("==", "!=").map(NameTransformer.encode)

  private val unaryOperations = Set("!", "~", "-", "+").map(name => s"unary_${NameTransformer.encode(name)}")

  private def isPrimitiveOperation(name: Name): Boolean = !notPrimitiveOperation.contains(name.toString)

  private def isPrimitive(tree: Tree): Boolean = TypeNames.fromTree(tree) match {
    case Some(treeType) => Names.Scala.primitives.allShorten.contains(treeType)
    case _ => isPrimitiveProxy(tree)
  }

  private def isPrimitiveProxy(tree: Tree): Boolean = ProxifiedPrimitive.unapply(tree).isDefined
}
