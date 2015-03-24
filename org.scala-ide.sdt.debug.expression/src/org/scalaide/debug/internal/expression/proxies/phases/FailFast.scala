/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

/**
 * Transformer for failing fast if user uses some unsupported feature.
 *
 * This transformer runs before `typecheck` - those features are unsupported at all.
 */
class FailFast
    extends AstTransformer[BeforeTypecheck] {

  import universe._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case ValDef(modifiers, _, _, _) if modifiers.hasFlag(Flag.LAZY) =>
      throw new UnsupportedFeature("lazy val")
    case Try(_, _, _) =>
      throw new UnsupportedFeature("try/catch/finally")
    case Super(_) =>
      throw new UnsupportedFeature("super")
    case Return(_) =>
      throw new UnsupportedFeature("return")
    case Select(New(Ident(TypeName("$anon"))), termNames.CONSTRUCTOR) =>
      throw new UnsupportedFeature("refined types")
    case other =>
      transformFurther(other)
  }
}

/**
 * Transformer for failing fast if user uses some unsupported feature.
 *
 * This transformer runs after `typecheck` - those features are only supported inside lambdas.
 */
class AfterTypecheckFailFast
    extends AstTransformer[AfterTypecheck] {

  import universe._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Match(_, _) =>
      throw new UnsupportedFeature("pattern matching")
    case Throw(_) =>
      throw new UnsupportedFeature("throw")
    case other =>
      transformFurther(other)
  }
}
