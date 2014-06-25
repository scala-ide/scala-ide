/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.DebuggerSpecific
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Transformer for converting `this` usages into special variable that stubs calls to `this`.
 */
case class MockThis(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer {

  import toolbox.u._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case This(thisName) => Ident(newTermName(DebuggerSpecific.thisValName))
    case other => transformFurther(other)
  }
}
