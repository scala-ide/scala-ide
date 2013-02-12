/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jface.text.rules.FastPartitioner
import org.eclipse.jface.text.IDocumentPartitioner
import org.eclipse.jdt.ui.text.IJavaPartitions
import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.IScalaEditor
import scala.tools.eclipse.lexical._
import scala.tools.eclipse.ui.InteractiveCompilationUnitEditor
import scala.tools.eclipse.ui.DecoratedInteractiveEditor

trait ScalaEditor extends IScalaEditor with ISourceViewerEditor with InteractiveCompilationUnitEditor with DecoratedInteractiveEditor{

  def createDocumentPartitioner = new ScalaDocumentPartitioner

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
