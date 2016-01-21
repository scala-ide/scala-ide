/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names.Debugger

import TypeNames._

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

  private def isSpecialMethod(name: Name) = Debugger.proxySpecialMethods.contains(name.toString())

  private def isValidQualifier(qualifier: Tree) = qualifier match {
    case New(_) => false
    case GenericSelect(_, name) if name.toString() == Debugger.primitiveValueOfProxyMethodName => false
    case q => !isPrimitive(q) && q.toString() != Debugger.contextParamName
  }

  private def getType(qualifier: Tree, tpe: Option[String]): Option[String] =  tpe match {
    case Some(MockSuper.SuperTypeMarker) =>
      val baseClassesWithoutCurrent = qualifier.tpe.baseClasses.drop(1)
      val superClass = baseClassesWithoutCurrent.headOption.map(_.name.toString())
      superClass
    case Some(thisType) =>
      Some(thisType)
    case None =>
      fromTree(qualifier, withoutGenerics = true).map(fixScalaObjectType)
  }

  /** Creates a proxy to mock methods calls. */
  private def createProxy(qualifier: Tree, name: Name, args: List[Tree], tpe: Option[String], transformFurther: Tree => Tree): Tree = {
    val thisType = getType(qualifier, tpe).map(name => q"Some($name)").getOrElse(q"None")
    val transformedQualifier = transformSingleTree(qualifier, transformFurther)
    val transformedArgs = args.mapConserve(transformSingleTree(_, transformFurther))
    val applyWithGenericType = Select(transformedQualifier, TermName(Debugger.proxyGenericApplyMethodName))
    val applyWithGenericTypeArgs = Literal(Constant(name.toString)) :: thisType :: transformedArgs

    Apply(applyWithGenericType, applyWithGenericTypeArgs)
  }

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case MockSuper(GenericSelectOrApply(qualifier, name, args), tpe) =>
      createProxy(qualifier, name, args, Some(tpe), transformFurther)
    case GenericApply(qualifier, name, args) if !isSpecialMethod(name) && isValidQualifier(qualifier) =>
      createProxy(qualifier, name, args, None, transformFurther)
    case other =>
      transformFurther(other)
  }
}
