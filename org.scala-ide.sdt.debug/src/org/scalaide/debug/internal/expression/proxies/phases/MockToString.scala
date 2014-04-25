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
 * Transformer for converting `toString` method invocations on proxies.
 */
case class MockToString(toolbox: ToolBox[universe.type])
  extends AstTransformer {

  import toolbox.u._

  /** Matches `toString` method call. */
  private def isToString(name: Name): Boolean = name.toString == "toString"

  /** Creates a proxy to replace `toString` call. */
  private def createProxy(proxy: Tree): Tree =
    Apply(
      SelectMethod(Debugger.contextParamName, Debugger.stringifyMethodName),
      List(proxy))

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Apply(Select(on, name), _) if isToString(name) => createProxy(on)
    case other => transformFurther(other)
  }
}
