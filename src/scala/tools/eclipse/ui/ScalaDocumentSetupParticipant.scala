/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$
package scala.tools.eclipse.ui

import org.eclipse.jdt.internal.ui.javaeditor.JavaDocumentSetupParticipant
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.ui.text.IJavaPartitions

import org.eclipse.jface.text.rules.RuleBasedPartitioner

import org.eclipse.jface.text.IDocument

import scala.tools.eclipse.ScalaPartitionScanner

class ScalaDocumentSetupParticipant extends JavaDocumentSetupParticipant {
  
  /**
   * @see org.eclipse.core.filebuffers.IDocumentSetupParticipant#setup(org.eclipse.jface.text.IDocument)
   */
  override def setup(document : IDocument) = {
    var tools = new ScalaTextTools(JavaPlugin.getDefault().getPreferenceStore(), JavaCore.getPlugin().getPluginPreferences(), true)
    tools.setupJavaDocumentPartitioner(document, ScalaPartitionScanner.SCALA_PARTITIONING)
  }
}