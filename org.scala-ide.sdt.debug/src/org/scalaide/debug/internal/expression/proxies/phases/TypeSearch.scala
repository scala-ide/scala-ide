/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.BeforeTypecheck
import org.scalaide.debug.internal.expression.MethodStub
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Searches for used types and adds them to TypesContext.
 */
case class TypeSearch(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer
  with BeforeTypecheck
  with AnonymousFunctionSupport {

  import toolbox.u._

  private object TypesTraverser extends Traverser {

    /** Extract name from function - as decoded name */
    private def extractFunctionName(tree: Tree): String = tree.symbol.name.decodedName.toString

    /** obtains type name form type -> normal by value */
    private def typeForArgument(arg: Tree): String =
      typesContext.treeTypeName(arg).getOrElse(Debugger.proxyName)

    /** extract function from select tree */
    private def extractFromSelect(select: Select, qualifier: Tree, name: Name): Option[(String, MethodStub)] = {
      val functionName = extractFunctionName(select)
      val onType = typesContext.treeTypeName(qualifier)
      val retType = typesContext.treeTypeName(select)
      val thisType = typesContext.jvmTypeForClass(qualifier.tpe)

      // obtains name, this type and return type
      onType.map(_ -> MethodStub(functionName, thisType, retType))
    }

    /**
     * Maps function due to implicit arguments application.
     *
     * Changes implicits args and modifies return type.
     */
    private def extractFromImplicitArgs(implApply: Tree, args: List[Tree])(extractedMethod: (String, MethodStub)) = {
      val implArgTypes = args
        .map(typesContext.treeTypeName)
        .map(_.getOrElse(Debugger.proxyName))

      val (typeName, oldMethod) = extractedMethod

      val newMethodStub = oldMethod.copy(
        returnType = typesContext.treeTypeName(implApply),
        paramTypes = oldMethod.paramTypes :+ implArgTypes
      )
      (typeName, newMethodStub)
    }

    /**
     * Maps function due to parse of another argument list.
     * Changes args lists and modifies return type.
     */
    private def extractFromArgs(apply: Tree, func: Tree, args: List[Tree])(extractedMethod: (String, MethodStub)) = {

      val argumentTypes = extractByNameParams(func).map {
        byNames =>
          args.zip(byNames.toStream ++ Stream.continually(false)) map {
            case (arg, true) => typesContext.byNameType
            case (arg, false) => typeForArgument(arg)
          }
      }.getOrElse(args.map(typeForArgument))

      val (typeName, oldMethod) = extractedMethod

      //Adds another parameter list and modify return type
      val newMethodStub = oldMethod.copy(
        returnType = typesContext.treeTypeName(apply),
        paramTypes = oldMethod.paramTypes :+ argumentTypes)

      (typeName, newMethodStub)
    }

    /**
     * Maps function due to implicit argument application
     * change implicit args and modifies return type
     */
    private def extractFromTypeAppy(typeApply: Tree)(extractedMethod: (String, MethodStub)) = {

      val (typeName, oldMethod) = extractedMethod

      val newMethodStub = oldMethod.copy(returnType = typesContext.treeTypeName(typeApply))

      (typeName, newMethodStub)
    }

    /**
     * Extracts method from given tree.
     *
     * Traverses code and searches for:
     * - selects (val, functions, etc)
     * - typeApplies
     * - applies (both normal and implicit args)
     *
     * @param traverseFunction function to call on each leaf of function call (like argument or qualifier part of select)
     * @param tree tree to look for function
     * @return option with function and type on witch function is called
     */
    private def extractMethod(traverseFunction: (Tree => Unit), tree: Tree): Option[(String, MethodStub)] = {
      tree match {
        //select part of function
        case select @ Select(qualifier, name) if select.symbol.isMethod =>
          traverseFunction(select)
          extractFromSelect(select, qualifier, name)

        //implicit parameter lists
        case implApply @ Apply(func, args) if implApply.getClass.getName.contains("ApplyToImplicitArgs") =>
          args.foreach(traverse)
          extractMethod(traverseFunction, func).map(extractFromImplicitArgs(implApply, args))

        //type parameters list
        case apply @ TypeApply(func, args) =>
          args.foreach(traverse)
          extractMethod(traverseFunction, func).map(extractFromTypeAppy(apply))

        //argument list
        case apply @ Apply(func, args) =>
          args.foreach(traverse)
          extractMethod(traverseFunction, func).map(extractFromArgs(apply, func, args))

        // not a method
        case any =>
          traverseFunction(any)
          None
      }
    }

    /** Extracts type for value definition (e.g. method argument) */
    private def extractValueType(tree: Tree): Unit = tree match {
      case ValDef(_, _, tpt, _) => typesContext.treeTypeName(tpt).foreach(typesContext.stubType)
      case any =>
    }

    /** Traverse, find types and method calls. Each type and method is added to context */
    override final def traverse(tree: Tree) {
      extractMethod(super.traverse _, tree).foreach {
        case (typeString, method) => typesContext.newMethod(typeString, method)
      }
      // if we got function arg or value - extract it's type
      extractValueType(tree)
    }
  }

  /** Finds type and return original tree */
  protected override def transformSingleTree(baseTree: universe.Tree, transformFurther: (universe.Tree) => universe.Tree): universe.Tree = {
    TypesTraverser.traverse(baseTree)
    baseTree
  }
}
