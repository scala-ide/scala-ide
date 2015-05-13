/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import Names.Debugger

/**
 * Transformer for converting `toString` method invocations on proxies.
 *
 * Transforms:
 * {{{
 *   list.toString
 * }}}
 * into:
 * {{{
 *   __context.stringify(list)
 * }}}
 */
class MockToString
  extends AstTransformer[AfterTypecheck] {

  import universe._

  /** Creates a proxy to replace `toString` call. */
  private def createProxy(proxy: Tree): Tree =
    Apply(
      SelectMethod(Debugger.contextParamName, Debugger.stringifyMethodName),
      List(proxy))

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case q"$on.toString()" => createProxy(on)
    case other => transformFurther(other)
  }
}
