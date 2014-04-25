/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression._
import org.scalaide.debug.internal.expression.ClassListener.NewClassContext

/**
 * Resposible for extracting all functions creation from code and rewrite it to proxy
 * create new class for each function and compiles it
 * New function is named like '_randomString._randomString$CustomFunction2v4'
 */
case class MockLambdas(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer(typesContext)
  with AnonymousFunctionSupport {

  import toolbox.u

  private def hasByNameParams(byNames: Option[Seq[Boolean]]): Boolean =  byNames.forall(bools => bools.isEmpty || bools.forall(!_))

  protected override def transformSingleTree(baseTree: u.Tree, transformFurther: (u.Tree) => u.Tree): u.Tree = baseTree match {
    case fun @ u.Function(params, body) if !isStartFunctionForExpression(params) =>
      //search for witch FunctionXJdiProxy should be used
      val parentType = typesContext.treeTypeName(fun).getOrElse(throw new RuntimeException("Function must have type!"))
      createAndCompileNewFunction(params, body, parentType)

    case apply @ u.Apply(func, args) =>
      val byNames = extractByNameParams(func)
      if (hasByNameParams(byNames)) transformFurther(apply)
      else {
        val newArgs = args zip byNames.get map {
          case (tree, false) =>
            transformSingleTree(tree, transformFurther)
          case (tree, true) =>
            createAndCompileNewFunction(Nil, tree, ScalaFunctions.Function0)
        }
        u.Apply(transformSingleTree(func, transformFurther), newArgs)
      }
    case tree =>
      transformFurther(tree)
  }
}
