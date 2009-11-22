/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.ui.ScalaDocumentSetupParticipant

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider
import org.eclipse.jdt.ui.text.IJavaPartitions

import org.eclipse.ui.editors.text.ForwardingDocumentProvider
import org.eclipse.ui.editors.text.TextFileDocumentProvider
import org.eclipse.ui.texteditor.IDocumentProvider

class ScalaDocumentProvider(val parentProvider : IDocumentProvider) extends CompilationUnitDocumentProvider {
  val forwarder = new ForwardingDocumentProvider(ScalaPartitionScanner.SCALA_PARTITIONING, new ScalaDocumentSetupParticipant(), parentProvider)
  setParentDocumentProvider(parentProvider)
}