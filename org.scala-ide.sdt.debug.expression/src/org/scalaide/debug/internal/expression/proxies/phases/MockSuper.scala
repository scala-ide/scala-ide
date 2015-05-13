/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._
import scala.tools.reflect.ToolBox

import Names.Debugger

object MockSuper {
  import Debugger._

  val SuperTypeMarker = "super"

  private val MockSuperSelect = Select(Ident(TermName(s"$contextName")), s"$placeholderSuperName")

  def apply(methodCall: Tree, tpe: String): Apply =
    Apply(MockSuperSelect, List(methodCall, Literal(Constant(tpe))))

  def unapply(tree: Apply): Option[(Tree, String)] = tree match {
    case Apply(TypeApply(qualifier, _), List(methodCall, Apply(_, List(tpe))))
        if qualifier.toString.endsWith(MockSuperSelect.toString()) =>
      Some((methodCall, tpe.toString.replaceAll("\"", "")))
    case _ =>
      None
  }
}

/**
 * Transformer for converting `super` usages into special placeholder.
 *
 * Transforms:
 * {{{
 * __this.super.list(1, 2);
 * __this.super[BaseTrait1].list(1, 2);
 * __this.super.InnerObject.vararg(1, 2)
 * }}}
 *
 * to:
 * {{{
 * JdiContext.placeholderSuper(__this.list(1, 2), "super");
 * JdiContext.placeholderSuper(__this.list(1, 2), "BaseTrait1");
 * JdiContext.placeholderSuper(__this.InnerObject, "super").vararg(1, 2)
 * }}}
 *
 * This transformation runs before `typecheck`.
 */
class MockSuper(val toolbox: ToolBox[universe.type]) extends AstTransformer[BeforeTypecheck] {

  /** Creates a proxy to replace `super` with placeholder */
  private def createProxy(qualifier: Tree, tpe: TypeName, name: Name, args: List[Tree]): Tree = {
    val selectOrApply = if (args.isEmpty) Select(qualifier, name) else Apply(Select(qualifier, name), args)
    val thisType = if (tpe.toString.isEmpty) MockSuper.SuperTypeMarker else tpe.toString()
    MockSuper(selectOrApply, thisType)
  }

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case GenericSelectOrApply(Super(qualifier, tpe), name, args) =>
      createProxy(qualifier, tpe, name, args)
    case other =>
      transformFurther(other)
  }
}
