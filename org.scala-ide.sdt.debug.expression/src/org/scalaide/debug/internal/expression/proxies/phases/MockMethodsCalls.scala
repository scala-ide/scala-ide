/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.tools.reflect.ToolBox
import scala.reflect.runtime.universe
import org.scalaide.debug.internal.expression.AstTransformer
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.TypesContext
import org.scalaide.debug.internal.expression.proxies.JdiProxyCompanion
import org.scalaide.debug.internal.expression.Names

/**
 * Extracts and transforms all implicit methods calls to valid proxies
 * @param toolbox
 */
case class MockMethodsCalls(toolbox: ToolBox[universe.type])
  extends AstTransformer {

  import toolbox.u._
  import Names.Debugger._

  private def literal(n: String) = Literal(Constant(n))

  private def isContext(f: Tree) = f.toString() == contextParamName

  private def isSpecialMethod(name: Name) = proxySpecialMethods.contains(name.toString())

  /** Creates a proxy to mock methods calls.
   *
   *  Transforms trees like:
   *  scala.this.Predef.ArrowAssoc[Int](__context.proxy(1)).->[Int](__context.proxy(2))
   *  to:
   *  scala.this.Predef.ArrowAssoc[Int](__context.proxy(1)).applyWithGenericType(
   *    "$minus$greater", scala.Some.apply("scala.Predef.ArrowAssoc"), __context.proxy(2))
   */
  private def createProxy(qualifier: Tree, name: Name, args: List[Tree], transformFurther: Tree => Tree): Tree = {
    val fullType = Option(qualifier.tpe).map(tpe => q"Some(${tpe.typeSymbol.fullName})").getOrElse(q"None")
    val transformedQualifier = transformFurther(qualifier)
    val transformedArgs = args.mapConserve(transformFurther)
    val applyWithGenericType = Select(transformedQualifier, TermName(proxyGenericApplyMethodName))
    val applyWithGenericTypeArgs = literal(name.toString()) :: fullType :: transformedArgs

    Apply(applyWithGenericType, applyWithGenericTypeArgs)
  }

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Apply(Select(qualifier: Apply, name), args)
      if !isSpecialMethod(name) && !isContext(qualifier) => createProxy(qualifier, name, args, transformFurther)
    case Apply(TypeApply(Select(qualifier: Apply, name), _), args)
      if !isSpecialMethod(name) && !isContext(qualifier) => createProxy(qualifier, name, args, transformFurther)
    case other =>
      transformFurther(other)
  }
}
