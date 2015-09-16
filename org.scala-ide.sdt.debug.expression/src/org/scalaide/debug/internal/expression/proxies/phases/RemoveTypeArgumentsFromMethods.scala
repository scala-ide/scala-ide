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

  override protected def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree =
    tree match {
      // exclude our own method call to `__value[A]` from removal
      case TypeApply(select @ Select(_, TermName(Debugger.primitiveValueOfProxyMethodName)), typeTree) =>
        TypeApply(transformSingleTree(select, transformFurther), typeTree)

      // removes type arguments from method (e.g. fun[Ala](...) becomes fun(...)
      case TypeApply(func, _) =>
        transformSingleTree(func, transformFurther)

      // not a method
      case any =>
        transformFurther(any)
    }
}
