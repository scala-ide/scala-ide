/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.scalaide.debug.internal.expression.Names.Debugger
import scala.tools.reflect.ToolBox
import scala.reflect.runtime._
import org.scalaide.debug.internal.expression.AstTransformer

trait PrimitivesCommons extends AstTransformer {

  import universe._

  /** creates code that pack primitive to proxy */
  protected final def packPrimitive(primitiveTree: Tree) =
    Apply(SelectMethod(Debugger.contextParamName, "proxy"), List(primitiveTree))
}
