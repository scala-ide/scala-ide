/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Scala

/**
 * Transformer for converting `isInstanceOf` method invocations on proxies.
 *
 * Example transformation:
 * {{{
 *  value.isInstanceOf[Type]
 * }}}
 * to:
 * {{{
 *  __context.isInstanceOfCheck(value, "Type")
 * }}}
 */
class MockIsInstanceOf
    extends AstTransformer[AfterTypecheck] {

  import universe._

  /** Creates a proxy to replace `isInstanceOf` call. */
  private def createProxy(proxy: Tree, classType: String): Tree =
    Apply(
      SelectMethod(Debugger.contextParamName, Debugger.isInstanceOfMethodName),
      List(proxy, Literal(Constant(classType))))

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case TypeApply(Select(on, TermName("isInstanceOf")), List(tpt @ TypeTree())) =>
      val classType = tpt.tpe match {
        case AstMatchers.ArrayRef(typeParam) => Scala.Array(typeParam.typeSymbol.fullName)
        // it's a hack, I don't know a better way of handling objects
        case other if other.toString.endsWith(".type") => other.typeSymbol.fullName + "$"
        case other => other.typeSymbol.fullName
      }
      createProxy(transformFurther(on), classType)
    case other => transformFurther(other)
  }
}
