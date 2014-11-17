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

// TODO location of this phase is currently almost random (okay, it has to be before untypecheck for sure etc.)
case class MockPrimitivesOperations(toolbox: ToolBox[universe.type])
  extends AstTransformer {

  import toolbox.u._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case s@Apply(Select(on, name), List(arg)) if isPrimitiveOperation(name) && isPrimitive(on) && isPrimitive(arg) =>
      repackTwoPrimitives(on, name, arg, transformFurther)
    case s@Select(on, name) if unaryOperations.contains(name.toString) && isPrimitive(on) =>
      repackUnaryOperation(on, name, transformFurther)
    case If(nested, thenExpr, elseExpr) =>
      val ret = If(obtainPrimitive(nested, transformFurther), transformSingleTree(thenExpr, transformFurther), transformSingleTree(elseExpr, transformFurther))
      ret
    case other => transformFurther(other)
  }

  private def obtainPrimitive(on: Tree, transformFurther: Tree => Tree): Tree =
    on match {
      case Apply(_, List(Apply(contextFun, List(literal: Literal))))
        if contextFun.toString() == "__context.proxy" => literal
      case literal: Literal => literal
      case _ => Select(transformSingleTree(on, transformFurther), TermName(mirrorMethodName(on)))
    }

  private def mirrorMethodName(on: Tree): String = {
    s"_${on.tpe.typeSymbol.name.toString}Mirror"
  }

  private def packPrimitive(primitiveTree: Tree) =
    Apply(SelectMethod(Debugger.contextParamName, "proxy"), List(primitiveTree))

  private def repackTwoPrimitives(on: Tree, name: Name, arg: Tree, transformFurther: Tree => Tree): Tree = {
    val l = obtainPrimitive(on, transformFurther)
    val r = obtainPrimitive(arg, transformFurther)
    packPrimitive(Apply(Select(l, name), List(r)))
  }

  private def repackUnaryOperation(on: Tree, operationName: Name, transformFurther: Tree => Tree) =
    packPrimitive(Select(obtainPrimitive(on, transformFurther), operationName))

  private val notPrimitiveOperation = Set(
    "==", "!="
  ).map(NameTransformer.encode)

  private val unaryOperations = Set("!", "~", "-", "+").map(name => s"unary_${NameTransformer.encode(name)}")

  private def isPrimitiveOperation(name: Name): Boolean = !notPrimitiveOperation.contains(name.toString)

  private def isPrimitive(tree: Tree): Boolean =
    util.Try(if (tree.tpe.typeSymbol.toString.startsWith("object ")) "" else tree.tpe.typeSymbol.fullName) match {
      case Success(tp) =>
        Names.Scala.primitives.all.contains(tp)
      case _ => tree match {
        case Apply(Select(on, TermName("apply")), _) =>
          isPrimitiveProxy(on)
        case _ => false
      }
    }

  private def isPrimitiveProxy(tree: Tree): Boolean = Names.Debugger.boxedProxiesNames.contains(tree.toString())

  // TODO note: we could also change += like methods into combined = and +
}
