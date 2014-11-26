/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.editor.decorators.custom

import org.scalaide.core.compiler.{IScalaPresentationCompiler => SPC}

case class TraverserId(is: String) extends AnyVal

/**
 * Base trait for traversers.
 */
trait TraverserDef {

  /** Message to show on IU when this traverser find something */
  def message: String

  /** Method for initializing this traverser - returns implementation based on provided compiler */
  def init(compiler: SPC): TraverserImpl
}

object TraverserDef {

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
final case class AllMethodsTraverserDef(
  override val message: String,
  val typeDefinition: TraverserDef.TypeDefinition) extends TraverserDef {

  override def init(compiler: SPC) = AllMethodsTraverserImpl(this, compiler)
}

/**
 * Annotates all method calls that matches given MethodDefinition.
 */
final case class MethodTraverserDef(
  override val message: String,
  val methodDefinition: TraverserDef.MethodDefinition) extends TraverserDef {

  override def init(compiler: SPC) = MethodTraverserImpl(this, compiler)
}

/**
 * Annotates all method calls that matches given AnnotationDefinition.
 */
final case class AnnotationTraverserDef(
  override val message: String,
  val annotation: TraverserDef.AnnotationDefinition) extends TraverserDef {

  override def init(compiler: SPC) = AnnotationTraverserImpl(this, compiler)
}
