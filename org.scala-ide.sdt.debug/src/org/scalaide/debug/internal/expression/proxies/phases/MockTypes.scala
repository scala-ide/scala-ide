/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression._
import org.scalaide.debug.internal.expression.FunctionStub

/**
 * Finds all types that should be stubbed.
 * Firstly finds all types that must be stubbed and all methods used on those types
 * Than Mock all type occurences to stubs
 * This class use heavyly TypeContext
 */
case class MockTypes(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer {

  import toolbox.u._

  /**  create type name for given type */
  private def createTypeTree(name: String): Tree =
    toolbox.parse(s"var a: $name = null") match {
      case ValDef(_, _, tpt, _) => tpt
    }

  /** See `AstTransformer.transformSingleTree`. */
  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = {
    tree match {
      case TypeApply(func, _) => transformFurther(func)
      case typeTree: TypeTree =>
        typesContext.treeTypeFromContext(transformFurther(typeTree)).map(createTypeTree).getOrElse(typeTree)
      case any => transformFurther(any)
    }
  }

}