/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Transformation phase changing code defining mock proxies into code that actually implements them.
 */
case class ImplementValues(toolbox: ToolBox[universe.type], valueCreationCode: universe.TermName => Option[String])
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
    case ValDef(mods, name, tpt, value) if isProxy(value) =>
      val valImpl = proxyImplementation(name, tpt.toString)
      ValDef(mods, name, tpt, valImpl)
    case other =>
      other
  }

  /** Checks if name corresponds to `this` stub */
  private def isThisMethodStub(name: Name): Boolean =
    name.toString.startsWith(Debugger.thisValName)

  /** Checks if a tree is a placeholder put there in previous phase. */
  private def isProxy(value: Tree): Boolean = {
    import Debugger._
    value.toString == contextFullName + "." + placeholderName
  }

  /**
   * Creates a proxy of given type for given name.
   *
   * @return tree representing proxy factory method call
   */
  private def proxyImplementation(name: TermName, typeName: String): Tree = {
    import Debugger._
    val code = valueCreationCode(name).getOrElse(s"""$contextParamName.$valueProxyMethodName("$name")""")
    wrapInType(typeName)(code)
  }

  /**
   * check if impementation should be wrapped in given type and if yes wraps it
   */
  private def wrapInType(typeName: String)(code: String): Tree = {
    val realCode = if (typeName == Debugger.proxyFullName) code
    else s"""$typeName($code)"""
    toolbox.parse(realCode)
  }

}