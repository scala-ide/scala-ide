/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.BeforeTypecheck

/**
 * If your expression contains only single `val` or `def` wrap it in block (`{}`) as a workaround for bug in toolbox.
 */
class SingleValDefWorkaround
  extends AstTransformer[BeforeTypecheck] {

  import universe._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case valDef @ ValDef(_, _, _, _) => Block(List(valDef), Literal(Constant(())))
    case defDef @ DefDef(_, _, _, _, _, _) => Block(List(defDef), Literal(Constant(())))
    case other => other
  }
}
