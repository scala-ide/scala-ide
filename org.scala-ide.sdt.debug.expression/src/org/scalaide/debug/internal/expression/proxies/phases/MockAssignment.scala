/*
 * Copyright (c) 2014-2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.NameTransformer
import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names.Debugger

/**
 * Mocks assignment to non-local unbound variables.
 *
 * Transforms:
 * {{{
 *   localString = <expression>
 * }}}
 * to:
 * {{{
 *   __this.localString_$eq(<expression>)
 * }}}
 *
 * @param unboundVariables by-name of unbound variables in current frame (used to check if variable is local)
 */
class MockAssignment
    extends AstTransformer[BeforeTypecheck] {

  import universe._

  def isNonLocalUnboundVariable(termName: TermName): Boolean =
    data.unboundVariables.contains(UnboundVariable(termName, isLocal = false))

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Assign(Ident(termName: TermName), value) if isNonLocalUnboundVariable(termName) =>
      Apply(
        SelectMethod(Debugger.thisValName, termName + NameTransformer.SETTER_SUFFIX_STRING),
        List(value))
    case other => transformFurther(other)
  }
}
