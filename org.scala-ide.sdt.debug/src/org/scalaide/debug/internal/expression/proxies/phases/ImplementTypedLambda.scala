package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.DebuggerSpecific.contextFullName
import org.scalaide.debug.internal.expression.DebuggerSpecific.placeholderFunctionName
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Implement all mock lambdas created durinf 'MockTypedLabdas' phase
 */
case class ImplementTypedLambda(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer(typesContext) with AnonymousFunctionSupport {

  import toolbox.u

  private val placeholderFuncitonString = {
    import org.scalaide.debug.internal.expression.DebuggerSpecific._
    s"$contextFullName.$placeholderFunctionName"
  }

  private val placeholderPartialFuncitonString = {
    import org.scalaide.debug.internal.expression.DebuggerSpecific._
    s"$contextFullName.$placeholderPartialFunctionName"
  }

  private def isPlaceholerFunction(fun: u.Tree) = {
    val treeString = fun.toString()
    treeString.startsWith(placeholderPartialFuncitonString) ||
      treeString.startsWith(placeholderFuncitonString)
  }


  protected def transformSingleTree(baseTree: u.Tree, transformFurther: (u.Tree) => u.Tree): u.Tree = baseTree match {
    case u.Apply(u.TypeApply(fun, _), List(u.Literal(u.Constant(proxyType)))) if isPlaceholerFunction(fun) =>
      lambdaProxy(proxyType.toString)
    case _ => transformFurther(baseTree)
  }

}
