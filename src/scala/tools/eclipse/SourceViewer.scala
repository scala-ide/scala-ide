/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse;

import java.{ util => ju }

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProposal
import org.eclipse.jdt.internal.ui.text.java.hover.{ JavadocBrowserInformationControlInput, JavadocHover }
import org.eclipse.jdt.ui.text.{ JavaSourceViewerConfiguration, IJavaPartitions }
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext
import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{ TextPresentation, ITypedRegion, IDocument, ITextViewer, IRegion }
import org.eclipse.jface.text.contentassist.{ ContentAssistant, IContentAssistProcessor }
import org.eclipse.jface.text.hyperlink.{ IHyperlink, IHyperlinkDetector }
import org.eclipse.jface.text.presentation.PresentationReconciler
import org.eclipse.jface.text.rules.{ DefaultDamagerRepairer, ITokenScanner }
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.swt.graphics.Image
import org.eclipse.ui.texteditor.ITextEditor

import scala.tools.eclipse.contribution.weaving.jdt.ui.text.java.ScalaCompletionProcessor
import scala.tools.eclipse.util.ReflectionUtils

class ScalaHyperlinkDetector extends IHyperlinkDetector {
  override def detectHyperlinks(tv : ITextViewer, region : IRegion, canShowMultiple : Boolean) : Array[IHyperlink] = {
    null
  }
}

class ScalaSontentAssistProcessor(editor : ITextEditor) extends ScalaCompletionProcessor(editor, new ContentAssistant, IDocument.DEFAULT_CONTENT_TYPE) {
  override def collectProposals0(tv : ITextViewer, offset : Int, monitor : IProgressMonitor,  context : ContentAssistInvocationContext) : ju.List[_] = {
    // val completions = file.get.doComplete(offset) // TODO
    //ju.Arrays.asList(completions.toArray : _*)
    ju.Collections.EMPTY_LIST
  }

  def newCompletion(offset : Int, length : Int, text : String, 
    info : Option[String], image : Option[Image], additional : => Option[String]) = {
    new JavaCompletionProposal(text, offset, length, image getOrElse null, text + info.getOrElse(""), 0) {
      override def apply(viewer : ITextViewer, trigger : Char, stateMask : Int, offset : Int) {
        super.apply(viewer, trigger, stateMask, offset)
      }
    }
  }
}

class ScalaDamagerRepairer(scanner : ITokenScanner) extends DefaultDamagerRepairer(scanner) {
  override def createPresentation(presentation : TextPresentation, damage : ITypedRegion) : Unit =
    super.createPresentation(presentation, damage);
}

class ScalaTextHover extends JavadocHover with ReflectionUtils {
  val getStyleSheetMethod = getDeclaredMethod(classOf[JavadocHover], "getStyleSheet")
  
  override def getHoverInfo2(textViewer : ITextViewer, hoverRegion :  IRegion) = {
    val i = super.getHoverInfo2(textViewer, hoverRegion)
    if (i != null)
      i
    else {
      val s = "Not yet implemented"
      val buffer = new StringBuffer(s)
      HTMLPrinter.insertPageProlog(buffer, 0, getStyleSheet0)
      HTMLPrinter.addPageEpilog(buffer)
      
      new JavadocBrowserInformationControlInput(null, null, buffer.toString, 0)
    }
  }
  
  def getStyleSheet0 = getStyleSheetMethod.invoke(null).asInstanceOf[String]
}

class ScalaSourceViewerConfiguration(store : IPreferenceStore, editor : ITextEditor)
  extends JavaSourceViewerConfiguration(JavaPlugin.getDefault.getJavaTextTools.getColorManager, store, editor, IJavaPartitions.JAVA_PARTITIONING) {
    
  private val codeScanner = new ScalaCodeScanner(getColorManager, store);
  private val contentAssistProcessor = new ScalaSontentAssistProcessor(editor) 
  
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
  
  override def getHyperlinkDetectors(sv : ISourceViewer) = Array(new ScalaHyperlinkDetector)

  override def getContentAssistant(sv : ISourceViewer) = {
    val assistant = super.getContentAssistant(sv).asInstanceOf[ContentAssistant]
    assistant.setContentAssistProcessor(contentAssistProcessor, IDocument.DEFAULT_CONTENT_TYPE)
    assistant
  }
}
