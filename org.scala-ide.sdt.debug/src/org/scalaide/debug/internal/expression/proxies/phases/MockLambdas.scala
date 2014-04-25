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
 * Resposible for extracting all functions creation from code and rewrite it to proxy
 * create new class for each function and compiles it
 * New function is named like '_randomString._randomString$CustomFunction2v4'
 */
case class MockLambdas(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer
  with AnonymousFunctionSupport {

  import toolbox.u._

  private def hasByNameParams(byNames: Option[Seq[Boolean]]): Boolean =  byNames.forall(bools => bools.isEmpty || bools.forall(!_))

  protected override def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree = baseTree match {
    case fun @ Function(params, body) if !isStartFunctionForExpression(params) =>
      //search for witch FunctionXJdiProxy should be used
      val parentType = typesContext.treeTypeName(fun).getOrElse(throw new RuntimeException("Function must have type!"))
      createAndCompileNewFunction(params, body, parentType)

    case apply @ Apply(func, args) =>
      val byNames = extractByNameParams(func)
      if (hasByNameParams(byNames)) transformFurther(apply)
      else {
        val newArgs = args zip byNames.get map {
          case (tree, false) =>
            transformSingleTree(tree, transformFurther)
          case (tree, true) =>
            createAndCompileNewFunction(Nil, tree, Scala.functions.Function0)
        }
        Apply(transformSingleTree(func, transformFurther), newArgs)
      }
    case tree =>
      transformFurther(tree)
  }
}
