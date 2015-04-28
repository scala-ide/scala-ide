/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.logging.HasLogger

/**
 * Typechecks given code using `toolbox.typecheck`.
 */
case class TypeCheck(toolbox: ToolBox[universe.type])
    extends TransformationPhase[IsTypecheck]
    with HasLogger {

  override def transform(data: TransformationPhaseData): TransformationPhaseData = {
    val newTree = toolbox.typecheck(data.tree)
    data.after(phaseName, newTree)
  }
}
