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
import org.scalaide.debug.internal.expression.ScalaOther
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Proxies all constructors in code.
 * for construction we use JdiContext.newInstance
 * Rewrites new Class(a)(b) to __context.newInstance("package.Class", Seq(Seq(a), Seq(b)))
 *
 */
case class MockNewOperator(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer(typesContext) {

  import toolbox.u._

  private var currentTrees: Seq[Tree] = Nil

  /**
   * Traverse throught function call and returns arguments
   */
  private def extractParameters(tree: Tree): List[List[Tree]] = tree match {
    case select: Select => Nil
    case typeApply @ TypeApply(fun, _) => extractParameters(fun)
    case apply @ Apply(fun, args) => args :: extractParameters(fun)
    case _ => throw new RuntimeException(s"Bad part of call function tree: $tree")
  }

  /**
   * Rewrites new Class(a)(b) to __context.newInstance("package.Class", Seq(Seq(a), Seq(b)))
   */
  private def proxiedNewCode(fun: Tree, args: List[Tree], classType: String): Tree = {
    // parameters lists for constructor
    val params = (args +: extractParameters(fun)).reverse

    // responsible for ""package.Class"" part of expression
    val classTypeCode = toolbox.parse('"' + classType + '"')

    // responsible for "Seq" parts of expression
    val Apply(seqApplyFunction, _) = toolbox.parse("Seq()")

    // responsible for "__context.newInstance" part of expression
    val newInstanceFunc = toolbox.parse(s"${DebuggerSpecific.contextParamName}.newInstance")

    // responsible for "Seq(a), Seq(a)" part of expression
    val argumentSeqArgumentSeqs: List[Tree] = params.map {
      list => Apply(seqApplyFunction, list)
    }

    // responsible for "Seq(Seq(a), Seq(a))" part of expression
    val argsCode = Apply(seqApplyFunction, argumentSeqArgumentSeqs)

    // whole expression
    Apply(newInstanceFunc, List(classTypeCode, argsCode))
  }

  /** Transformer */
  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = {
    currentTrees = tree +: currentTrees
    val retTree = tree match {
      case newTree @ Apply(fun, args) if fun.symbol.name.toString == ScalaOther.constructorFunctionName =>
        proxiedNewCode(fun, args, newTree.tpe.toString)
      case any => transformFurther(tree)
    }
    currentTrees = currentTrees.tail
    retTree
  }
}
