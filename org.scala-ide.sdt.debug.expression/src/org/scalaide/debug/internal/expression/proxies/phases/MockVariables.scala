/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.debug.internal.expression.context.GenericVariableType
import org.scalaide.debug.internal.expression.context.PlainVariableType
import org.scalaide.debug.internal.expression.context.VariableContext
import org.scalaide.debug.internal.expression.sources.GenericTypes
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

    /** breaks generated expression into sequence of definitions of values */
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
      import Names.Debugger._

      //Try obtain generic arguments twice on failure (SPC is fragile)
      lazy val fromSource = GenericTypes.genericTypeForValues()
        .orElse(GenericTypes.genericTypeForValues())

      context.typeOf(name).map {
        case GenericVariableType(typeName, genericSignature) =>
          val typeFromSource = fromSource.flatMap(_.get(name.toString()))
          typeFromSource.getOrElse(generateProxiedGenericName(typeName, genericSignature))
        case PlainVariableType(typeName) =>
          typeName
      }.map(typeSig => s"""val $name: $typeSig = $contextName.$placeholderName""")
    }

    private def generateProxiedGenericName(className: String, genericSignature: String): String = {
      //genSignature is like '<A:Ljava/lang/Object;C:Ljava/lang/Object;>Ljava/lang/Object;Ldebug/GenericTrait<TC;>;'

      //cuts it to this: A:Ljava/lang/Object;C:Ljava/lang/Object;
      val listTypes = genericSignature.split('>').head.drop(1)

      //splits to this: Seq("A:Ljava/lang/Objec", "C:Ljava/lang/Object")
      listTypes.split(";")
        .map(_ => Names.Debugger.proxyName).mkString(s"$className[", ", ", "]")
    }
  }

  override def transform(tree: universe.Tree): universe.Tree =
    new MockProxyBuilder().prependMockProxyCode(tree)
}