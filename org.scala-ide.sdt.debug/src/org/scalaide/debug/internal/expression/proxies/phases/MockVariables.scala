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
import org.scalaide.debug.internal.expression.ExpressionEvaluator
import org.scalaide.debug.internal.expression.TransformationPhase
import org.scalaide.debug.internal.expression.context.VariableContext

case class MockVariables(toolbox: ToolBox[universe.type], context: VariableContext)
  extends TransformationPhase {

  import toolbox.u.{Try => _, _}

  /**
   * Collects unbound names in the tree.
   */
  class VariableProxyTraverser(tree: Tree) extends Traverser {

    private val scopeManager = new ScopeManager(tree)

    private val nameManager = new NameManager()

    /**
     * Collects unbound names in the tree.
     */
    final def findUnboundNames(): Set[String] = {
      this.traverse(tree)
      nameManager.unboundNames()
    }

    /**
     * Collects unbound names in tree.
     * Keeps track of all name bindings in order to collect unbound names.
     */
    final override def traverse(tree: Tree): Unit = {
      scopeManager.pushTree(tree)
      tree match {
        // all identifiers
        case Assign(Ident(termName), value) =>
          // supressing value extraction from lhs
          super.traverse(value)

        case Ident(name) =>
          nameManager.registerUnboundName(name, tree)

        // like: case ala: Ala =>
        case CaseDef(Bind(name, _), _, _) =>
          nameManager.registerNameBinding(name, tree)
          super.traverse(tree)

        // named args like: foo(ala = "ola")
        case AssignOrNamedArg(Ident(name), _) =>
          nameManager.registerNameBinding(name, scopeManager.findCurrentScopeTree())
          super.traverse(tree)

        // for assignments like: var ala; ala = "ola"
        case Bind(name, _) =>
          nameManager.registerNameBinding(name, scopeManager.findCurrentScopeTree())
          super.traverse(tree)

        // value definition like: val ala = "Ala"
        case restTree @ ValDef(_, name, _, _) =>
          nameManager.registerNameBinding(name, scopeManager.findCurrentScopeTree())
          super.traverse(restTree)

        case _ => super.traverse(tree)
      }
      scopeManager.popTree()
    }
  }

  /**
   * Keeps track of all enclosing trees
   * @param topLevelTree top level tree
   */
  class ScopeManager(topLevelTree: Tree) {

    private var treeStack: List[Tree] = Nil

    final def popTree(): Unit = treeStack = treeStack.tail

    final def pushTree(tree: Tree): Unit = treeStack = tree :: treeStack

    /**
     * @return tree defining enclosing scope e.g.: function, block, case def
     *         when no such tree is found the top level tree is returned
     */
    final def findCurrentScopeTree(): Tree =
      treeStack.find {
        case _ @ Function(_, _) | _ @ Block(_, _) | _ @ CaseDef(_, _, _) => true
        case _ => false
      }.getOrElse(topLevelTree)
  }

  /**
   * Keeps track of bound and unbound names
   */
  class NameManager {

    /**
     * Unbound names in a sense that a name is unbound if there exists at least one scope in which it is unbound.
     * NOTE: In some other scopes in a processed code snippet the unbound name might be actually bound
     */
    private var _unboundNames = Set.empty[String]

    /**
     * A map of names and corresponding trees which bind the name
     */
    private var boundNames = Map.empty[Name, Seq[Tree]].withDefaultValue(Seq.empty)

    /**
     * @return collected unbound names
     */
    final def unboundNames(): Set[String] = _unboundNames

    /**
     * @param name identifier
     * @param boundingTree tree bounding the name
     */
    final def registerNameBinding(name: Name, boundingTree: Tree): Unit = {
      boundNames += name -> (boundNames(name) :+ boundingTree)
    }

    /**
     * Checks whether name is unbound and if so registers it as such
     * @param name an identifier
     * @param tree tree representing the name usage
     */
    final def registerUnboundName(name: Name, tree: Tree): Unit = {
      val isBound = boundingTreesOf(name).exists(parentOf(tree))
      val isScalaSymbol = name == nme.WILDCARD || name.toString == "scala"
      val isPredefSymbol = isPredef(name.toString)

      if (!isScalaSymbol && !isPredefSymbol && !isBound && name.isTermName) {
        _unboundNames += name.toString
      }
    }

    /** Checks if type is defined in `scala.Predef` */
    private def isPredef(typeName: String): Boolean = predefSymbols(typeName)

    /** Types in scala Predef */
    private lazy val predefSymbols: Set[String] = {
      val clazz = Predef.getClass
      val methods = clazz.getMethods.map(_.getName)
      val fields = clazz.getFields.map(_.getName)
      val custom = Seq("List", "Nil") // not found in Predef - WTF?
      (methods ++ fields ++ custom).toSet
    }

    /**
     * @param child child searching for a parent
     * @param node node checked for parenthood
     * @return true if the node contains the child, i.e. the node is a parent of the child, false otherwise
     */
    private def parentOf(child: Tree)(node: Tree): Boolean = node.exists(_ == child)

    /**
     * @param name identifier
     * @return all trees which define the name
     */
    private def boundingTreesOf(name: Name): Seq[Tree] = boundNames(name)

  }

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
      val mockProxiesCode = generateProxies(findUnboundVariableNames(code), context)
      if (mockProxiesCode.isEmpty) {
        code
      } else {
        Block(mockProxiesCode, code)
      }
    }

    /**
     * Collects unbound variable names in the tree.
     */
    private def findUnboundVariableNames(code: Tree): Set[String] = {
      new VariableProxyTraverser(code).findUnboundNames()
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
      context.getType(name).map {
        typeName =>
          val typeParams: String = buildTypeParameterSig(typeName)
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