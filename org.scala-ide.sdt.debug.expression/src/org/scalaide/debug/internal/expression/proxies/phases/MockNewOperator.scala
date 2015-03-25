/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy

/**
 * Proxies all constructors in code.
 *
 * Transforms:
 * {{{
 *   new Class(a)(b)
 * }}}
 * to:
 * {{{
 *   __context.newInstance("Class", Seq(a, b))
 * }}}
 */
class MockNewOperator
  extends AstTransformer[AfterTypecheck] {

  import universe._

  /**
   * Traverse through method call and returns arguments
   */
  private def extractParameters(tree: Tree): List[Tree] = tree match {
    case select: Select => Nil
    case TypeApply(fun, _) => extractParameters(fun)
    case Apply(fun, args) => extractParameters(fun) ++ args
    case _ => throw new IllegalStateException(s"Not supported or unrecognized $tree")
  }

  /**
   * Rewrites new Class(a)(b) to __context.newInstance("package.Class", Seq(Seq(a), Seq(b)))
   */
  private def proxiedNewCode(fun: Tree, args: List[Tree], classType: String): Tree = {
    // parameters lists for constructor
    val params = extractParameters(fun) ++ args

    // responsible for `"Class"` part of expression
    val classTypeCode: Tree = Literal(Constant(classType))

    // responsible for `__context.newInstance` part of expression
    val methodCall = {
      // creating nested type applied tree is too cumbersome to do by hand
      import Debugger._
      Select(Ident(TermName(contextParamName)), TermName(newInstance))
    }

    // responsible for `Seq(a, b)` part of expression
    val argsCode = Apply(SelectApplyMethod("Seq"), params)

    Apply(methodCall, List(classTypeCode, argsCode))
  }

  private def isConstructor(symbol: Symbol) = symbol.name.toString == Scala.constructorMethodName

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case newTree @ Apply(fun, args) if isConstructor(fun.symbol) =>
      val classType = TypeNames.getFromTree(newTree, withoutGenerics = true)
      proxiedNewCode(fun, args, classType)
    case any => transformFurther(any)
  }
}
