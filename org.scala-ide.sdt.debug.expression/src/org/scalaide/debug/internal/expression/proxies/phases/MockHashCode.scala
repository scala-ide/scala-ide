/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Transformer for converting `hashCode` method invocations on proxies.
 */
case class MockHashCode(toolbox: ToolBox[universe.type])
  extends AstTransformer {

  import toolbox.u._

  /** Matches `hashCode` method call. */
  private def isHashCode(name: Name): Boolean = name.toString == "hashCode"

  /** Creates a proxy to replace `hashCode` call. */
  private def createProxy(proxy: Tree): Tree =
    Apply(
      SelectMethod(Debugger.contextParamName, Debugger.hashCodeMethodName),
      List(proxy))

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Apply(Select(on, name), _) if isHashCode(name) => createProxy(on)
    case other => transformFurther(other)
  }
}
