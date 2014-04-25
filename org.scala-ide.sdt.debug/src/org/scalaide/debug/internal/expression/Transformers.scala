/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

import org.scalaide.debug.internal.expression.context.JdiContext

import Names.Scala

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
}

trait BeforeTypecheck{
  self: AstTransformer =>
  override protected def beforeTypecheck: Boolean = true
}

/**
 * This is proxy-aware transformer.
 * It works like TransformationPhase but skip all part of tree that is dynamic or is not a part of original expression.
 */
abstract class AstTransformer
  extends TransformationPhase {

  private var _wholeTree: Tree = EmptyTree

  protected def beforeTypecheck: Boolean = false

  /** gets whole tree for this transformer - transformation starts with this tree */
  final def wholeTree = _wholeTree

  /**
   * Basic method for transforming a tree
   * for seting futher in tree it should call transformFurther but not transformSingleTree or transform method
   * @param baseTree tree to transform
   * @param transformFurther call it on tree node to recursively transform it further
   */
  protected def transformSingleTree(baseTree: universe.Tree, transformFurther: universe.Tree => universe.Tree): universe.Tree

  /** Main method for transformer, applies transformation */
  final override def transform(baseTree: universe.Tree): universe.Tree = {
    _wholeTree = baseTree
    transformer.transform(baseTree)
  }

  /** Checks if symbol corresponds to some of methods on `scala.Dynamic`. */
  private def isDynamicMethod(methodName: String) =
    Scala.dynamicTraitMethods.contains(methodName)

  /** Transformer that skip all part of tree that is dynamic and it is not a part of original expression */
  private val transformer = new universe.Transformer {
    override def transform(baseTree: universe.Tree): universe.Tree = baseTree match {
      case tree @ universe.Apply(select @ universe.Select(on, name), args) if isDynamicMethod(name.toString) =>
        universe.Apply(universe.Select(transformSingleTree(on, tree => super.transform(tree)), name), args)
      case tree =>
        transformSingleTree(tree, super.transform)
    }
  }

  /** Helper for creating Select on 'apply' method */
  protected def SelectApplyMethod(typeName: String): Select = SelectMethod(typeName, "apply")

  /** Helper for creating Select on given method */
  protected def SelectMethod(typeName: String, methodName: String): Select =
    Select(Ident(TermName(typeName)), TermName(methodName))
}