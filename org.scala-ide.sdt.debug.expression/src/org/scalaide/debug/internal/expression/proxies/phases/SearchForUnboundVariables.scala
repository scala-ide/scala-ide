/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

/**
 * Searches for all unbound variables in scope.
 *
 * Does not transform the tree in any way.
 *
 * @param localVariablesNames list of names for `val`s and `var`s defined in current method
 */
class SearchForUnboundVariables(val toolbox: ToolBox[universe.type], localVariablesNames: => Set[String])
    extends TransformationPhase[BeforeTypecheck]
    with UnboundValuesSupport {

  import toolbox.u._

  override def transform(data: TransformationPhaseData): TransformationPhaseData = {
    val unboundNames = new VariableProxyTraverser(data.tree, _ => None, localVariablesNames).findUnboundVariables()
    data.after(phaseName, data.tree).withUnboundVariables(unboundNames)
  }

}
