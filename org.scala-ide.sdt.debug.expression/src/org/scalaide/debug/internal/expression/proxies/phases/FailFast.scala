/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.BeforeTypecheck
import org.scalaide.debug.internal.expression.UnsupportedFeature

/**
 * Transformer for failing fast if user uses some unsupported feature.
 *
 * This transformer works on untyped trees - before typecheck.
 */
case class FailFast(toolbox: ToolBox[universe.type])
  extends AstTransformer
  with BeforeTypecheck {

  import toolbox.u._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case TypeApply(Select(on, method), List(tpe)) if method.toString == "asInstanceOf" =>
      throw new UnsupportedFeature("asInstanceOf")
    case TypeApply(Select(on, method), List(tpe)) if method.toString == "isInstanceOf" =>
      throw new UnsupportedFeature("isInstanceOf")
    case Apply(on, args) if on.toString == "Array" =>
      throw new UnsupportedFeature("Array.apply()")
    case TypeApply(on, args) if on.toString == "Array" =>
      throw new UnsupportedFeature("Array.apply()")
    case Try(_, _, _) =>
      throw new UnsupportedFeature("try/catch/finally")
    case other =>
      transformFurther(other)
  }
}
