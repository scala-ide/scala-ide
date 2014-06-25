/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.debug.internal.expression.ExpressionEvaluator
import org.scalaide.debug.internal.expression.TypesContext

case class GenerateStubs(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends TransformationPhase {

  import toolbox.u._

  /** Breaks generated block of code into seq of ClassDef */
  private def breakClassDefBlock(code: Tree): Seq[Tree] = breakBlock(code) {
    case classDef: ClassDef => Seq(classDef)
  }

  /** Adds additional expressions to one created by `wrapInExpression` */
  private def insertAtFunctionStart(blockWithFunction: Tree, newCode: Seq[Tree]): Tree = {
    val Block(importStms, Function(args, body)) = blockWithFunction
    val newFunction = Function(args, Block(newCode.toList, body))
    Block(importStms, newFunction)
  }

  override def transform(tree: Tree): Tree = {
    val newTypes = typesContext.typesStubCode

    if (newTypes != "") {
      val newTypesCode = toolbox.parse(newTypes)
      val newCodeLines = breakClassDefBlock(newTypesCode)

      insertAtFunctionStart(tree, newCodeLines)
    } else tree
  }
}