/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

/**
 * Break up expression execution if expression will ends up with Nothing.
 */
class DetectNothingTypedExpression
    extends TransformationPhase[AfterTypecheck] {

  import universe._

  override def transform(baseTree: Tree): Tree = {
    baseTree match {
      case Block(_, epxr) if epxr.tpe.typeSymbol.fullName == Names.Scala.nothingType =>
        throw NothingTypeInferredException
      case _ => baseTree
    }
  }
}
