/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.scalaide.debug.internal.expression.{Names, NothingTypeInferredException, TransformationPhase}
import scala.tools.reflect.ToolBox
import scala.reflect.runtime._

case class DetectNothingTypedExpression(toolbox: ToolBox[universe.type]) extends TransformationPhase {

  import toolbox.u._

  /**
   * Transforms current tree to new form.
   * It is called only once per object lifetime.
   * Result of this method is passed to another TransformationPhase instance.
   * @param baseTree tree to transform
   */
  override def transform(baseTree: Tree): Tree = {
    baseTree match {
      case Block(_, epxr)
        if epxr.tpe.typeSymbol.fullName == Names.Scala.nothingType =>
        throw NothingTypeInferredException
      case _ => baseTree
    }
  }
}
