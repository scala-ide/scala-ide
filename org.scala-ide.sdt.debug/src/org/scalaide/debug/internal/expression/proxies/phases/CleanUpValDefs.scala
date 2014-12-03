/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.scalaide.debug.internal.expression.AstTransformer
import scala.tools.reflect.ToolBox
import scala.reflect.runtime.universe


/**
 * Removes all types from values definitions (it all became JdiProxy)
 * @param toolbox
 */
case class CleanUpValDefs(toolbox: ToolBox[universe.type]) extends AstTransformer {

  import toolbox.u._

  override protected def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree = {
    baseTree match {
      case ValDef(params, name, _, impl) =>
        ValDef(params, name, TypeTree(), transformFurther(impl))
      case other => transformFurther(other)
    }
  }
}
