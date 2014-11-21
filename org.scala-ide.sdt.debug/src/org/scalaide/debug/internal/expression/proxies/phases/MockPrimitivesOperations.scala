/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.TypesContext
import scala.util.Success
import scala.reflect.NameTransformer

case class MockPrimitivesOperations(toolbox: ToolBox[universe.type])
  extends AstTransformer with PrimitivesCommons {

  import toolbox.u._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Apply(Select(on, name), List(arg)) if isPrimitiveOperation(name) && isPrimitive(on) && isPrimitive(arg) =>
      repackTwoPrimitives(on, name, arg, transformFurther)
    case Select(on, name) if unaryOperations.contains(name.toString) && isPrimitive(on) =>
      repackUnaryOperation(on, name, transformFurther)
    case If(nested, thenExpr, elseExpr) =>
      If(obtainPrimitive(nested, transformFurther), transformSingleTree(thenExpr, transformFurther), transformSingleTree(elseExpr, transformFurther))
    case other => transformFurther(other)
  }

  object ProxifiedPrimitive {
    def unapply(tree: Tree): Option[Literal] = {
      import Debugger._
      tree match {
        case Apply(contextFun, List(literal: Literal)) if contextFun.toString() == s"$contextParamName.$proxyMethodName" => Some(literal)
        case literal: Literal => Some(literal)
        case _ => None
      }
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

  private def mirrorMethodType(on: Tree): Tree =
    Ident(newTypeName(on.tpe.typeSymbol.name.toString))

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

  private def isPrimitive(tree: Tree): Boolean =
    util.Try(if (tree.tpe.typeSymbol.toString.startsWith("object ")) "" else tree.tpe.typeSymbol.fullName) match {
      case Success(tp) =>
        Names.Scala.primitives.all.contains(tp)
      case _ => isPrimitiveProxy(tree)
    }

  private def isPrimitiveProxy(tree: Tree): Boolean = ProxifiedPrimitive.unapply(tree).isDefined
}
