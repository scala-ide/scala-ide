package org.scalaide.debug.internal.expression.proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.DebuggerSpecific
import org.scalaide.debug.internal.expression.ScalaOther
import org.scalaide.debug.internal.expression.TypesContext

/**
 * Mock all lambdas that has arguments with explicit types
 */
case class MockTypedLambda(toolbox: ToolBox[universe.type], typesContext: TypesContext)
  extends AstTransformer(typesContext) with AnonymousFunctionSupport {

  import toolbox.u._

  // for function naming
  private var functionsCount = 0

  //should we mock this lambda?
  private def allParamsTyped(params: Seq[ValDef]): Boolean = !params.isEmpty && params.forall(!_.tpt.isEmpty)


  /** all val def and binds have type adnotation? */
  private def treeFullyTyped(tree: Tree): Boolean = tree match {
    case bind @ Bind(_, tpt) => !tpt.isEmpty
    case appl @ Apply(_, args) => args.forall(treeFullyTyped)
    case _ => false
  }

  private def caseFullyTyped(caseDef: CaseDef): Boolean = treeFullyTyped(caseDef.pat)

  private def allCasesTyped(cases: Seq[CaseDef]) = cases.forall(caseFullyTyped)

  /** Compile lambda, create new class form lambda and create mock that represent this lambda*/
  private def createStubedPartialFunction(function: Match): Tree = {
    val Function(args, Match(on, _)) = toolbox.parse("(_ : Any) match { case _ => false }")

    val newFunction = Function(args, Match(on, function.cases))

    val typeCheckFunction @ Function(_, newBody) = toolbox.typeCheck(newFunction)
    val ret = typesContext.treeTypeName(newBody).getOrElse(ScalaOther.nothingType)
    val compiled = compileFunction(typeCheckFunction.vparams, typeCheckFunction.body)

    val parentType = ScalaOther.partialFunctionType

    val proxyClassName = s"${parentType}v${functionsCount}_typed"
    functionsCount += 1

    val newFunctionType = typesContext.newType(proxyClassName, compiled.newClassName, parentType, compiled.newClassCode)

    import DebuggerSpecific._
    val code = s"""$contextName.$placeholderPartialFunctionName[$ret]("$newFunctionType")"""
    toolbox.parse(code)
  }


  /** Compile lambda, create new class form lambda and create mock that represent this lambda*/
  private def createStubedFunction(function: Function): Tree = {
    val ret = typesContext.treeTypeName(function.body).getOrElse(ScalaOther.nothingType)
    val compiled = compileFunction(function.vparams, function.body)

    val parentType = typesContext.treeTypeName(function).getOrElse(throw new RuntimeException("Function must have type!"))

    val proxyClassName = s"${parentType}v${functionsCount}_typed"
    functionsCount += 1

    val newFunctionType = typesContext.newType(proxyClassName, compiled.newClassName, parentType, compiled.newClassCode)

    import DebuggerSpecific._
    val code = s"""$contextName.$placeholderFunctionName${function.vparams.size}[$ret]("$newFunctionType")"""
    toolbox.parse(code)
  }

  /**
   * Search and mock all typed lambdas
   * @param baseTree tree to transform
   * @param transformFurther call it on tree node to recursively transform it further
   */
  protected def transformSingleTree(baseTree: Tree, transformFurther: (Tree) => Tree): Tree = baseTree match {
    case fun @ Function(params, body) if !isStartFunctionForExpression(params) && allParamsTyped(params) =>
      val typedFunction = toolbox.typeCheck(fun)
      createStubedFunction(typedFunction.asInstanceOf[Function])
    case fun @ Match(selector, cases) if selector.isEmpty && allCasesTyped(cases) =>
      createStubedPartialFunction(fun)
    case _ => transformFurther(baseTree)
  }
}
