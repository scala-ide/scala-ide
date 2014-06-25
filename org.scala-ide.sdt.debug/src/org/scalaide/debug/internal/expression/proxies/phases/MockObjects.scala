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
import org.scalaide.debug.internal.expression.ExpressionEvaluator
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Extract and transforms all object to valid proxies
 * Eg. call to object 'Ala' is transformed to $o_ala_package_Ala_stub(__proxy.objectProxy("ala.package.Ala"))
 * where:
 * - $o_ala_package_Ala_stub() - generated stub for this object
 * - __proxy.objectProxy("ala.package.Ala") - obtain object from context
 */
case class MockObjects(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer {

  import toolbox.u._

  /**
   * Check if given select is object.
   * We consioder select as object if isModule (scala's AST name for object) an it is not JdiContext and package
   */
  private def isObject(select: Select): Boolean =
    select.tpe != null &&
      select.symbol.isModule &&
      !select.symbol.isPackage &&
      select.toString != DebuggerSpecific.contextFullName

  /** generate and parse object code */
  private def createProxy(select: Tree, context: TypesContext): Tree = {
    val objectType = context.treeTypeName(select).getOrElse(throw new RuntimeException("object must have type!"))

    val stubClass = context.typeNameFor(objectType)

    // generates code like $o_ala_package_Ala_stub(__proxy.objectProxy("ala.package.Ala"))
    toolbox.parse(s"""$stubClass(${DebuggerSpecific.contextParamName}.${DebuggerSpecific.objectProxyMethodName}("$objectType"))""")
  }

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case select: Select if isObject(select) => createProxy(select, typesContext)
    case other => transformFurther(other)
  }
}
