package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Implement all mock lambdas created during 'MockTypedLabdas' phase
 */
case class ImplementTypedLambda(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer
  with AnonymousFunctionSupport {

  import toolbox.u._

  protected def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree = baseTree match {
    case PlaceholderFunction(proxyType, _, closureArgs) =>
      lambdaProxy(proxyType, closureArgs)
    case _ => transformFurther(baseTree)
  }

}
