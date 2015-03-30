/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.context.JdiContext

import Names.Scala

/**
 * Sealed hierarchy for defining where `TransformationPhase` is
 * relative to [[org.scalaide.debug.internal.expression.proxies.phases.TypeCheck]].
 */
sealed trait TypecheckRelation
class BeforeTypecheck extends TypecheckRelation
class IsTypecheck extends TypecheckRelation
class AfterTypecheck extends TypecheckRelation

/**
 * Abstract representation of transformation phase, just transform expression tree to another tree
 *
 * @tparam Tpe where this phase is placed relative to `TypeCheck` phase
 */
trait TransformationPhase[+Tpe <: TypecheckRelation] {

  /**
   * Transforms current tree to new form.
   * It is called only once per object lifetime.
   *
   * Result of this method is passed to another TransformationPhase instance.
   *
   * @param data data for transformation. Contains tree and metadata.
   */
  def transform(data: TransformationPhaseData): TransformationPhaseData

  /** Name of this phase - by default just simpleName of class */
  def phaseName = this.getClass.getSimpleName
}

/**
 * Contains current tree and metadata connected with transformation.
 *
 * @param tree tree after last transformation
 * @param history trees after phases
 */
case class TransformationPhaseData(
    tree: universe.Tree = universe.EmptyTree,
    history: Vector[(String, universe.Tree)] = Vector.empty) {

  final def after(phaseName: String, newTree: universe.Tree): TransformationPhaseData =
    this.copy(tree = newTree, history = this.history :+ (phaseName, newTree))
}

/**
 * This is proxy-aware transformer.
 * It works like TransformationPhase but skip all part of tree that is dynamic or is not a part of original expression.
 *
 * @tparam Tpe where this phase is placed relative to `TypeCheck` phase
 */
abstract class AstTransformer[+Tpe <: TypecheckRelation]
    extends TransformationPhase[Tpe] {

  import universe._

  private var _wholeTree: Tree = EmptyTree

  /** gets whole tree for this transformer - transformation starts with this tree */
  final def wholeTree = _wholeTree

  /**
   * Basic method for transforming a tree
   * for setting further in tree it should call transformFurther but not transformSingleTree or transform method
   * @param baseTree tree to transform
   * @param transformFurther call it on tree node to recursively transform it further
   */
  protected def transformSingleTree(baseTree: universe.Tree, transformFurther: universe.Tree => universe.Tree): universe.Tree

  /** Main method for transformer, applies transformation */
  final override def transform(data: TransformationPhaseData): TransformationPhaseData = {
    _wholeTree = data.tree
    val newTree = transformer.transform(data.tree)
    data.after(phaseName, newTree)
  }

  /** Checks if symbol corresponds to some of methods on `scala.Dynamic`. */
  private def isDynamicMethod(methodName: String) =
    Scala.dynamicTraitMethods.contains(methodName)

  private def isContextMethod(on: Tree, name: String): Boolean = {
    on.toString() == Names.Debugger.contextParamName && name == Names.Debugger.newInstance
  }

  /** Transformer that skip all part of tree that is dynamic and it is not a part of original expression */
  private val transformer = new universe.Transformer {
    override def transform(baseTree: universe.Tree): universe.Tree = baseTree match {
      //dynamic calls
      case tree @ universe.Apply(select @ universe.Select(on, name), args) if isDynamicMethod(name.toString) =>
        universe.Apply(universe.Select(transformSingleTree(on, tree => super.transform(tree)), name), args)
      case tree @ universe.Apply(select @ universe.Select(on, name), args) if isContextMethod(on, name.toString) =>
        tree
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
