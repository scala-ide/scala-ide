/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse;

import org.eclipse.jdt.core.{ IJavaProject, IJavaElement }
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.{ IClassFileEditorInput, ICompilationUnitDocumentProvider, JavaElementHyperlinkDetector }
import org.eclipse.jdt.internal.ui.text.ContentAssistPreference
import org.eclipse.jdt.internal.ui.text.java.{ JavaAutoIndentStrategy, JavaStringAutoIndentStrategy, SmartSemicolonAutoEditStrategy } 
import org.eclipse.jdt.internal.ui.text.java.hover.{ AbstractJavaEditorTextHover, BestMatchHover }
import org.eclipse.jdt.internal.ui.text.javadoc.JavaDocAutoIndentStrategy
import org.eclipse.jdt.ui.text.{ JavaSourceViewerConfiguration, IJavaPartitions }
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{ IAutoEditStrategy, IDocument, ITextHover }
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector
import org.eclipse.jface.text.presentation.PresentationReconciler
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.texteditor.{ HyperlinkDetectorDescriptor, ITextEditor }
import org.eclipse.swt.SWT

import scala.tools.eclipse.ui.{ JdtPreferenceProvider, ScalaAutoIndentStrategy, ScalaIndenter }
import scala.tools.eclipse.util.ReflectionUtils

class ScalaSourceViewerConfiguration(store : IPreferenceStore, editor : ITextEditor) 
  extends JavaSourceViewerConfiguration(JavaPlugin.getDefault.getJavaTextTools.getColorManager, store, editor, ScalaPartitionScanner.SCALA_PARTITIONING) {
  
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
      case SWT.MOD3 => addHover(new ScalaDebugHover)
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
  
  
  /**
   * Direct copy+paste of getProject from SourceViewerConfiguration.
   * <grumble>No need for this to be _private_ in the parent class</grumble>
   */
  def getProject : IJavaProject = {
    if (editor == null)
      return null;

    val input = editor.getEditorInput();
    val provider = editor.getDocumentProvider();
    
    val element = if (provider.isInstanceOf[ICompilationUnitDocumentProvider]) {
      provider.asInstanceOf[ICompilationUnitDocumentProvider].getWorkingCopy(input)
    } else if (input.isInstanceOf[IClassFileEditorInput]) {
      input.asInstanceOf[IClassFileEditorInput].getClassFile()
    } else {
      null
    }
  
    if (element == null) {
      return null;
    }
    
    return element.getJavaProject();
  }
    
    
  /**
   * Replica of JavaSourceViewerConfiguration#getAutoEditStrategies that returns
   * a ScalaAutoIndentStrategy instead of a JavaAutoIndentStrategy.
   * 
   * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getAutoEditStrategies(org.eclipse.jface.text.source.ISourceViewer, java.lang.String)
   */
  override def getAutoEditStrategies(sourceViewer : ISourceViewer, contentType : String) : Array[IAutoEditStrategy] = {
    val partitioning = getConfiguredDocumentPartitioning(sourceViewer)
    
    if (IJavaPartitions.JAVA_DOC.equals(contentType) || IJavaPartitions.JAVA_MULTI_LINE_COMMENT.equals(contentType)) {
      return Array(new JavaDocAutoIndentStrategy(partitioning))
    } else if (IJavaPartitions.JAVA_STRING.equals(contentType)) {
      return Array(new SmartSemicolonAutoEditStrategy(partitioning), new JavaStringAutoIndentStrategy(partitioning))
    } else if (IJavaPartitions.JAVA_CHARACTER.equals(contentType) || IDocument.DEFAULT_CONTENT_TYPE.equals(contentType)) {
      return Array(new SmartSemicolonAutoEditStrategy(partitioning), new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, new JdtPreferenceProvider(getProject)))
    } else {
      return Array(new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, new JdtPreferenceProvider(getProject)))
    }
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
