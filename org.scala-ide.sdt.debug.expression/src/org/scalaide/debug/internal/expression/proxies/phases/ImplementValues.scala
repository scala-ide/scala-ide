/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.Names.Debugger

/**
 * Transforms code defining mock proxies into code that actually implements them.
 * Also removes all types from values definitions (it all becomes JdiProxy).
 *
 * Transforms:
 * {{{
 *   val list: List[Int] = org.scalaide.debug.internal.expression.context.JdiContext.placeholder;
 *   val int: Int = org.scalaide.debug.internal.expression.context.JdiContext.placeholder;
 *   val __this: test.Values.type = org.scalaide.debug.internal.expression.context.JdiContext.placeholder;
 * }}}
 * into:
 * {{{
 *   val list = __context.valueProxy("list");
 *   val int = __context.valueProxy("int");
 *   val __this = __context.thisObjectProxy;
 * }}}
 */
case class ImplementValues(toolbox: ToolBox[universe.type], valueCreationCode: universe.TermName => Option[String])
  extends AstTransformer[AfterTypecheck] {

  import toolbox.u._

  /**
   * Transforms a proxy mock definition into actual proxy creating code
   */
  final override def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = transformFurther(tree) match {
    case ValDef(mods, name, tpt, value) if isProxy(value) =>
      val valImpl = proxyImplementation(name, tpt.toString)
      ValDef(mods, name, TypeTree(), valImpl)
    case ValDef(mods, name, tpt, value) =>
      ValDef(mods, name, TypeTree(), value)
    case other =>
      other
  }

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
    toolbox.parse(valueCreationCode(name).getOrElse(s"""$contextParamName.$valueProxyMethodName("$name")"""))
  }
}
