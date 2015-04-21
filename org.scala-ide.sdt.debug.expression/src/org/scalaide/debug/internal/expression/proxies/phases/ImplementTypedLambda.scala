/*
 * Copyright (c) 2014 -2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

/**
 * Implement all mock lambdas created during `MockTypedLambda` phase
 *
 * Transforms:
 * {{{
 *   list.map[Int, List[Int]](org.scalaide.debug.internal.expression.context.JdiContext.placeholderFunction1[Int](
 *     "<random-name-of-compiled-lambda>", collection.this.Seq.apply[Int](int)
 *   ))(immutable.this.List.canBuildFrom[Int])
 * }}}
 * into:
 * {{{
 *   list.map[Int, List[Int]](__context.newInstance(
 *     "<random-name-of-compiled-lambda>", Seq.apply(int)
 *   ))(immutable.this.List.canBuildFrom[Int])
 * }}}
 */
case class ImplementTypedLambda(toolbox: ToolBox[universe.type], typesContext: TypesContext)
    extends AstTransformer[AfterTypecheck]
    with AnonymousFunctionSupport {

  import toolbox.u._

  protected def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree = baseTree match {
    case PlaceholderFunction(proxyType, _, closureArgs) =>
      lambdaProxy(proxyType, closureArgs)
    case _ => transformFurther(baseTree)
  }

}
