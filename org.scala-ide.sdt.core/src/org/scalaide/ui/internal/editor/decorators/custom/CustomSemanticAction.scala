/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.editor.decorators.custom

import scala.reflect.internal.util.SourceFile

import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.ISourceViewer
import org.scalaide.core.compiler.{ ScalaPresentationCompiler => SPC }
import org.scalaide.core.internal.jdt.model.{ ScalaCompilationUnit => SCU }
import org.scalaide.ui.internal.editor.decorators.BaseSemanticAction

/**
 * Used for creating custom semantic action based on [[org.scalaide.ui.internal.editor.decorators.custom.TraverserRef]]s.
 *
 * @param traversers traversers to use for this action
 * @param sourceViewer
 * @param annotationId id of annotation (must match id from plugin.xml)
 * @param preferencePageId id of preference page, see [[org.scalaide.ui.internal.editor.decorators.BaseSemanticAction]], optional
 */
class CustomSemanticAction(
  traversers: Seq[TraverserDef],
  sourceViewer: ISourceViewer,
  annotationId: String,
  preferencePageId: Option[String] = None)
  extends BaseSemanticAction(sourceViewer, annotationId, preferencePageId) {

  protected final override def findAll(compiler: SPC, scu: SCU, sourceFile: SourceFile): Map[Annotation, Position] = {
    TraverserImpl.extract(compiler)(sourceFile, annotationId, traversers.map(_.init(compiler))).toMap
  }
}