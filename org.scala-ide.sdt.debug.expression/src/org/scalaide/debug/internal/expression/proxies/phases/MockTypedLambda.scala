/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

/**
 * Mock all lambdas that has arguments with explicit types.
 *
 * Transforms:
 * {{{
 *   list.map(((x$1: Int) => (x$1: Int).$minus(int)))
 * }}}
 * into:
 * {{{
 *   list.map(JdiContext.placeholderFunction1[scala.Int](
 *     <random-name-of-compiled-lambda>, Seq(int)
 *   ))
 * }}}
 */
case class MockTypedLambda(toolbox: ToolBox[universe.type])
    extends AnonymousFunctionSupport[BeforeTypecheck] {

  import toolbox.u._

  //should we mock this lambda?
  private def allParamsTyped(params: Seq[ValDef]): Boolean = !params.isEmpty && params.forall(!_.tpt.isEmpty)

  /** all val def and binds have type annotation? */
  private def treeFullyTyped(tree: Tree): Boolean = tree match {
    case Bind(_, tpt) => !tpt.isEmpty && tpt.toString() != "_"
    case Apply(_, args) => args.forall(treeFullyTyped)
    case _ => false
  }

  private def caseFullyTyped(caseDef: CaseDef): Boolean = treeFullyTyped(caseDef.pat)

  private def allCasesTyped(cases: Seq[CaseDef]) = cases.forall(caseFullyTyped)

  /** Used to obtain type for given values (in scope) */
  private object PlaceholderArgs {
    import Names.Debugger._
    val onString = s"$contextFullName.$placeholderArgsName"

    def unapply(tree: Tree): Option[Map[TermName, String]] = tree match {
      case Apply(on, args) if on.toString() == onString =>
        Some(args.map {
          case ident @ Ident(name: TermName) => name -> TypeNames.getFromTree(ident)
        }(collection.breakOut))
      case _ => None
    }
  }

  private def extractTypesForPlaceholderArgs(mockedTree: Tree): Map[TermName, String] = {
    /** extract types from markers store in PlaceholderArgs */
    var args: Map[TermName, String] = Map.empty
    val extracting = new universe.Traverser {

      override def traverse(tree: universe.Tree): Unit = {
        tree match {
          case PlaceholderArgs(typedArgs) =>
            args = typedArgs
          case _ => super.traverse(tree)
        }
      }
    }
    extracting.traverse(mockedTree)
    args
  }

  /**
   * Cut tree on the requested tree.
   * Then replace last expression with replacement.
   * Used to obtain types for closures arguments.
   */
  def createMockedTree(stopTree: Tree, replaceTree: Tree): Tree =
    new universe.Transformer {
      //do we found requested tree
      private var found = false
      //do we reach first block that contains requested block
      private var firstBlock = false

      override def transform(tree: universe.Tree): universe.Tree = {
        if (found) { //we found what we need - we don't care about the rest
          EmptyTree
        } else
          tree match {
            case _ if tree == stopTree =>
              found = true
              tree match {
                case _: CaseDef => tree
                case _ => replaceTree
              }

            case block: Block =>
              val ret = super.transform(block)
              firstBlock = true
              ret
            case _ =>
              val ret = super.transform(tree)
              if (found && !firstBlock)
                replaceTree //replace last expression with replacement
              else
                ret
          }
      }
    }.transform(data.tree)

  private def closureTypesForTypedLambda(body: Tree, vparams: List[ValDef]): Map[TermName, String] = {
    val names = getClosureParamsNames(body, vparams)

    if (names.isEmpty) Map()
    else {
      // If we have closure args - then replace last expression with them and typecheck it. You just got the types.
      import Names.Debugger._
      val replaceTree = toolbox.parse(names.mkString(s"$contextName.$placeholderArgsName(", ", ", ")"))

      // In partial function is better to search for first case -> match can change during the processing
      val stopTree = body match {
        case Match(_, first :: _) => first
        case _ => body
      }

      val newTree = createMockedTree(stopTree, replaceTree)
      val typechecked = toolbox.typecheck(newTree)
      extractTypesForPlaceholderArgs(typechecked)
    }
  }

  private def prepareLambdaForCompilation(body: Tree,
    vparams: List[ValDef],
    closuresTypes: Map[TermName, String]): Function = {
    val closuresArs = toolbox.parse(closuresTypes.map {
      case (name, typeName) =>
        s"val $name: $typeName = ???"
    }.mkString("\n"))

    val argsTrees = closuresArs match {
      case v: ValDef => Seq(v)
      case block: universe.Block => block.children
      case universe.EmptyTree => Nil
      case any => throw new IllegalArgumentException(s"Unsupported tree: $any")
    }

    val toTypeCheck = Block(argsTrees.toList, Function(vparams, body))
    val typeChecked = toolbox.typecheck(toTypeCheck)

    typeChecked match {
      case Block(_, function: Function) => function
      case Block(Seq(function: Function), _) => function
    }
  }

  /** Common code for stub creation. */
  private def createAnyStubbedFunction(body: Tree,
    vparams: List[ValDef],
    stubName: String): Tree = {

    val closuresTypes: Map[TermName, String] = closureTypesForTypedLambda(body, vparams)

    val typeCheckedFunction: Function = prepareLambdaForCompilation(body, vparams, closuresTypes)

    val retType = TypeNames.getFromTree(typeCheckedFunction.body)

    val compiled = compileFunction(typeCheckedFunction.vparams,
      typeCheckedFunction.body,
      closuresTypes)

    val newFunctionType = compiled.newClassName

    typesContext.createNewType(newFunctionType,
      compiled.newClassCode,
      closuresTypes.values.toSeq)

    import Names.Debugger._
    val additionalArgs = closuresTypes.keySet.mkString("Seq(", ", ", ")")

    val code = s"""$contextName.$stubName[$retType]("$newFunctionType", $additionalArgs)"""

    toolbox.parse(code)
  }

  /** Compile lambda, create new class form lambda and create mock that represents this lambda */
  private def createStubedPartialFunction(function: Match): Tree = {
    val stubVarName = "__x"

    // TODO - O-5330 - recreate body for this function - currently we support only Function1
    val params = List(ValDef(Modifiers(NoFlags | Flag.PARAM), TermName(stubVarName), Ident(TypeName("Any")), EmptyTree))
    val body = Match(Ident(TermName(stubVarName)), function.cases)

    createAnyStubbedFunction(body, params,
      Names.Debugger.placeholderPartialFunctionName)
  }

  /** Compiles lambda, creates new class for it and creates mock that represents this lambda */
  private def createStubedFunction(function: Function): Tree = {
    val argsCount = function.vparams.size
    createAnyStubbedFunction(function.body, function.vparams,
      Names.Debugger.placeholderFunctionName + argsCount)
  }

  /**
   * Search and mock all typed lambdas
   * @param baseTree tree to transform
   * @param transformFurther call it on tree node to recursively transform it further
   */
  protected def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree = baseTree match {
    case fun @ Function(params, _) if !isStartFunctionForExpression(params) && allParamsTyped(params) =>
      createStubedFunction(fun)
    case fun @ Match(selector, cases) if selector.isEmpty && allCasesTyped(cases) =>
      createStubedPartialFunction(fun)
    case _ => transformFurther(baseTree)
  }
}
