/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse;

import scala.collection.Sequence

import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.text.ContentAssistPreference
import org.eclipse.jdt.ui.text.{ JavaSourceViewerConfiguration, IJavaPartitions }
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.jface.text.presentation.PresentationReconciler
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.texteditor.ITextEditor

class ScalaSourceViewerConfiguration(store : IPreferenceStore, editor : ITextEditor)
  extends JavaSourceViewerConfiguration(JavaPlugin.getDefault.getJavaTextTools.getColorManager, store, editor, IJavaPartitions.JAVA_PARTITIONING) {
  
  private val codeScanner = new ScalaCodeScanner(getColorManager, store);
  
  override def getPresentationReconciler(sv : ISourceViewer) = {
    val reconciler = super.getPresentationReconciler(sv).asInstanceOf[PresentationReconciler]
    val dr = new ScalaDamagerRepairer(codeScanner)
    reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
    reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
    reconciler
  }
    
  override def getTextHover(sv : ISourceViewer, contentType : String, stateMask : Int) = {
    val hover = new ScalaTextHover
    hover.setEditor(editor)
    hover
  }
  
  override def getHyperlinkDetectors(sv : ISourceViewer) = {
    super.getHyperlinkDetectors(sv) ++ Sequence(new ScalaHyperlinkDetector)
  }

  override def getContentAssistant(sv : ISourceViewer) = {
    val assistant = super.getContentAssistant(sv).asInstanceOf[ContentAssistant]
    if (assistant != null) {
      val scalaProcessor = new ScalaCompletionProcessor(editor, assistant, IDocument.DEFAULT_CONTENT_TYPE)
      assistant.setContentAssistProcessor(scalaProcessor, IDocument.DEFAULT_CONTENT_TYPE)
      ContentAssistPreference.configure(assistant, fPreferenceStore)
    }
    assistant
  }
}
