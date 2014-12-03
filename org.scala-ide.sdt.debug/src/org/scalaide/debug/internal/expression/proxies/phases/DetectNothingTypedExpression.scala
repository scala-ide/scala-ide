/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.tools.reflect.ToolBox
import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.NothingTypeInferredException
import org.scalaide.debug.internal.expression.TransformationPhase

/**
 * Break up expression execution if expression will ends up with Nothing
 */
case class DetectNothingTypedExpression(toolbox: ToolBox[universe.type]) extends TransformationPhase {

  import toolbox.u._

  override def transform(baseTree: Tree): Tree = {
    baseTree match {
      case Block(_, epxr)
        if epxr.tpe.typeSymbol.fullName == Names.Scala.nothingType =>
        throw NothingTypeInferredException
      case _ => baseTree
    }
  }
}
