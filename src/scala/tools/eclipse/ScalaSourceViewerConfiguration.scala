/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse;

import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.JavaElementHyperlinkDetector
import org.eclipse.jdt.internal.ui.text.ContentAssistPreference
import org.eclipse.jdt.internal.ui.text.java.hover.{ AbstractJavaEditorTextHover, BestMatchHover }
import org.eclipse.jdt.ui.text.{ JavaSourceViewerConfiguration, IJavaPartitions }
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{ IDocument, ITextHover }
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector
import org.eclipse.jface.text.presentation.PresentationReconciler
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.texteditor.{ HyperlinkDetectorDescriptor, ITextEditor }
import org.eclipse.swt.SWT

import scala.tools.eclipse.util.ReflectionUtils

class ScalaSourceViewerConfiguration(store : IPreferenceStore, editor : ITextEditor) 
  extends JavaSourceViewerConfiguration(JavaPlugin.getDefault.getJavaTextTools.getColorManager, store, editor, IJavaPartitions.JAVA_PARTITIONING) {
  
  import ScalaSourceViewerConfigurationUtils._
  
  private val codeScanner = new ScalaCodeScanner(getColorManager, store);
  
  override def getPresentationReconciler(sv : ISourceViewer) = {
    val reconciler = super.getPresentationReconciler(sv).asInstanceOf[PresentationReconciler]
    val dr = new ScalaDamagerRepairer(codeScanner)
    reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
    reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
    reconciler
  }

  override def getConfiguredTextHoverStateMasks(sourceViewer : ISourceViewer, contentType : String) : Array[Int] =
    (Set.empty ++ super.getConfiguredTextHoverStateMasks(sourceViewer, contentType) ++ Seq(SWT.MOD3, SWT.MOD1|SWT.MOD3)).toArray
  
  override def getTextHover(sv : ISourceViewer, contentType : String, stateMask : Int) = {
    val javaHover = super.getTextHover(sv, contentType, stateMask)
    
    def addHover(hover : AbstractJavaEditorTextHover) = {
      hover.setEditor(editor)
      javaHover match {
        case bmh : BestMatchHover => addTextHover(bmh, hover) ; bmh
        case _ => hover
      }
    }
    
    stateMask match {
      case SWT.MOD3 => addHover(new ScalaInferredTypeHover)
      case x if x == (SWT.MOD1|SWT.MOD3) => addHover(new ScalaDebugHover)
      case _ => javaHover
    }
  }
  
  override def getHyperlinkDetectors(sv : ISourceViewer) = {
    val shd = new ScalaHyperlinkDetector
    shd.setContext(editor)
    super.getHyperlinkDetectors(sv).map(d =>
      if (getHyperlinkDescriptor(d).getId == "org.eclipse.jdt.internal.ui.javaeditor.JavaElementHyperlinkDetector")
        shd
      else
        d)
  }
}

object ScalaSourceViewerConfigurationUtils extends ReflectionUtils {
  val bestMatchHoverClazz = classOf[BestMatchHover]
  val addTextHoverMethod = getDeclaredMethod(bestMatchHoverClazz, "addTextHover", classOf[ITextHover])
  val hyperlinkDetectorDelegateClazz = Class.forName("org.eclipse.ui.texteditor.HyperlinkDetectorRegistry$HyperlinkDetectorDelegate")
  val hyperlinkDescriptorField = getDeclaredField(hyperlinkDetectorDelegateClazz, "fHyperlinkDescriptor")
  
  def addTextHover(bmh : BestMatchHover, hover : ITextHover) = addTextHoverMethod.invoke(bmh, hover)
  def getHyperlinkDescriptor(hdd : IHyperlinkDetector) = hyperlinkDescriptorField.get(hdd).asInstanceOf[HyperlinkDetectorDescriptor]
}
