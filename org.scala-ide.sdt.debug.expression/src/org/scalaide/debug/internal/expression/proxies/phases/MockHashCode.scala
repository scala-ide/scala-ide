/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names.Debugger

/**
 * Transformer for converting `hashCode` method invocations on proxies.
 *
 * Transforms:
 * {{{
 *   list.hashCode
 * }}}
 * into:
 * {{{
 *   __context.generateHashCode(list)
 * }}}
 */
class MockHashCode
  extends AstTransformer[AfterTypecheck] {

  import universe._

  /** Creates a proxy to replace `hashCode` call. */
  private def createProxy(proxy: Tree): Tree =
    Apply(
      SelectMethod(Debugger.contextParamName, Debugger.hashCodeMethodName),
      List(proxy))

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Apply(Select(on, TermName("hashCode")), _) => createProxy(on)
    case other => transformFurther(other)
  }
}
