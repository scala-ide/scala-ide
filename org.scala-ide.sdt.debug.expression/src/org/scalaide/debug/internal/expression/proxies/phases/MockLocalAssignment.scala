/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

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
 * @param unboundVariables by-name of unbound variables in current frame (used to check if variable is local)
 */
class MockLocalAssignment(unboundVariables: => Set[UnboundVariable])
    extends AstTransformer[AfterTypecheck] {

  import universe._

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
