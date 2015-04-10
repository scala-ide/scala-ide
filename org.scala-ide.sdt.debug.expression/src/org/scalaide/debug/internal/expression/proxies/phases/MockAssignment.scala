/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.BeforeTypecheck
import org.scalaide.debug.internal.expression.Names.Debugger

class MockAssignment(val toolbox: ToolBox[universe.type], unboundVariables: => Set[universe.TermName])
  extends AstTransformer
  with BeforeTypecheck {

  import toolbox.u._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Assign(Ident(termName: TermName), value) if unboundVariables.contains(termName) =>
      Apply(
        Select(Ident(TermName(Debugger.thisValName)), TermName(termName + "_$eq")),
        List(value))
    case other => transformFurther(other)
  }
}