/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.debug.internal.expression.TypesContext

case class SearchForUnboundVariables(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends TransformationPhase
  with UnboundValuesSupport {

  import toolbox.u._

  override def transform(tree: universe.Tree): universe.Tree = {
    val unboundNames = new VariableProxyTraverser(tree, _ => None).findUnboundNames()
    typesContext.addUnboundVariables(unboundNames)
    tree
  }

}