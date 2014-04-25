/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.tools.reflect.ToolBox
import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.debug.internal.expression.sources.Imports
import org.scalaide.debug.internal.expression.Names.Debugger

class AddImports(val toolbox: ToolBox[universe.type], thisPackage: => Option[String])
  extends TransformationPhase {

  import toolbox.u._

  /**
   * Adds imports:
   *
   * {{{
   *  import <this package>._
   *  import <proxies package>._
   *  import <proxies primitives package>._
   *  <imports from file>
   *
   *  <original tree>
   * }}}
   */
  override def transform(tree: Tree): Tree = {
    val proxiesPackageName = org.scalaide.debug.internal.expression.proxies.name
    val contextPackageName = org.scalaide.debug.internal.expression.context.name
    val primitiveProxiesPackageName = org.scalaide.debug.internal.expression.proxies.primitives.name

    // import from enclosing package (if one exists)
    val thisPackageImport = thisPackage.map(name => s"import $name._").getOrElse("")
    val importsFromCurrentFile = Imports.forCurrentStackFrame.mkString("\n")

    val importCode = s"""
      | $thisPackageImport
      | import $contextPackageName._
      | import $proxiesPackageName._
      | import $primitiveProxiesPackageName._
      | $importsFromCurrentFile
      | ???
      |""".stripMargin

    val Block(imports, _) = toolbox.parse(importCode)

    Block(imports, tree)
  }

}

class PackInFunction(val toolbox: ToolBox[universe.type])
  extends TransformationPhase {

  import toolbox.u._

  /**
   * Wraps the tree in expression:
   *
   * {{{
   *  (context: JdiContext) => <original tree>
   * }}}
   */
  private def wrapInExpression(ast: Tree): Tree = {

    // gets AST of function arguments required in expression
    val functionArgs: List[ValDef] = {
      import Debugger._

      val dummyFunction = s"""($contextParamName: $contextFullName) => ???"""
      val parsed = toolbox.parse(dummyFunction)
      val Function(args, _) = toolbox.typecheck(parsed)
      args
    }

    Function(functionArgs, ast)
  }

  override def transform(tree: Tree): Tree = wrapInExpression(tree)
}