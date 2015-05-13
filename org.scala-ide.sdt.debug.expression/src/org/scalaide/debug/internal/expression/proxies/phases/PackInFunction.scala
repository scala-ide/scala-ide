/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.tools.reflect.ToolBox
import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names.Debugger

/**
 * Wraps the tree in expression:
 *
 * {{{
 *  (context: JdiContext) => <original tree>
 * }}}
 *
 * Runs after `typecheck`.
 */
class PackInFunction(val toolbox: ToolBox[universe.type])
    extends TransformationPhase[AfterTypecheck] {

  import toolbox.u._

  override def transform(data: TransformationPhaseData): TransformationPhaseData = {
    // gets AST of function arguments required in expression
    val functionArgs: List[ValDef] = {
      import Debugger._

      val dummyFunction = s"""($contextParamName: $contextFullName) => ???"""
      val parsed = toolbox.parse(dummyFunction)
      val Function(args, _) = toolbox.typecheck(parsed)
      args
    }

    val newTree = Function(functionArgs, data.tree)
    data.after(phaseName, newTree)
  }
}
