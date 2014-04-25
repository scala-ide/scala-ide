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
import org.scalaide.debug.internal.expression.FunctionStub
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Searches for used types and adds them to TypesContext.
 */
case class TypeSearch(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer(typesContext)
  with AnonymousFunctionSupport {

  import toolbox.u

  private object TypesTraverser extends u.Traverser {

    /** Extract name from function - as decoded name */
    private def extractFunctionName(tree: u.Tree): String = tree.symbol.name.decoded

    /** obtains type name form type -> normal by value */
    private def typeForArgument(arg: u.Tree): String =
      typesContext.treeTypeName(arg).getOrElse(DebuggerSpecific.proxyName)

    /** extract function from select tree */
    private def extractFromSelect(select: u.Select, qualifier: u.Tree, name: u.Name): Option[(String, FunctionStub)] = {
      val functionName = extractFunctionName(select)
      val onType = typesContext.treeTypeName(qualifier)
      val retType = typesContext.treeTypeName(select)

      // obtains name, this type and return type

      onType.map(_ -> FunctionStub(functionName, retType))
    }

    /**
     * Maps function due to implicit arguments application.
     *
     * Changes implicits args and modifies return type.
     */
    private def extractFromImplicitArgs(implApply: u.Tree, args: List[u.Tree])(extractedFunction: (String, FunctionStub)) = {
      val implArgTypes = args
        .map(typesContext.treeTypeName)
        .map(_.getOrElse(DebuggerSpecific.proxyName))

      val (typeName, oldFunction) = extractedFunction

      val newFunctionStub = oldFunction.copy(
        returnType = typesContext.treeTypeName(implApply),
        implicitArgumentTypes = implArgTypes)
      (typeName, newFunctionStub)
    }

    /**
     * Maps function due to parse of another argument list.
     * Changes args lists and modifies return type.
     */
    private def extractFromArgs(apply: u.Tree, func: u.Tree, args: List[u.Tree])(extractedFunction: (String, FunctionStub)) = {

      val argumentTypes = extractByNameParams(func).map {
        byNames =>
          args zip byNames map {
            case (arg, true) => typesContext.byNameType
            case (arg, false) => typeForArgument(arg)
          }
      }.getOrElse(args.map(typeForArgument))

      val (typeName, oldFunction) = extractedFunction

      //Adds another parameter list and modify return type
      val newFunctionStub = oldFunction.copy(
        returnType = typesContext.treeTypeName(apply),
        argumentTypes = oldFunction.argumentTypes :+ argumentTypes)

      (typeName, newFunctionStub)
    }

    /**
     * Maps function due to implicit argument application
     * change implicit args and modifies return type
     */
    private def extractFromTypeAppy(typeApply: u.Tree)(extractedFunction: (String, FunctionStub)) = {

      val (typeName, oldFunction) = extractedFunction

      val newFunctionStub = oldFunction.copy(returnType = typesContext.treeTypeName(typeApply))

      (typeName, newFunctionStub)
    }

    /**
     * Extracts function from given tree.
     * Traversing code and search for:
     * - selects (val, functions, etc)
     * - typeApplies
     * - applies (both normal and implcit args)
     *
     * @param traverseFunction function to call on each leaf of function call (like argument or qualifier part of select)
     * @param tree tree to look for function
     * @return option with function and type on witch function is called
     */
    private def extractFunction(traverseFunction: (u.Tree => Unit), tree: u.Tree): Option[(String, FunctionStub)] = {
      tree match {
        //select part of function
        case select @ u.Select(qualifier, name) if select.symbol.isMethod =>
          traverseFunction(select)
          extractFromSelect(select, qualifier, name)

        //implicit parameter lists
        case implApply @ u.Apply(func, args) if implApply.getClass.getName.contains("ApplyToImplicitArgs") =>
          args.foreach(traverse)
          extractFunction(traverseFunction, func).map(extractFromImplicitArgs(implApply, args))

        //type parameters list
        case apply @ u.TypeApply(func, args) =>
          args.foreach(traverse)
          extractFunction(traverseFunction, func).map(extractFromTypeAppy(apply))

        //argument list
        case apply @ u.Apply(func, args) =>
          args.foreach(traverse)
          extractFunction(traverseFunction, func).map(extractFromArgs(apply, func, args))

        //not a function
        case any =>
          traverseFunction(any)
          None
      }
    }

    /** Extracts type for value definition (eg. function argument) */
    private def extractValueType(tree: u.Tree): Unit = tree match {
      case u.ValDef(_, _, tpt, _) => typesContext.treeTypeName(tpt).foreach(typesContext.typeNameFor)
      case any =>
    }

    /** Traverse, find types and function calls. Each type and function is added to context */
    override final def traverse(tree: u.Tree) {
      extractFunction(super.traverse _, tree).foreach {
        case (typeString, function) => typesContext.newFunction(typeString, function)
      }
      // if we got function arg or value - extract it's type
      extractValueType(tree)
    }
  }

  /** Finds type and return orginal tree */
  protected override def transformSingleTree(baseTree: universe.Tree, transformFurther: (universe.Tree) => universe.Tree): universe.Tree = {
    TypesTraverser.traverse(baseTree)
    baseTree
  }
}
