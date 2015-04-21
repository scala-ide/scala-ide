/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.annotation.tailrec
import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.Names.Debugger

/**
 * Extract and transforms all object-like code to valid proxies
 * (it can be also Java static call but we can't distinguish here - there's needed dynamic dispatch on a later level).
 * E.g. call to object:
 * {{{
 *  Ala
 * }}}
 * is transformed to:
 * {{{
 *  __proxy.objectOrStaticCallProxy("ala.package.Ala")
 *  }}}
 */
case class MockObjectsAndStaticCalls(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer {

  import toolbox.u._

  /**
   * Check if given select is stable object.
   * We consider select as stable object if isModule (scala's AST name for object) and it is not JdiContext and package.
   * Also all owners of that object must be of singleton type (objects, packages, Java static classes etc.).
   */
  private def isStableObject(select: Select): Boolean = {
    @tailrec def hasNonSingletonOwner(symbol: Symbol): Boolean = symbol match {
      case NoSymbol => false
      case _ if symbol.isClass && !symbol.isModuleClass => true
      case _ => hasNonSingletonOwner(symbol.owner)
    }

    select.tpe != null &&
      select.symbol.isModule &&
      !select.symbol.isPackage &&
      !hasNonSingletonOwner(select.symbol) &&
      select.toString != Debugger.contextFullName
  }

  /** generate and parse object/static call code */
  private def createProxy(select: Tree): Tree = {
    val className = typesContext.jvmTypeForClass(select.tpe)

    // generates code like __proxy.objectOrStaticCallProxy("ala.package.Ala")
    import Debugger._
    toolbox.parse(s"""$contextParamName.$objectOrStaticCallProxyMethodName("$className")""")
  }

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case select: Select if isStableObject(select) => createProxy(select)
    case other => transformFurther(other)
  }
}
