/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import java.lang.reflect.TypeVariable

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.debug.internal.expression.context.VariableContext
import org.scalaide.logging.HasLogger

class MockVariables(val toolbox: ToolBox[universe.type],
  projectClassLoader: ClassLoader,
  context: VariableContext,
  unboundVariables: => Set[universe.TermName])
  extends TransformationPhase
  with HasLogger {

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
      val mockProxiesCode = generateProxies(unboundVariables, context)
      if (mockProxiesCode.isEmpty) {
        code
      } else {
        Block(mockProxiesCode, code)
      }
    }

    /** break genetated expression into sequence of value definition */
    private def breakValDefBlock(code: Tree): Seq[Tree] = code match {
      case valDef: ValDef => Seq(valDef)
      case block: Block => block.children
      case empty @ universe.EmptyTree => Nil
      case any => throw new IllegalArgumentException(s"Unsupported tree: $any")
    }

    /**
     * @param names identifiers
     * @param context variable context
     * @return tree representing proxy definitions for each name in names and according to types from context
     */
    private def generateProxies(names: Set[TermName], context: VariableContext): List[Tree] = {
      val namesWithThis: Seq[TermName] = (names.toSeq ++ context.syntheticVariables).distinct //order matter in case of this values
      val proxyDefinitions = namesWithThis.flatMap(buildProxyDefinition(context)) ++ context.syntheticImports

      breakValDefBlock(toolbox.parse(proxyDefinitions.mkString("\n"))).toList
    }

    /**
     * @param context variable context
     * @param name variable name
     * @return String representing proxy variable definition
     */
    private def buildProxyDefinition(context: VariableContext)(name: TermName): Option[String] = {
      import Debugger._

      context.typeOf(name).map {
        typeName =>
          val typeParams: String = typeName match {
            case Scala.Array(_) => ""
            case other => buildTypeParameterSig(other)
          }

          s"""val $name: $typeName$typeParams = $contextName.$placeholderName"""
      }
    }

    /**
     * @param className class name whose generic type parameters are processed
     * @return String representing type parameters signature for given class name
     */
    private def buildTypeParameterSig(className: String): String = {
      if (className.endsWith(".type")) ""
      else {
        val typeParameters: List[TypeVariable[_]] = try {
          val clazz = projectClassLoader.loadClass(className)
          clazz.getTypeParameters.toList
        } catch {
          case e: Throwable =>
            logger.warn(s"Could not aquire type parameters for class: $className", e)
            Nil
        }

        if (typeParameters.isEmpty) {
          ""
        } else {
          // TODO - O-4565 - extract real types from source
          typeParameters.map(_ => Debugger.proxyName).mkString("[", ", ", "]")
        }
      }
    }
  }

  override def transform(tree: universe.Tree): universe.Tree =
    new MockProxyBuilder().prependMockProxyCode(tree)
}