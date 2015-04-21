/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import Names.Debugger

/**
 * Transformer for converting `this` usages into special variable that stubs calls to `this`.
 *
 * Transforms:
 * {{{
 *   this.foo(a, b)
 * }}}
 *
 * to:
 * {{{
 *   __this.foo(a, b)
 * }}}
 *
 * This transformation runs before `typecheck`.
 */
class MockThis
  extends AstTransformer[BeforeTypecheck] {

  import universe._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case This(thisName) => Ident(TermName(Debugger.thisValName))
    case other => transformFurther(other)
  }
}
