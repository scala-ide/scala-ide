/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.MethodStub
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Finds all types that should be stubbed.
 * Firstly finds all types that must be stubbed and all methods used on those types
 * Than Mock all type occurrences to stubs
 * This class uses heavily TypeContext
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
        typesContext.treeStubType(transformFurther(typeTree)).map(createTypeTree).getOrElse(typeTree)
      case any => transformFurther(any)
    }
  }

}