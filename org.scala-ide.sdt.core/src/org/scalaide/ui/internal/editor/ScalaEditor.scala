package org.scalaide.ui.internal.editor

import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.IScalaEditor

import org.scalaide.core.internal.lexical.ScalaDocumentPartitioner

trait ScalaEditor extends IScalaEditor with ISourceViewerEditor with InteractiveCompilationUnitEditor {

  override def createDocumentPartitioner = new ScalaDocumentPartitioner

}
