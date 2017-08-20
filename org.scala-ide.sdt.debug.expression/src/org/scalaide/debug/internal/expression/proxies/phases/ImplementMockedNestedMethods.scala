/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.context.NestedMethodDeclaration
import org.scalaide.debug.internal.expression.context.VariableContext
import org.scalaide.debug.internal.expression.Names.Debugger._

/**
 * Tries to implement all found nested methods mocks.
 * It removes mock values definitions and rewrites nested method call.
 *
 * Transforms:
 * {{{
 *   val simpleNested: Int => String = org.scalaide.debug.internal.expression.context.JdiContext.placeholderNestedMethod(1, 8, 9);
 *   simpleNested.apply(1)
 * }}}
 * into:
 * {{{
 *   __this_1.simpleNested$1(1)
 * }}}
 */
class ImplementMockedNestedMethods(context: VariableContext)
    extends AstTransformer[AfterTypecheck] {

  import universe._

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
    else if (applyCount > args.size) throw new UnsupportedFeature("Nested method used as a function (eta expansion)")
    else true

  private def implementMethod(methodName: Name, args: List[List[Tree]]): Option[Tree] = {
    val name = methodName.toString
    for {
      nestedMethodDeclaration <- nestedMethods.get(name)
      if checkParametersListsCount(args, nestedMethodDeclaration.parametersListsCount)
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
      case Ident(methodName) =>
        Some((methodName, Nil))
      case _ => None
    }
  }

  final override def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case ValDef(_, name, tpt, MockedNestedLambda(parametersListsCount, from, to)) =>

      val count = tpt.tpe match {
        case TypeRef(_, _, args) => args.size
      }

      nestedMethods += (name.toString -> NestedMethodDeclaration(name.toString, from, to, count, parametersListsCount))
      // remove mock value definition
      EmptyTree

    case original @ NestedMethod(name, args) =>
      implementMethod(name, args).getOrElse(transformFurther(original))

    case other =>
      transformFurther(other)
  }
}
