/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import Names.Debugger

/**
 * Flattens the methods parameters lists.
 *
 * Transforms:
 * {{{
 *   foo.method(int)(int)
 * }}}
 * into:
 * {{{
 *   foo.method(int, int)
 * }}}
 */
class FlattenMultiArgListMethods extends AstTransformer[AfterTypecheck] {

  import universe._

  private def flattenArgumentLists(transformFurther: (Tree => Tree), tree: Tree): (Option[List[Tree]], Tree) = {
    tree match {
      // select part of a method
      case select @ Select(qualifier, name) if select.symbol.isMethod =>
        None -> transformFurther(select)

      // flatten parameters lists
      case Apply(func, args) =>
        val newArgs = args.map(arg => transformSingleTree(arg, transformFurther))
        flattenArgumentLists(transformFurther, func) match {
          case (None, transformed) =>
            Some(newArgs) -> transformed
          case (Some(nextArguments), transformed) =>
            Some(nextArguments ++ newArgs) -> transformed
        }

      // not a method
      case any =>
        None -> transformFurther(any)
    }
  }

  override protected def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree =
    flattenArgumentLists(transformFurther, baseTree) match {
      case (None, tree) => tree
      case (Some(args), func) => Apply(func, args)
    }
}
