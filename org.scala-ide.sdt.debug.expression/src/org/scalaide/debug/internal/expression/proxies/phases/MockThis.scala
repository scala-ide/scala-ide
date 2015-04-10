/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.TypesContext
import org.scalaide.debug.internal.expression.BeforeTypecheck

/**
 * Transformer for converting `this` usages into special variable that stubs calls to `this`.
 */
case class MockThis(toolbox: ToolBox[universe.type])
  extends AstTransformer
  with BeforeTypecheck {

  import toolbox.u._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case This(thisName) => Ident(TermName(Debugger.thisValName))
    case other => transformFurther(other)
  }
}
