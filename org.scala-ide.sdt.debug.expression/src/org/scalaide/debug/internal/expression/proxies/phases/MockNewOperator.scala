/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstMatchers
import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TypesContext
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy

/**
 * Proxies all constructors in code.
 * for construction we use JdiContext.newInstance
 * Rewrites new Class(a)(b) to __context.newInstance("package.Class", Seq(Seq(a), Seq(b)))
 *
 */
case class MockNewOperator(toolbox: ToolBox[universe.type])
  extends AstTransformer {

  import toolbox.u._

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

    // responsible for ""package.Class"" part of expression
    val classTypeCode: Tree = Literal(Constant(classType))

    // responsible for "__context.newInstance" part of expression
    val methodCall = {
      // creating nested type applied tree is too cumbersome to do by hand
      import Debugger._
      Select(Ident(newTermName(contextParamName)), newTermName(newInstance))
    }

    // responsible for "Seq(Seq(a), Seq(a))" part of expression
    val argsCode = Apply(SelectApplyMethod("Seq"), params)

    Apply(methodCall, List(classTypeCode, argsCode))
  }

  private def isConstructor(symbol: Symbol) = symbol.name.toString == Scala.constructorMethodName

  /** Transformer */
  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case newTree @ Apply(fun, args) if isConstructor(fun.symbol) =>
      val classType = newTree.tpe match {
        case AstMatchers.ArrayRef(typeParam) => Scala.Array(typeParam.toString)
        case other => other.typeSymbol.fullName
      }
      proxiedNewCode(fun, args, classType)
    case any => transformFurther(any)
  }
}
