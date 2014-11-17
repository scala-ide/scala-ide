package org.scalaide.debug.internal.expression.proxies.phases

import org.scalaide.debug.internal.expression.AstTransformer
import scala.tools.reflect.ToolBox
import scala.reflect.runtime._

/**
 * Author: Krzysztof Romanowski
 */
case class FlattenFunctions(toolbox: ToolBox[universe.type]) extends AstTransformer {

  import toolbox.u._


  private def flattenFunction(transformFunction: (Tree => Tree), tree: Tree): (Option[List[Tree]], Tree) = {
    tree match {
      //select part of function
      case select@Select(qualifier, name) if select.symbol.isMethod =>
        None -> transformFunction(select)

      //implicit parameter lists
      case _@Apply(func, args) =>
        val newArgs = args.map(transformFunction)
        flattenFunction(transformFunction, func) match {
          case (None, transformed) =>
            Some(newArgs) -> transformed
          case (Some(nextArguments), transformed) =>
            Some(nextArguments ++ newArgs) -> transformed
        }

      // not a method
      case any =>
        None -> transformFunction(any)
    }
  }

  /**
   * Basic method for transforming a tree
   * for setting further in tree it should call transformFurther but not transformSingleTree or transform method
   * @param baseTree tree to transform
   * @param transformFurther call it on tree node to recursively transform it further
   */
  override protected def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree =
    flattenFunction(transformFurther, baseTree) match {
      case (None, tree) => tree
      case (Some(args), func) => Apply(func, args)
    }
}
