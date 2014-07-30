package org.scalaide.ui.internal.editor

import org.eclipse.jface.text.rules.FastPartitioner
import org.eclipse.jface.text.IDocumentPartitioner
import org.eclipse.jdt.ui.text.IJavaPartitions
import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.IScalaEditor
import org.scalaide.core.internal.lexical._

trait ScalaEditor extends IScalaEditor with ISourceViewerEditor with InteractiveCompilationUnitEditor {

  override def createDocumentPartitioner = new ScalaDocumentPartitioner

}

object ScalaEditor {

  val LEGAL_CONTENT_TYPES = Array[String](
    IJavaPartitions.JAVA_DOC,
    IJavaPartitions.JAVA_MULTI_LINE_COMMENT,
    IJavaPartitions.JAVA_SINGLE_LINE_COMMENT,
    IJavaPartitions.JAVA_STRING,
    IJavaPartitions.JAVA_CHARACTER,
    ScalaPartitions.SCALA_MULTI_LINE_STRING)

}
