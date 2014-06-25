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
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.ScalaOther
import org.scalaide.debug.internal.expression.TypesContext
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy

/**
 * Proxies all constructors in code.
 * for construction we use JdiContext.newInstance
 * Rewrites new Class(a)(b) to __context.newInstance("package.Class", Seq(Seq(a), Seq(b)))
 *
 */
case class MockNewOperator(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer {

  import toolbox.u._

  /**
   * Traverse throught function call and returns arguments
   */
  private def extractParameters(tree: Tree): List[List[Tree]] = tree match {
    case select: Select => Nil
    case TypeApply(fun, _) => extractParameters(fun)
    case Apply(fun, args) => args :: extractParameters(fun)
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
    val newInstance = SelectMethod(DebuggerSpecific.contextParamName, "newInstance")

    // responsible for "Seq(a), Seq(a)" part of expression
    val argumentSeqArgumentSeqs: List[Tree] = params.map {
      list => Apply(SelectApplyMethod("Seq"), list)
    }

    // responsible for "Seq(Seq(a), Seq(a))" part of expression
    val argsCode = Apply(SelectApplyMethod("Seq"), argumentSeqArgumentSeqs)

    val result = Apply(newInstance, List(classTypeCode, argsCode))
    // whole expression
    // TODO - O-5640, O-5700 - newInstance should be parameterized and return concrete proxy types
    if (JavaBoxed.all contains classType) wrapInPrimitiveProxy(result, classType)
    else result
  }

  /**
   * Wraps result with primitive proxy if needed.
   */
  // TODO - O-5640, O-5700 - this method should be used in some other places
  private def wrapInPrimitiveProxy(tree: Tree, primitiveType: String): Tree =
    Apply(
      SelectApplyMethod(BoxedJdiProxy.primitiveToProxyMap(primitiveType)),
      List(tree))

  private def isConstructor(symbol: Symbol) = symbol.name.toString == ScalaOther.constructorFunctionName

  /** Transformer */
  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case newTree @ Apply(fun, args) if isConstructor(fun.symbol) =>
      proxiedNewCode(fun, args, newTree.tpe.typeSymbol.fullName)
    case any => transformFurther(tree)
  }
}
