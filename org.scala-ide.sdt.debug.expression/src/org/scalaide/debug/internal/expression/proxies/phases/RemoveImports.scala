/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

/**
 * Removes all imports from expression - they are not required after typecheck and before final compilation.
 */
final class RemoveImports
    extends AstTransformer[AfterTypecheck] {

  import universe._

  override protected def transformSingleTree(baseTree: universe.Tree, transformFurther: (universe.Tree) => universe.Tree): universe.Tree = baseTree match {
    case Import(_, _) => EmptyTree
    case rest => transformFurther(rest)
  }
}
