/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.annotation.tailrec
import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Scala

/**
 * Extracts and transforms all object-like code to valid proxies
 * (it can be also Java static call but we can't distinguish here - dynamic dispatch is needed later).
 *
 * E.g. call to object:
 * {{{
 *   Ala
 * }}}
 * is transformed to:
 * {{{
 *   __proxy.objectOrStaticCallProxy("Ala")
 * }}}
 */
class MockObjectsAndStaticCalls
  extends AstTransformer[AfterTypecheck] {

  import universe._

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

  /** Creates java names for classes (replaces `.` with `$` for nested classes). */
  private def jvmTypeForClass(tpe: universe.Type): String = {
    import universe.TypeRefTag
    // hack for typecheck replacing `List.apply()` with `immutable.this.Nil`
    if (tpe.toString == "List[Nothing]") Scala.nil
    else tpe.typeConstructor match {
      case universe.TypeRef(prefix, sym, _) if !prefix.typeConstructor.typeSymbol.isPackage =>
        val className = sym.name
        val parentName = jvmTypeForClass(prefix)
        parentName + "$" + className
      case _ =>
        tpe.typeSymbol.fullName
    }
  }

  /** generate and parse object/static call code */
  private def createProxy(select: Tree): Tree = {
    val className = jvmTypeForClass(select.tpe)

    // generates code like __proxy.objectOrStaticCallProxy("ala.package.Ala")
    import Debugger._
    Apply(
        SelectMethod(contextParamName, objectOrStaticCallProxyMethodName),
        List(Literal(Constant(className))))
  }

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case select: Select if isStableObject(select) => createProxy(select)
    case other => transformFurther(other)
  }
}
