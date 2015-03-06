/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.tools.reflect.ToolBox
import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.AstTransformer

/**
 * Removes all imports - from inside of expression - they are not required after typecheck
 * @param toolbox
 */
final case class RemoveImports(toolbox: ToolBox[universe.type]) extends AstTransformer {

  import toolbox.u._

  override protected def transformSingleTree(baseTree: universe.Tree, transformFurther: (universe.Tree) => universe.Tree): universe.Tree = baseTree match {
    case Import(_, _) => EmptyTree
    case rest => transformFurther(rest)
  }
}
