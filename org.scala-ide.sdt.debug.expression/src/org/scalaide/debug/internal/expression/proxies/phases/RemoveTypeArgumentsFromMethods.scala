/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import Names.Debugger

/**
 * Removes type arguments from methods.
 *
 * Transforms:
 * {{{
 *   foo.method[Int](int)
 * }}}
 * into:
 * {{{
 *   foo.method(int)
 * }}}
 */
class RemoveTypeArgumentsFromMethods extends AstTransformer[AfterTypecheck] {

  import universe._

  private def flattenArgumentLists(transformFurther: (Tree => Tree), tree: Tree): (Option[List[Tree]], Tree) = {
    tree match {
      // exclude our own method call to `__value[A]` from removal
      case TypeApply(select @ Select(_, TermName(Debugger.primitiveValueOfProxyMethodName)), typeTree) =>
        None -> TypeApply(transformFurther(select), typeTree)

      // removes type arguments from method (e.g. fun[Ala](...) becomes fun(...)
      case TypeApply(func, _) =>
        None -> transformFurther(func)

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
