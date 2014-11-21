/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.AstTransformer
import scala.tools.reflect.ToolBox
import scala.reflect.runtime._

/**
 * Flattens the functions parameters lists and remove types from functions
 */
case class FlattenFunctions(toolbox: ToolBox[universe.type]) extends AstTransformer {

  import toolbox.u._

  private def flattenFunction(transformFunction: (Tree => Tree), tree: Tree): (Option[List[Tree]], Tree) = {
    tree match {
      //select part of function
      case select @ Select(qualifier, name) if select.symbol.isMethod =>
        None -> transformFunction(select)

      //flatten parameters lists
      case Apply(func, args) =>
        val newArgs = args.map(arg => transformSingleTree(arg, transformFunction))
        flattenFunction(transformFunction, func) match {
          case (None, transformed) =>
            Some(newArgs) -> transformed
          case (Some(nextArguments), transformed) =>
            Some(nextArguments ++ newArgs) -> transformed
        }

      case TypeApply(select @ Select(_, name), typeTree) if name.toString == Names.Debugger.primitiveValueOfProxyMethodName =>
        None -> TypeApply(transformFunction(select), typeTree)

      //remove types from function (e.g. fun[Ala](...) becomes fun(...)
      case TypeApply(func, _) =>
        None -> transformFunction(func)

      // not a method
      case any =>
        None -> transformFunction(any)
    }
  }

  override protected def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree =
    flattenFunction(transformFurther, baseTree) match {
      case (None, tree) => tree
      case (Some(args), func) => Apply(func, args)
    }
}
