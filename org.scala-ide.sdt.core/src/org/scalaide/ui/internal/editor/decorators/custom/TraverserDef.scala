/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.editor.decorators.custom

import org.scalaide.core.compiler.{ IScalaPresentationCompiler => SPC }

case class TraverserId(is: String) extends AnyVal

/**
 * Base trait for traversers.
 */
trait TraverserDef {

  /** Message to show on IU when this traverser find something */
  def message: (TraverserDef.Select => String)

  /** Method for initializing this traverser - returns implementation based on provided compiler */
  def init(compiler: SPC): TraverserImpl
}

object TraverserDef {

  /**
   * User-friendly version of `compiler.Select` for usage in traversers.
   *
   * @param prefix on what this method/field was accessed
   * @param name name of method/field which will be highlighted
   */
  case class Select(prefix: String, name: String) {
    override def toString(): String = prefix + "." + name
  }

  case class MethodDefinition(packages: Seq[String], cls: String, method: String) {
    def fullName: String = packages.mkString("", ".", ".") + cls
  }

  case class AnnotationDefinition(packages: Seq[String], name: String) {
    def fullName: String = packages.mkString("", ".", ".") + name
  }

  case class TypeDefinition(packages: Seq[String], name: String) {
    def fullName: String = packages.mkString("", ".", ".") + name
  }
}

/**
 * Annotates all usages of all methods on a type that matches given TypeDefinition.
 */
final class AllMethodsTraverserDef private (
  override val message: TraverserDef.Select => String,
  val typeDefinition: TraverserDef.TypeDefinition) extends TraverserDef {

  override def init(compiler: SPC) = AllMethodsTraverserImpl(this, compiler)
}

/**
 * API for creating instances of `AllMethodsTraverserDef`.
 */
object AllMethodsTraverserDef {

  def apply(message: String, typeDefinition: TraverserDef.TypeDefinition): AllMethodsTraverserDef =
    new AllMethodsTraverserDef(_ => message, typeDefinition)

  def apply(message: TraverserDef.Select => String, typeDefinition: TraverserDef.TypeDefinition): AllMethodsTraverserDef =
    new AllMethodsTraverserDef(message, typeDefinition)
}

/**
 * Annotates all method calls that matches given MethodDefinition.
 */
final class MethodTraverserDef private (
  override val message: TraverserDef.Select => String,
  val methodDefinition: TraverserDef.MethodDefinition) extends TraverserDef {

  override def init(compiler: SPC) = MethodTraverserImpl(this, compiler)
}

/**
 * API for creating instances of `MethodTraverserDef`.
 */
object MethodTraverserDef {

  def apply(message: String, methodDefinition: TraverserDef.MethodDefinition): MethodTraverserDef =
    new MethodTraverserDef(_ => message, methodDefinition)

  def apply(message: TraverserDef.Select => String, methodDefinition: TraverserDef.MethodDefinition): MethodTraverserDef =
    new MethodTraverserDef(message, methodDefinition)
}

/**
 * Annotates all method calls that matches given AnnotationDefinition.
 */
final class AnnotationTraverserDef private(
  override val message: TraverserDef.Select => String,
  val annotation: TraverserDef.AnnotationDefinition) extends TraverserDef {

  override def init(compiler: SPC) = AnnotationTraverserImpl(this, compiler)
}

/**
 * API for creating instances of `AnnotationTraverserDef`.
 */
object AnnotationTraverserDef {

  def apply(message: String, annotation: TraverserDef.AnnotationDefinition): AnnotationTraverserDef =
    new AnnotationTraverserDef(_ => message, annotation)

  def apply(message: TraverserDef.Select => String, annotation: TraverserDef.AnnotationDefinition): AnnotationTraverserDef =
    new AnnotationTraverserDef(message, annotation)
}
