/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.tools.reflect.ToolBox
import scala.reflect.runtime._

import org.scalaide.debug.internal.expression.Names.Debugger

trait PrimitivesCommons {
  self: AstTransformer[_] =>
  import universe._

  /** creates code that pack primitive to proxy */
  protected final def packPrimitive(primitiveTree: Tree) =
    Apply(SelectMethod(Debugger.contextParamName, "proxy"), List(primitiveTree))
}
