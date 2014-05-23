/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

import org.scalaide.debug.internal.expression.context.JdiContext

/**
 * Abstract representation of transformation phase, just transform expression tree to another tree
 */
trait TransformationPhase {

  /**
   * Tranforms current tree to new form.
   * It is called only once per object livetime.
   * Result of this method is passed to another TransformationPhase instance.
   * @param baseTree tree to transform
   */
  def transform(baseTree: universe.Tree): universe.Tree

  /** Breaks block of code into Seq of matching trees */
  protected final def breakBlock(code: universe.Tree)(single: PartialFunction[universe.Tree, Seq[universe.Tree]]): Seq[universe.Tree] = {
    single.orElse[universe.Tree, Seq[universe.Tree]] {
      case block: universe.Block => block.children
      case empty @ universe.EmptyTree => Nil
      case any => throw new UnsupportedTree("breaking block of given type", any)
    }.apply(code)
  }

  /** Thrown when breakBlock encounters unsupported tree */
  private class UnsupportedTree(where: String, tree: Any) extends IllegalArgumentException(s"Unsupported tree for: $where\n$tree")

}

/**
 * This is proxy-aware transformer.
 * It works like TransformationPhase but skip all part of tree that is dynamic or is not a part of original expression.
 */
abstract class AstTransformer
  extends TransformationPhase {

  /**
   * Basic method for transforming a tree
   * for seting futher in tree it should call transformFurther but not transformSingleTree or transform method
   * @param baseTree tree to transform
   * @param transformFurther call it on tree node to recursively transform it further
   */
  protected def transformSingleTree(baseTree: universe.Tree, transformFurther: universe.Tree => universe.Tree): universe.Tree

  /** Main method for transformer, applies transformation */
  final override def transform(baseTree: universe.Tree): universe.Tree =
    transformer.transform(baseTree)

  /** Checks if symbol corresponds to some of methods on `scala.Dynamic`. */
  private def isJdiDynamicProxyMethod(functionName: String) =
    ScalaOther.dynamicTraitMethods.contains(functionName)

  /** Transformer that skip all part of tree that is dynamic and it is not a part of original expression */
  private val transformer = new universe.Transformer {
    override def transform(baseTree: universe.Tree): universe.Tree = baseTree match {
      case tree @ universe.Apply(select @ universe.Select(on, name), args) if isJdiDynamicProxyMethod(name.toString) =>
        universe.Apply(universe.Select(transformSingleTree(on, tree => super.transform(tree)), name), args)
      case tree =>
        transformSingleTree(baseTree, super.transform)
    }
  }

  /** Helper for creating Select on 'apply' method */
  protected def SelectApplyMethod(typeName: String): Select = SelectMethod(typeName, "apply")

  /** Helper for creating Select on given method */
  protected def SelectMethod(typeName: String, methodName: String): Select =
    Select(Ident(newTermName(typeName)), newTermName(methodName))
}