/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.scalaide.debug.internal.expression.AstTransformer
import scala.tools.reflect.ToolBox
import scala.reflect.runtime.universe


case class CleanUpValDefs(toolbox: ToolBox[universe.type]) extends AstTransformer {

  import toolbox.u._

  /**
   * Basic method for transforming a tree
   * for setting further in tree it should call transformFurther but not transformSingleTree or transform method
   * @param baseTree tree to transform
   * @param transformFurther call it on tree node to recursively transform it further
   */
  override protected def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree = {
    baseTree match {
      case ValDef(params, name, _, impl) =>
        ValDef(params, name, TypeTree(), transformFurther(impl))
      case other => transformFurther(other)
    }
  }
}
