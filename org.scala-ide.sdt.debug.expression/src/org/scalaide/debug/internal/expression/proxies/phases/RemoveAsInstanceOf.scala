/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe


/**
 * Transformer for removing `asInstanceOf` method invocations on proxies. Those are not needed after `typecheck`,
 * as all our method calls are dynamically typed (actual types are checked during execution).
 *
 * Example transformation:
 * {{{
 *  value.asInstanceOf[Type]
 * }}}
 * to:
 * {{{
 *  value
 * }}}
 */
class RemoveAsInstanceOf
    extends AstTransformer[AfterTypecheck] {

  import universe._

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case TypeApply(Select(on, TermName("asInstanceOf")), List(TypeTree())) => on
    case other => transformFurther(other)
  }
}
