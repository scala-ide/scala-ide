/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.Names.Debugger

/**
 * Mocks assignment to unbound local (to frame) variables.
 *
 * Transforms:
 * {{{
 *  localString = <expression>
 * }}}
 * to:
 * {{{
 *  __context.setLocalVariable("localString", <expression>)
 * }}}
 *
 * This phase runs after `typecheck`.
 *
 * @param unboundVariables by-name of unbound variables in current frame (used to check if variable is local)
 */
class MockLocalAssignment(val toolbox: ToolBox[universe.type], unboundVariables: => Set[UnboundVariable])
    extends AstTransformer {

  import toolbox.u._

  def isLocalUnboundVariable(termName: TermName): Boolean =
    unboundVariables.contains(UnboundVariable(termName, isLocal = true))

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Assign(Ident(termName: TermName), value) if isLocalUnboundVariable(termName) =>
      Apply(
        SelectMethod(Debugger.contextParamName, Debugger.setLocalVariable),
        List(Literal(Constant(termName.toString)), value))
    case other => transformFurther(other)
  }
}
