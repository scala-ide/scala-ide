/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.JdiProxyFunctionParameter
import org.scalaide.debug.internal.expression.ClassListener
import org.scalaide.debug.internal.expression.DebuggerSpecific
import org.scalaide.debug.internal.expression.TypesContext

import org.scalaide.debug.internal.expression.ClassListener.NewClassContext
import scala.util.Try

/**
 * Author: Krzysztof Romanowski
 */
trait AnonymousFunctionSupport {

  //requirements
  protected val typesContext: TypesContext
  protected val toolbox: ToolBox[universe.type]

  // for function naming
  private var functionsCount = 0

  // for new function name
  private val newClassName = "CustomFunction"

  import toolbox.u

  // we should exlude start function -> it must stay function cos it is not a part of original expression
  protected def isStartFunctionForExpression(params: List[u.ValDef]) = params match {
    case List(u.ValDef(_, name, typeTree, _)) if name.toString == DebuggerSpecific.contextParamName => true
    case _ => false
  }

  protected def compilePartialFunction(partialFunction: u.Tree): NewClassContext = {
    ClassListener.listenForClasses(newClassName)(() => toolbox.compile(partialFunction))
  }

  protected def compileFunction(params: List[u.ValDef], body: u.Tree): NewClassContext = {
    val argumentsTypes = params.map(_.tpt).flatMap(typesContext.treeTypeName)

    argumentsTypes.collectFirst {
      case DebuggerSpecific.proxyFullName => throw new JdiProxyFunctionParameter
    }

    val functionGenericTypes = (argumentsTypes ++ Seq("Any")).mkString(", ")
    val newClass = toolbox.parse(s"class $newClassName extends Function${params.size}[$functionGenericTypes]{ override def apply(v1: Any) = ???}")

    val u.ClassDef(mods, name, tparams, u.Template(parents, self, List(constructor, oldApplyFunction))) = newClass

    val u.DefDef(functionMods, functionName, _, _, retType, _) = oldApplyFunction
    val newApplyFunction = u.DefDef(functionMods, functionName, Nil, List(params), retType, body)
    val newFunctionClass = u.ClassDef(mods, name, tparams, u.Template(parents, self, List(constructor, newApplyFunction)))
    val functionReseted = toolbox.resetAllAttrs(newFunctionClass)

    ClassListener.listenForClasses(newClassName)(() => toolbox.compile(functionReseted))
  }

  // creates and compiles new function class
  protected def createAndCompileNewFunction(params: List[u.ValDef], body: u.Tree, parentType: String): u.Tree = {
    val NewClassContext(jvmClassName, classCode) = compileFunction(params, body)

    val proxyClassName = s"${parentType}v$functionsCount"
    functionsCount += 1

    val newFunctionType = typesContext.newType(proxyClassName, jvmClassName, parentType, classCode)
    lambdaProxy(newFunctionType)
  }

  protected def lambdaProxy(proxyClass: String): u.Tree =
    toolbox.parse(s"$proxyClass(${DebuggerSpecific.contextParamName})")

  protected def extractByNameParams(select: u.Tree): Option[Seq[Boolean]] = Try {
    def innerArgs(tpe: u.Type): Seq[Boolean] =
      tpe match {
        case poly @ u.PolyType(_, realType) =>
          innerArgs(realType)
        case method @ u.MethodType(params, resultType) =>
          params.map(byName)
        case _ => Nil
      }

    def byName(symbol: u.Symbol): Boolean =
      symbol.typeSignature.toString.startsWith("=>")

    innerArgs(select.tpe)
  }.toOption
}
