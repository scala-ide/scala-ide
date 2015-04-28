/*
 * Copyright (c) 2014 -2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import Names.Scala

/**
 * Responsible for extracting all functions created from code and rewriting them to proxies.
 * Creates new class for each function and compiles it.
 *
 * New function is named like '__randomString.__randomString$CustomFunction2v4'.
 *
 * Transforms:
 * {{{
 *   list.map[Int, List[Int]](((i: Int) => i.+(int)))(immutable.this.List.canBuildFrom[Int])
 * }}}
 * into:
 * {{{
 *   list.map[Int, List[Int]](__context.newInstance(
 *     <random-name-of-compiled-lambda>, Seq.apply(int)
 *   ))(immutable.this.List.canBuildFrom[Int])
 * }}}
 */
case class MockLambdas(toolbox: ToolBox[universe.type])
  extends AnonymousFunctionSupport[AfterTypecheck] {

  import toolbox.u._

  private def hasByNameParams(byNames: Option[Seq[Boolean]]): Boolean =  byNames.forall(bools => bools.isEmpty || bools.forall(!_))

  protected override def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree = baseTree match {
    case fun @ Function(params, body) if !isStartFunctionForExpression(params) =>
      //search for FunctionXJdiProxy which should be used
      createAndCompileNewFunction(params, body)

    case apply @ Apply(func, args) =>
      val byNames = extractByNameParams(func)
      if (hasByNameParams(byNames)) transformFurther(apply)
      else {
        val newArgs = args zip byNames.get map {
          case (tree, false) =>
            transformSingleTree(tree, transformFurther)
          case (tree, true) =>
            createAndCompileNewFunction(Nil, tree)
        }
        Apply(transformSingleTree(func, transformFurther), newArgs)
      }
    case tree =>
      transformFurther(tree)
  }
}
