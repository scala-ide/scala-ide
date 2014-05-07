/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Thanks to it boolean proxies can be used as condition in if-else, while and do-while expressions.
 */
case class MockConditionalExpressions(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer(typesContext) {

  import toolbox.u._

  private def getBooleanValueFromProxy(booleanTree: Tree) = Select(booleanTree, newTermName("booleanValue"))

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    // while and do-while expressions also use if
    case If(cond, thenp, elsep) =>
      If(
        getBooleanValueFromProxy(transformSingleTree(cond, transformFurther)),
        transformSingleTree(thenp, transformFurther),
        transformSingleTree(elsep, transformFurther))
    case other => transformFurther(other)
  }
}