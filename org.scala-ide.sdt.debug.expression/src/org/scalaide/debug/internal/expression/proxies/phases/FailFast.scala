/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.BeforeTypecheck
import org.scalaide.debug.internal.expression.UnsupportedFeature

/**
 * Transformer for failing fast if user uses some unsupported feature.
 *
 * This transformer works on untyped trees - before typecheck.
 */
class FailFast
  extends AstTransformer[BeforeTypecheck] {

  import universe._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Try(_, _, _) =>
      throw new UnsupportedFeature("try/catch/finally")
    case other =>
      transformFurther(other)
  }
}
