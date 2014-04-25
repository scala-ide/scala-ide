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
   * Traverse throught method call and returns arguments
   */
  private def extractParameters(tree: Tree): List[List[Tree]] = tree match {
    case select: Select => Nil
    case TypeApply(fun, _) => extractParameters(fun)
    case Apply(fun, args) => args :: extractParameters(fun)
    // TODO - better exception (and message)
    case _ => throw new RuntimeException(s"Bad part of call function tree: $tree")
  }

  /**
   * Rewrites new Class(a)(b) to __context.newInstance("package.Class", Seq(Seq(a), Seq(b)))
   */
  private def proxiedNewCode(fun: Tree, args: List[Tree], classType: String): Tree = {
    // parameters lists for constructor
    val params = (args +: extractParameters(fun)).reverse

    // responsible for ""package.Class"" part of expression
    val classTypeCode: Tree = Literal(Constant(classType))

    // responsible for "__context.newInstance" part of expression
    val methodCall = {
      val tpe = classType match {
        case Scala.Array(typeParam) => Debugger.ArrayJdiProxy(primitiveToProxy(typeParam))
        case _ if Java.boxed.all contains classType => primitiveToProxy(classType)
        case _ => Debugger.proxyName
      }
      // creating nested type applied tree is too cumbersome to do by hand
      import Debugger._
      toolbox.parse(contextParamName + "." + newInstance + "[" + tpe + "]")
    }

    // responsible for "Seq(a), Seq(a)" part of expression
    val argumentSeqArgumentSeqs: List[Tree] = params.map {
      list => Apply(SelectApplyMethod("Seq"), list)
    }

    // responsible for "Seq(Seq(a), Seq(a))" part of expression
    val argsCode = Apply(SelectApplyMethod("Seq"), argumentSeqArgumentSeqs)

    Apply(methodCall, List(classTypeCode, argsCode))
  }

  private def primitiveToProxy(primitiveType: String): String =
    BoxedJdiProxy.primitiveToProxy(primitiveType).getOrElse(Debugger.proxyName)

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
