/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe

import scala.tools.reflect.ToolBox
import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.debug.internal.expression.DebuggerSpecific

case class PackInFunction(toolbox: ToolBox[universe.type], thisPackage: Option[String])
  extends TransformationPhase {

  import toolbox.u

  /**
   * Wraps the tree in expression:
   *
   * {{{
   *  import <this package>._
   *  import <proxies package>._
   *  import <proxies primitives package>._
   *
   *  (context: JdiContext) => <ast>
   * }}}
   */
  private def wrapInExpression(ast: u.Tree): u.Tree = {
    val proxiesPackageName = org.scalaide.debug.internal.expression.proxies.name
    val contextPackageName = org.scalaide.debug.internal.expression.context.name
    val primitiveProxiesPackageName = org.scalaide.debug.internal.expression.proxies.primitives.name

    // gets AST of function arguments required in expression
    val functionArgs: List[u.ValDef] = {
      import DebuggerSpecific._
      val dummyFunction = s"""($contextParamName: $contextFullName) => ???"""
      val parsed = toolbox.parse(dummyFunction)
      val u.Function(args, _) = toolbox.typeCheck(parsed)
      args
    }

    // import from enclosing package (if one exists)
    val thisPackageImport = thisPackage.map(name => s"import $name._").getOrElse("")

    val importCode = s"""
      | $thisPackageImport
      | import $contextPackageName._
      | import $proxiesPackageName._
      | import $primitiveProxiesPackageName._
      | ???
      |""".stripMargin

    val u.Block(imports, _) = toolbox.parse(importCode)

    u.Block(imports, u.Function(functionArgs, ast))
  }

  override def transform(tree: u.Tree): u.Tree = wrapInExpression(tree)
}