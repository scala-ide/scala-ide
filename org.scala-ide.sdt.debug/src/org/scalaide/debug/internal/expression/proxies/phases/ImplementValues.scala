/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.DebuggerSpecific
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Transformation phase changing code defining mock proxies into code that actually implements them.
 */
case class ImplementValues(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer {

  import toolbox.u._

  /**
   * Transforms a proxy mock definition into actual proxy creating code
   *
   * @param tree processed tree
   * @param transformFurther function to transform tree furhter
   * @return transformed tree
   */
  final override def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = transformFurther(tree) match {
    case ValDef(mods, name, tpt, value) if isThisMethodStub(name) =>
      val thisValImpl = thisProxyImplementation(tpt.toString)
      ValDef(mods, name, tpt, thisValImpl)
    case ValDef(mods, name, tpt, value) if isProxy(value) =>
      val valImpl = proxyImplementation(name.toString, tpt.toString)
      ValDef(mods, name, tpt, valImpl)
    case other =>
      other
  }

  /** Checks if name corresponds to `this` stub */
  private def isThisMethodStub(name: Name): Boolean =
    name.toString == DebuggerSpecific.thisValName


  /** Checks if a tree is a placeholder put there in previous phase. */
  private def isProxy(value: Tree): Boolean = {
    import DebuggerSpecific._
    value.toString == contextFullName + "." + placeholderName
  }

  /**
   * Creates a proxy of given type for given name.
   *
   * @return tree representing proxy factory method call
   */
  private def proxyImplementation(name: String, typeName: String): Tree = {
    import DebuggerSpecific._
    wrapInType(typeName)( s"""$contextParamName.$valueProxyMethodName("$name")""")
  }

  /** Implements proxy for `this` */
  private def thisProxyImplementation(typeName: String): Tree = {
    import DebuggerSpecific._
    wrapInType(typeName)(s"$contextParamName.$thisObjectProxyMethodName()")
  }

  /**
    * check if impementation should be wrapped in given type and if yes wraps it
    */
  private def wrapInType(typeName: String)(code: String): Tree = {
    val realCode = if (typeName == DebuggerSpecific.proxyFullName) code
    else s"""$typeName($code)"""
    toolbox.parse(realCode)
  }

}