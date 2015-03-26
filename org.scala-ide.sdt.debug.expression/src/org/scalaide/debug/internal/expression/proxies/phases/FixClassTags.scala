/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

import org.scalaide.debug.internal.expression.AstTransformer

/**
 * Replaces all occurrences of `ClassTag` class with fully qualified `scala.reflect.ClassTag`.
 *
 * Transforms:
 * {{{
 *   immutable.this.List.apply[Int](1, 2, 3).toArray[Int]((ClassTag.Int: scala.reflect.ClassTag[Int]))
 * }}}
 * into:
 * {{{
 *   immutable.this.List.apply[Int](1, 2, 3).toArray[Int](scala.reflect.ClassTag.Int)
 * }}}
 *
 * This phase runs after `typecheck`.
 */
case class FixClassTags(toolbox: ToolBox[universe.type])
    extends AstTransformer[AfterTypecheck] {

  import universe._

  private def isClassTagType(tpe: String) = {
    val classTag = """scala.reflect.ClassTag\[.*?\]""".r
    classTag.findFirstIn(tpe).isDefined
  }

  override final def transformSingleTree(tree: Tree, transformFurther: Tree => Tree): Tree = tree match {
    case This(TypeName("ScalaRunTime")) =>
      toolbox.typecheck(q"scala.runtime.ScalaRunTime")
    case q"ClassTag" =>
      toolbox.typecheck(q"scala.reflect.ClassTag")
    case Typed(expr: Tree, tpt: Tree) if isClassTagType(tpt.toString()) =>
      transformFurther(expr)
    case other =>
      transformFurther(other)
  }
}
