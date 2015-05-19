/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import java.lang.RuntimeException

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.context._
import org.scalaide.debug.internal.expression.sources.GenericTypes
import org.scalaide.debug.internal.expression.sources.GenericTypes.GenericEntry
import org.scalaide.debug.internal.expression.sources.GenericTypes.GenericProvider
import org.scalaide.logging.HasLogger

/**
 * This phase generates mocks for all unbound values (and variables)
 * and import from multiple kinds of `this` (local instance, parent classes etc).
 *
 * Transforms:
 * {{{
 *   list.map(i => i + int)
 * }}}
 *
 * to:
 * {{{
 *   val list: List[Int] = JdiContext.placeholder
 *   val int: scala.Int = JdiContext.placeholder
 *   val __this: test.Values.type = JdiContext.placeholder
 *   import __this._
 *
 *   list.map(i => i + int)
 * }}}
 *
 * Handles also case when we compiling code without classpath.
 * Than this code:
 * {{{
 *    method(value)
 * }}}
 * Becomes:
 * {{{
 *  val _this: JdiProxy = JdiContext.placeholder
 *  import __this._
 *  val value = _this.value
 *  def method(v1: Any) = _this.method(v1)
 *
 *  method(value)
 * }}}
 *
 * @param context gives access to imports, local variables etc. from current scope
 */
class MockUnboundValuesAndAddImportsFromThis(val toolbox: ToolBox[universe.type],
                                             context: VariableContext)
  extends TransformationPhase[BeforeTypecheck]
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
    final def prependMockProxyCode(code: Tree, unboundVariables: Set[UnboundVariable]): Tree = {
      val mockProxiesCode = generateProxies(unboundVariables, context)
      if (mockProxiesCode.isEmpty)
        code
      else
        Block(mockProxiesCode, code)
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
    private def generateProxies(names: Set[UnboundVariable], context: VariableContext): List[Tree] = {
      val genericProvider = new GenericProvider

      val implicits = genericProvider.implicits
      val implicitsNames: Set[String] = implicits.map(_.name)(collection.breakOut)

      val namesWithThis: Seq[Variable] = (names.toSeq ++ context.syntheticVariables).distinct
        .filterNot(v => implicitsNames.contains(v.name.toString)) //order matter in case of this values

      val (memberVariables, simpleVariables) = namesWithThis.flatMap(context.typed)
        .partition(_.isInstanceOf[DynamicVariable])

      val proxyDefinitions =
        simpleVariables.map(buildProxyDefinition(context, genericProvider)) ++
          context.syntheticImports ++
          names.toSeq.flatMap(buildNestedMethodDefinition(genericProvider)) ++
          implicits.map(buildImplicitEntryDefinition) ++
          memberVariables.map(buildDynamicMemberDefinition)

      breakBlock(toolbox.parse(proxyDefinitions.mkString("\n"))).toList
    }

    private def valueDefinitionCode(variable: Variable)(fieldType: String): String = variable match {
      case UnboundVariable(name, true) => valueDefinitionCode(name.toString, true, fieldType)
      case variable => valueDefinitionCode(variable.name.toString, false, fieldType)
    }

    private def valueDefinitionCode(name: String, isVar: Boolean, fieldType: String): String = {
      import Debugger._
      val fieldKind = if (isVar) "var" else "val"
      s"$fieldKind $name: $fieldType = $contextName.$placeholderName"
    }

    /**
     * @param context variable context
     * @param name variable name
     * @return String representing proxy variable definition
     */
    private def buildProxyDefinition(context: VariableContext, genericProvider: GenericProvider)(variable: TypedVariable): String = {
      val typeName = variable match {
        case GenericVariable(_, typeName, genericSignature) =>
          genericProvider.typeForField(variable.name.toString).getOrElse(generateProxiedGenericName(typeName, genericSignature))
        case PlainTypedVariable(_, typeName) =>
          typeName
        case dynamic => throw new RuntimeException("Cannot build static proxy for dynamic variable: " + dynamic)
      }

      valueDefinitionCode(variable.untyped)(typeName)
    }

    /**
     * Build dynamic member variable implementation.
     * For values "field" is implemented as "val field = from.field"
     * For methods "method(12)" is implemented as "def method(v1: Any) = from.method(v1)"
     *
     * "from" in this case is on of "_thisX" values that are responsible from methods/values from this scope.
     * Both cases do not need further implementation.
     */
    private def buildDynamicMemberDefinition(variable: TypedVariable): String = variable match {
      case DynamicMemberBasedVariable(v, memberName) => s"val ${v.name} = $memberName.${v.name}"
      case DynamicMemberBasedMethod(v, memberName, arity) =>
        val argsNames = (1 to arity).map("v" +)
        val typedNames = argsNames.map(name => s"$name: Any").mkString(", ")
        s"def ${v.name}($typedNames) = $memberName.${v.name}(${argsNames.mkString(", ")})"
     case static => throw new RuntimeException("Cannot build dynamic proxy for static variable: " + static)
    }

    private def buildImplicitEntryDefinition(entry: GenericEntry): String = {
      val nonImplicitCode = entry.entryType match {
        case GenericTypes.Field => valueDefinitionCode(entry.name, false, entry.genericType)
        case _ => generateLocalFunctionSignature(entry)
      }
      s"implicit $nonImplicitCode"
    }

    private def buildNestedMethodDefinition(genericsProvider: GenericProvider)(variable: Variable): Option[String] =
      genericsProvider.typeForNestedMethod(variable.name.toString).map(generateLocalFunctionSignature)

    private def generateLocalFunctionSignature(entry: GenericTypes.GenericEntry): String = {
      import entry._
      val GenericTypes.LocalMethod(parametersListCount, start, end) = entry.entryType
      import Debugger._
      s"""val $name: $genericType = $contextName.$placeholderNestedMethodName($parametersListCount, $start, $end)"""
    }

    /** If there is no information about generic type from source code fill generic type with JdiProxy */
    private def generateProxiedGenericName(className: String, genericSignature: String): String = {
      // genSignature is like '<A:Ljava/lang/Object;C:Ljava/lang/Object;>Ljava/lang/Object;Ldebug/GenericTrait<TC;>;'

      // cuts it to this: A:Ljava/lang/Object;C:Ljava/lang/Object;
      val listTypes = genericSignature.split('>').head.drop(1)

      // splits to this: Seq("A:Ljava/lang/Objec", "C:Ljava/lang/Object")
      listTypes.split(";")
        .map(_ => Debugger.proxyName).mkString(s"$className[", ", ", "]")
    }
  }

  override def transform(data: TransformationPhaseData): TransformationPhaseData = {
    val newTree = MockProxyBuilder.prependMockProxyCode(data.tree, data.unboundVariables)
    data.after(phaseName, newTree)
  }
}
