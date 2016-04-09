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
class AddImports[A <: TypecheckRelation](val toolbox: ToolBox[universe.type], thisPackage: => Option[String], includeSourceFile: Boolean = false)
    extends TransformationPhase[A] {

  import toolbox.u._

  override def transform(data: TransformationPhaseData): TransformationPhaseData = {
    val proxiesPackageName = org.scalaide.debug.internal.expression.proxies.name
    val contextPackageName = org.scalaide.debug.internal.expression.context.name
    val primitiveProxiesPackageName = org.scalaide.debug.internal.expression.proxies.primitives.name

    val debuggerImportsRoots = thisPackage.toList ++ List(contextPackageName, proxiesPackageName, primitiveProxiesPackageName)
    val debuggerImports = debuggerImportsRoots.map(root => s"import $root._")
    val allImports = if (includeSourceFile) debuggerImports +: Imports.importsFromCurrentStackFrame else Seq(debuggerImports)

    val newTree = allImports.foldRight(data.tree) {
      case (imports, tree) =>
        Block(imports.map(toolbox.parse), tree)
    }
    data.after(phaseName, newTree)
  }
}
