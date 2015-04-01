/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.context.JdiContext

import Names.Debugger
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
 * This is proxy-aware transformer.
 * It works like TransformationPhase but skip all part of tree that is dynamic or is not a part of original expression.
 *
 * @tparam Tpe where this phase is placed relative to `TypeCheck` phase
 */
abstract class AstTransformer[+Tpe <: TypecheckRelation]
    extends TransformationPhase[Tpe]
    with AstHelpers {

  import universe._

  private var _data: TransformationPhaseData = _

  /** Initial data passed to this transformer */
  protected final def data: TransformationPhaseData = _data

  /**
   * Basic method for transforming a tree
   * for setting further in tree it should call transformFurther but not transformSingleTree or transform method
   * @param baseTree tree to transform
   * @param transformFurther call it on tree node to recursively transform it further
   */
  protected def transformSingleTree(baseTree: universe.Tree, transformFurther: universe.Tree => universe.Tree): universe.Tree

  /**
   * Main method for transformer, applies transformation.
   *
   * To modify the result (for example to add some meta-data) override it and use `super.transform`.
   */
  override def transform(data: TransformationPhaseData): TransformationPhaseData = {
    _data = data
    val newTree = transformer.transform(data.tree)
    data.after(phaseName, newTree)
  }

  /** Checks if symbol corresponds to some of methods on `scala.Dynamic`. */
  private def isDynamicMethod(methodName: String) =
    Scala.dynamicTraitMethods.contains(methodName)

  /** Construction/destruction for `__context.newInstance` */
  object NewInstanceCall {

    import Debugger._

    def apply(className: Tree, argList: Tree): Tree =
      Apply(SelectMethod(contextParamName, newInstance), List(className, argList))

    def unapply(tree: Tree): Option[(Tree, Tree)] = tree match {
      case Apply(Select(Ident(TermName(`contextParamName`)), TermName(`newInstance`)), List(className, argList)) =>
        Some(className, argList)
      case _ => None
    }
  }

  /** Transformer that skip all part of tree that is dynamic and it is not a part of original expression */
  private val transformer = new universe.Transformer {
    private val baseTransform = super.transform _

    override def transform(baseTree: universe.Tree): universe.Tree = baseTree match {
      // dynamic calls
      case JdiSelectDynamic(on, name) =>
        JdiSelectDynamic(transform(on), name)
      case JdiUpdateDynamic(on, name, value) =>
        JdiUpdateDynamic(transform(on), name, transform(value))
      case JdiApplyDynamic(on, name, values) =>
        JdiApplyDynamic(transform(on), name, values.map(transform))
      case NoArgumentsJdiApplyDynamic(on, name) =>
        NoArgumentsJdiApplyDynamic(transform(on), name)
      // on calls to `__context.newInstance` do not process the first argument
      case NewInstanceCall(className, argList) =>
        NewInstanceCall(className, transform(argList))
      case tree =>
        transformSingleTree(tree, baseTransform)
    }

    import Scala._

    /**
     * Match exact select dynamic call.
     * Used when dynamic methods are resolved during typecheck not compilation.
     */
    object JdiSelectDynamic {
      def apply(tree: Tree, name: Tree): Tree =
        Apply(Select(tree, TermName(SelectDynamicName)), List(name))

      def unapply(t: Tree): Option[(Tree, Tree)] = t match {
        case Apply(Select(on, TermName(SelectDynamicName)), List(name)) =>
          Some((on, name))
        case _ => None
      }
    }

    /**
     * Match exact update dynamic call.
     * Used when dynamic methods are resolved during typecheck not compilation.
     */
    object JdiUpdateDynamic {

      def apply(on: Tree, name: Tree, value: Tree): Tree = {
        val selectVariable = Apply(Select(on, TermName(UpdateDynamicName)), List(name))
        Apply(selectVariable, List(value))
      }

      def unapply(t: Tree): Option[(Tree, Tree, Tree)] = t match {
        case Apply(Apply(Select(on, TermName(UpdateDynamicName)), List(name)), List(value)) =>
          Some((on, name, value))
        case _ => None
      }
    }

    /**
     * Match exact apply dynamic call without arguments list.
     * Used when dynamic methods are resolved during typecheck not compilation.
     */
    object NoArgumentsJdiApplyDynamic {
      def apply(on: Tree, name: Tree): Tree =
        Apply(Select(transform(on), TermName(ApplyDynamicName)), List(name))

      def unapply(t: Tree): Option[(Tree, Tree)] = t match {
        case Apply(Select(on, TermName(ApplyDynamicName)), List(name)) =>
          Some((on, name))
        case _ => None
      }
    }

    /**
     * Match exact apply dynamic call with arguments list.
     *   Used when dynamic methods are resolved during typecheck not compilation.
     */
    object JdiApplyDynamic {
      def apply(on: Tree, name: Tree, args: List[Tree]): Tree =
        Apply(NoArgumentsJdiApplyDynamic(on, name), args)

      def unapply(t: Tree): Option[(Tree, Tree, List[Tree])] = t match {
        case Apply(NoArgumentsJdiApplyDynamic(on, name), values) =>
          Some(on, name, values)
        case _ => None
      }
    }

  }
}
