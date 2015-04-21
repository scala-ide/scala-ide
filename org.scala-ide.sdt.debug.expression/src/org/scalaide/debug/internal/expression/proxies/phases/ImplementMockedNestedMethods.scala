/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.scalaide.debug.internal.expression.context.NestedMethodDeclaration
import org.scalaide.debug.internal.expression.context.VariableContext

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.Names.Debugger._
import org.scalaide.debug.internal.expression.UnsupportedFeature

import scala.reflect.runtime._
import scala.tools.reflect.ToolBox

/**
 * Tries to implement all found nested functions mocks.
 * It remove mock value definition and rewrite nested method call.
 */
class ImplementMockedNestedMethods(val toolbox: ToolBox[universe.type], context: VariableContext) extends AstTransformer {

  import toolbox.u._

  private var nestedMethods: Map[String, NestedMethodDeclaration] = Map()

  private object MockedNestedLambda {
    def unapply(t: Tree): Option[(Int, Int, Int)] = t match {
      case Apply(fun, Seq(Literal(Constant(parametersListsCount: Int)), Literal(Constant(from: Int)), Literal(Constant(to: Int))))
        if fun.toString() == contextFullName + "." + placeholderNestedMethodName => Some((parametersListsCount, from, to))
      case _ => None
    }
  }

  private def checkParametersListsCount(args: List[_], applyCount: Int): Boolean =
    if (applyCount < args.size) false
    else if(applyCount > args.size) throw new UnsupportedFeature("Nested method as function")
    else true


  private def implementMethod(functionName: Name, args: List[List[Tree]]): Option[Tree] = {
    val name = functionName.toString
    for {
      nestedMethodDeclaration <- nestedMethods.get(name) if checkParametersListsCount(args, nestedMethodDeclaration.parametersListsCount)
      nestedMethodImplementation <- context.nestedMethodImplementation(nestedMethodDeclaration)
    } yield {

      if (args.flatten.size < nestedMethodImplementation.argsNames.size)
        throw new UnsupportedFeature("Nested method with closure parameters")

      Apply(Select(Ident(nestedMethodImplementation.on), TermName(nestedMethodImplementation.jvmName)), args.flatten)
    }
  }

  object NestedMethod {
    def unapply(tree: Tree): Option[(Name, List[List[Tree]])] = tree match {
      case Apply(Select(NestedMethod(name, lowLevelArgs), TermName("apply")), args) =>
        Some((name, args :: lowLevelArgs))
      case Ident(functionName) =>
        Some((functionName, Nil))
      case _ => None
    }
  }

  final override def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case ValDef(mods, name, tpt, MockedNestedLambda(parametersListsCount, from, to)) =>

      val count = tpt.tpe match {
        case TypeRef(_, _, args) => args.size
      }

      nestedMethods += (name.toString -> NestedMethodDeclaration(name.toString, from, to, count, parametersListsCount))
      // remove mock value definition
      EmptyTree

    case original@NestedMethod(name, args) =>
      implementMethod(name, args).getOrElse(transformFurther(original))

    case other =>
      transformFurther(other)
  }
}
