/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.tools.reflect.ToolBox
import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.sources.Imports

/**
 * Adds imports:
 *
 * {{{
 *  import <this package>._
 *  import <context package>._
 *  import <proxies package>._
 *  import <proxies primitives package>._
 *  <imports from file>
 *
 *  <original tree>
 * }}}
 *
 * Could be run both before and after `typecheck`.
 */
class AddImports[A <: TypecheckRelation](val toolbox: ToolBox[universe.type], thisPackage: => Option[String])
    extends TransformationPhase[A] {

  import toolbox.u._

  override def transform(tree: Tree): Tree = {
    val proxiesPackageName = org.scalaide.debug.internal.expression.proxies.name
    val contextPackageName = org.scalaide.debug.internal.expression.context.name
    val primitiveProxiesPackageName = org.scalaide.debug.internal.expression.proxies.primitives.name

    // import from enclosing package (if one exists)
    val thisPackageImport = thisPackage.map(name => s"import $name._").getOrElse("")
    val importsFromCurrentFile = Imports.importsFromCurrentStackFrame.mkString("\n")

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
