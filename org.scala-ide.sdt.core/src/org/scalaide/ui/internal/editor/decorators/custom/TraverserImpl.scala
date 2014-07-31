/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.editor.decorators.custom

import scala.reflect.internal.util.SourceFile

import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.Annotation
import org.scalaide.core.compiler.{ ScalaPresentationCompiler => SPC }
import org.scalaide.logging.HasLogger

/**
 * Base trait for traverser implementations.
 */
private[custom] trait TraverserImpl extends HasLogger {

  /** Definition of what should be extracted */
  def traverserDef: TraverserDef

  /** Compiler instance - for types and reflection */
  val compiler: SPC

  /**
   * Main method to be implemented by subclasses,
   * it takes a Tree and returns position and message of annotation that should be added there.
   */
  def apply(tree: SPC#Tree): Option[(SPC#Position, String)]
}

object TraverserImpl extends HasLogger {

  /**
   * Extracts all annotations from given source using given traversers.
   */
  final def extract(compiler: SPC)(sourceFile: SourceFile, annotationId: String, traversers: Seq[TraverserImpl]): Seq[(Annotation, Position)] = {
    var regions = IndexedSeq.empty[(Annotation, Position)]

    new compiler.Traverser {
      override def traverse(tree: compiler.Tree): Unit = {
        for {
          traverser <- traversers
          (pos, msg) <- traverser(tree)
          annotation <- createAnnotation(pos, msg, annotationId)
        } regions :+= annotation
        super.traverse(tree)
      }
    }.traverse(compiler.loadedType(sourceFile).fold(identity, _ => compiler.EmptyTree))
    regions
  }

  /** Helper for creating annotations */
  private def createAnnotation(pos: SPC#Position, message: String, annotationId: String): Option[(Annotation, Position)] = {
    val annotation = new CustomAnnotation(annotationId, message)
    val position =
      if (pos.isDefined) new Position(pos.start, pos.end - pos.start)
      else new Position(0, 0)
    if (position.getLength != 0) Some(annotation -> position)
    else {
      logger.warn(s"Skipping annotation, position length = 0, position offset = ${position.getOffset}")
      None
    }
  }

  protected class CustomAnnotation(id: String, text: String) extends Annotation(id, /*isPersistent*/ false, text)
}

/**
 * Implementation for traversers looking for all usages of all methods on given type.
 */
final case class AllMethodsTraverserImpl(traverserDef: AllMethodsTraverserDef, compiler: SPC) extends TraverserImpl {

  /** Checks if AST node matches type definition */
  private def checkType(obj: compiler.Tree): Boolean = {
    val result = compiler.askOption { () =>
      val requiredClass = compiler.rootMirror.getRequiredClass(traverserDef.typeDefinition.fullName)
      val hasType = obj.tpe.erasure
      val needsType = requiredClass.toType.erasure
      hasType <:< needsType
    }
    result.getOrElse(false)
  }

  override def apply(tree: SPC#Tree): Option[(SPC#Position, String)] = {
    import compiler.Select
    tree match {
      case select @ Select(obj, method) if checkType(obj) && !select.symbol.isConstructor => Some((obj.pos, traverserDef.message))
      case _ => None
    }
  }
}

/**
 * Implementation for traversers looking for all usages of single method on given type.
 */
final case class MethodTraverserImpl(traverserDef: MethodTraverserDef, compiler: SPC) extends TraverserImpl {

  /**
   * Checks if AST node matches method definition.
   */
  private def checkMethod(obj: compiler.Tree, name: compiler.Name): Boolean = {

    def checkMethod(methodName: String): Boolean = name.toString() == methodName

    val result = compiler.askOption { () =>
      val requiredClass = compiler.rootMirror.getRequiredClass(traverserDef.methodDefinition.fullName)
      val hasType = obj.tpe
      val needsType = requiredClass.toType
      checkMethod(traverserDef.methodDefinition.method) && hasType.erasure <:< needsType.erasure
    }

    result.getOrElse(false)
  }

  override def apply(tree: SPC#Tree): Option[(SPC#Position, String)] = {
    import compiler.Select
    tree match {
      case select @ Select(obj, method) if checkMethod(obj, method) && !select.symbol.isConstructor => Some((select.pos, traverserDef.message))
      case _ => None
    }
  }
}

/**
 * Implementation for traversers looking for all usages of all methods annotated with given annotation.
 */
final case class AnnotationTraverserImpl(traverserDef: AnnotationTraverserDef, compiler: SPC) extends TraverserImpl {

  /**
   * Checks if AST node matches annotation definition.
   */
  private def checkAnnotations(select: SPC#Select): Boolean = {
    // for defs
    val symbolAnnots = select.symbol.annotations
    // for vals and vars
    val accessedAnnots: List[SPC#AnnotationInfo] =
      if (select.symbol.isAccessor) compiler.askOption { () =>
        select.symbol.accessed.annotations
      }.getOrElse(Nil)
      else Nil
    compiler.askOption { () =>
      val requiredAnnotation = compiler.rootMirror.getRequiredClass(traverserDef.annotation.fullName)
      (accessedAnnots ++ symbolAnnots).exists(_.symbol == requiredAnnotation)
    } getOrElse (false)
  }

  override def apply(tree: SPC#Tree): Option[(SPC#Position, String)] = {
    import compiler.Select
    tree match {
      case select @ Select(obj, method) if checkAnnotations(select) && !select.symbol.isConstructor => Some((select.pos, traverserDef.message))
      case _ => None
    }
  }
}


