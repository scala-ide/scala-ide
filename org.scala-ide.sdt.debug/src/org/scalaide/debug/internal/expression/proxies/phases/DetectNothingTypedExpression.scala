/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.scalaide.debug.internal.expression.{Names, NothingTypeInferredException, TransformationPhase}
import scala.tools.reflect.ToolBox
import scala.reflect.runtime._

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
