/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.context.GenericVariableType
import org.scalaide.debug.internal.expression.context.PlainVariableType
import org.scalaide.debug.internal.expression.context.VariableContext
import org.scalaide.debug.internal.expression.sources.GenericTypes
import org.scalaide.debug.internal.expression.sources.GenericTypes.GenericProvider
import org.scalaide.logging.HasLogger

class MockVariables(val toolbox: ToolBox[universe.type],
  context: VariableContext,
  unboundVariables: => Set[universe.TermName])
    extends TransformationPhase
    with HasLogger {

  import toolbox.u.{ Try => _, _ }

  /**
   * Insert mock proxy code for unbound variables into given code tree
   */
  object MockProxyBuilder {

    /**
     * For variables that need to be proxied mock proxy code is generated
     * @param code processed code
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

    /**
     * Breaks generated block into sequence of trees.
     * If last one is `()` it is removed as it is not needed.
     */
    private def breakBlock(code: Tree): Seq[Tree] = code match {
      case valDef: ValDef => Seq(valDef)
      case Block(children, Literal(Constant(()))) => children
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
      val genericProvider = new GenericProvider
      val proxiesAndImports =
        namesWithThis.flatMap(buildProxyDefinition(context, genericProvider)) ++
          context.syntheticImports ++
          names.toSeq.flatMap(buildNestedMethodDefinition(genericProvider))

      val proxiesAndImportsParsed = toolbox.parse(proxiesAndImports.mkString("\n"))
      breakBlock(proxiesAndImportsParsed).toList
    }

    /**
     * @param context variable context
     * @param name variable name
     * @return String representing proxy variable definition
     */
    private def buildProxyDefinition(context: VariableContext, genericProvider: GenericProvider)(name: TermName): Option[String] = {
      import Debugger._

      context.typeOf(name).map {
        case GenericVariableType(typeName, genericSignature) =>
          genericProvider.typeForField(name.toString).getOrElse(generateProxiedGenericName(typeName, genericSignature))
        case PlainVariableType(typeName) =>
          typeName
      }.map(typeSig => s"""val $name: $typeSig = $contextName.$placeholderName""")
    }

    private def buildNestedMethodDefinition(genericsProvider: GenericProvider)(name: TermName): Option[String] =
      genericsProvider.typeForNestedMethod(name.toString).map(generateLocalFunctionSignature)

    private def generateLocalFunctionSignature(entry: GenericTypes.GenericEntry): String = {
      import entry._
      val GenericTypes.LocalMethod(parametersListCount, start, end) = entry.entryType
      import Debugger._
      s"""val $name: $genericType = $contextName.$placeholderNestedMethodName($parametersListCount, $start, $end)"""
    }

    private def generateProxiedGenericName(className: String, genericSignature: String): String = {
      // genSignature is like '<A:Ljava/lang/Object;C:Ljava/lang/Object;>Ljava/lang/Object;Ldebug/GenericTrait<TC;>;'

      // cuts it to this: A:Ljava/lang/Object;C:Ljava/lang/Object;
      val listTypes = genericSignature.split('>').head.drop(1)

      // splits to this: Seq("A:Ljava/lang/Objec", "C:Ljava/lang/Object")
      listTypes.split(";")
        .map(_ => Debugger.proxyName).mkString(s"$className[", ", ", "]")
    }
  }

  override def transform(tree: universe.Tree): universe.Tree =
    MockProxyBuilder.prependMockProxyCode(tree)
}