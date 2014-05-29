/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import java.lang.reflect.ParameterizedType

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import scala.util.Try

import org.scalaide.debug.internal.expression.DebuggerSpecific
import org.scalaide.debug.internal.expression.ScalaOther
import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.debug.internal.expression.TypesContext
import org.scalaide.debug.internal.expression.context.VariableContext

case class MockVariables(toolbox: ToolBox[universe.type], context: VariableContext, typesContext: TypesContext)
  extends TransformationPhase {

  import toolbox.u.{ Try => _, _ }

  /**
   * Insert mock proxy code for unbound variables into given code tree
   */
  class MockProxyBuilder {

    /**
     * For variables that need to be proxied mock proxy code is generated
     * @param code processed code
     * @param context variables context for retrieving type information
     * @return code with mock definitions prepended
     */
    final def prependMockProxyCode(code: Tree): Tree = {
      val mockProxiesCode = generateProxies(typesContext.unboundVariables, context)
      if (mockProxiesCode.isEmpty) {
        code
      } else {
        Block(mockProxiesCode, code)
      }
    }

    /** break genetated expression into sequence of value definition */
    private def breakValDefBlock(code: Tree): Seq[Tree] = breakBlock(code) {
      case valDef: ValDef => Seq(valDef)
    }

    /**
     * @param names identifiers
     * @param context variable context
     * @return tree representing proxy definitions for each name in names and according to types from context
     */
    private def generateProxies(names: Set[String], context: VariableContext): List[Tree] = {
      val namesWithThis = names + DebuggerSpecific.thisValName
      val proxyDefinitions = namesWithThis.flatMap(buildProxyDefinition(context)).mkString("\n")
      breakValDefBlock(toolbox.parse(proxyDefinitions)).toList
    }

    /**
     * @param context variable context
     * @param name variable name
     * @return String representing proxy variable definition
     */
    private def buildProxyDefinition(context: VariableContext)(name: String): Option[String] = {
      import DebuggerSpecific._

      def isArray(typeName: String) = typeName endsWith "[]"

      def typeOfArray(typeName: String) = typeName dropRight 2

      context.getType(name).map { typeName =>
        val typeParams: String = typeName match {
          case ScalaOther.Array(_) => ""
          case other => buildTypeParameterSig(other)
        }

        s"""val $name: $typeName$typeParams = $contextName.$placeholderName""" + (
          if (name == thisValName && typeName != proxyName) s"\nimport $name._" else "")
      }
    }

    /**
     * @param className class name whose generic type parameters are processed
     * @return String representing type parameters signature for given class name
     */
    private def buildTypeParameterSig(className: String): String = {
      val typeParameters = Try {
        val clazz = Class.forName(className)
        clazz.getGenericSuperclass match {
          case parameterizedType: ParameterizedType =>
            parameterizedType.getActualTypeArguments.toList
          case _ => Nil
        }
      }.getOrElse(Nil)

      if (typeParameters.isEmpty) {
        ""
      } else {
        typeParameters.map(_ => DebuggerSpecific.proxyName).mkString("[", ", ", "]")
      }
    }
  }

  override def transform(tree: universe.Tree): universe.Tree =
    new MockProxyBuilder().prependMockProxyCode(tree)
}