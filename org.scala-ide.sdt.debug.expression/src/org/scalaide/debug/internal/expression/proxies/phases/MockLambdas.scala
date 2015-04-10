/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Responsible for extracting all functions creation from code and rewriting it to proxy
 * Creates new class for each function and compiles it
 * New function is named like '__randomString.__randomString$CustomFunction2v4'
 */
case class MockLambdas(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer
  with AnonymousFunctionSupport {

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
