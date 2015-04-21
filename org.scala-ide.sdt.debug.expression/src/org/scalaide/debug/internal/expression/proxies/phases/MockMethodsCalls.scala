/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.proxies.JdiProxyCompanion

/**
 * Extracts and transforms all implicit methods calls to valid proxies.
 *
 * Transforms:
 * {{{
 *   scala.this.Predef.ArrowAssoc[Int](__context.proxy(1)).->[Int](__context.proxy(2))
 * }}}
 * to:
 * {{{
 *   scala.this.Predef.ArrowAssoc[Int](__context.proxy(1)).applyWithGenericType(
 *     "$minus$greater",
 *     scala.Some.apply("scala.Predef.ArrowAssoc"),
 *     __context.proxy(2)
 *   )
 * }}}
 */
class MockMethodsCalls
    extends AstTransformer[AfterTypecheck] {

  import universe._

  private def literal(n: String) = Literal(Constant(n))

  private def isContext(f: Tree) = f.toString() == Debugger.contextParamName

  private def isSpecialMethod(name: Name) = Debugger.proxySpecialMethods.contains(name.toString())

  /** Creates a proxy to mock methods calls. */
  private def createProxy(qualifier: Tree, name: Name, args: List[Tree], transformFurther: Tree => Tree): Tree = {
    val fullType = TypeNames.fromTree(qualifier, withoutGenerics = true).map(name => q"Some($name)").getOrElse(q"None")
    val transformedQualifier = transformFurther(qualifier)
    val transformedArgs = args.mapConserve(transformFurther)
    val applyWithGenericType = Select(transformedQualifier, TermName(Debugger.proxyGenericApplyMethodName))
    val applyWithGenericTypeArgs = literal(name.toString()) :: fullType :: transformedArgs

    Apply(applyWithGenericType, applyWithGenericTypeArgs)
  }

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case Apply(Select(qualifier: Apply, name), args) if !isSpecialMethod(name) && !isContext(qualifier) =>
      createProxy(qualifier, name, args, transformFurther)
    case Apply(TypeApply(Select(qualifier: Apply, name), _), args) if !isSpecialMethod(name) && !isContext(qualifier) =>
      createProxy(qualifier, name, args, transformFurther)
    case other =>
      transformFurther(other)
  }
}
