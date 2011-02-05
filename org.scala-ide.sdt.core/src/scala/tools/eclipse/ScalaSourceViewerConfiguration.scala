/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse;

import org.eclipse.jdt.core.{ IJavaProject, IJavaElement, ICodeAssist }
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.{ IClassFileEditorInput, ICompilationUnitDocumentProvider, JavaElementHyperlinkDetector }
import org.eclipse.jdt.internal.ui.text.ContentAssistPreference
import org.eclipse.jdt.internal.ui.text.java.{ JavaAutoIndentStrategy, JavaStringAutoIndentStrategy, SmartSemicolonAutoEditStrategy }
import org.eclipse.jdt.internal.ui.text.java.hover.{ AbstractJavaEditorTextHover, BestMatchHover }
import org.eclipse.jdt.internal.ui.text.javadoc.JavaDocAutoIndentStrategy
import org.eclipse.jdt.ui.text.{ JavaSourceViewerConfiguration, IJavaPartitions }
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{ IAutoEditStrategy, IDocument, ITextHover }
import org.eclipse.jface.text.formatter.ContentFormatter
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.jface.text.contentassist.IContentAssistant
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector
import org.eclipse.jface.text.presentation.PresentationReconciler
import org.eclipse.jface.text.rules.{ DefaultDamagerRepairer, RuleBasedScanner, ITokenScanner }
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.texteditor.{ HyperlinkDetectorDescriptor, ITextEditor }
import org.eclipse.swt.SWT
import scala.tools.eclipse.ui.{ JdtPreferenceProvider, ScalaAutoIndentStrategy, ScalaIndenter }
import scala.tools.eclipse.util.ReflectionUtils
import scala.tools.eclipse.lexical._
import scala.tools.eclipse.formatter.ScalaFormattingStrategy

class ScalaSourceViewerConfiguration(store : IPreferenceStore, editor : ITextEditor)
  extends JavaSourceViewerConfiguration(JavaPlugin.getDefault.getJavaTextTools.getColorManager, store, editor, IJavaPartitions.JAVA_PARTITIONING) {

  private val codeScanner = new ScalaCodeScanner(getColorManager, store);

  override def getPresentationReconciler(sv : ISourceViewer) = {
    val reconciler = super.getPresentationReconciler(sv).asInstanceOf[PresentationReconciler]
    val dr = new ScalaDamagerRepairer(codeScanner)

    reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE)
    reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE)
    
    def handlePartition(partitionType: String, tokenScanner: ITokenScanner) {
      val dr = new DefaultDamagerRepairer(tokenScanner)
      reconciler.setDamager(dr, partitionType)
      reconciler.setRepairer(dr, partitionType)
    }
    
    handlePartition(ScalaPartitions.SCALA_MULTI_LINE_STRING, getStringScanner())
    handlePartition(ScalaPartitions.XML_TAG, new XmlTagScanner(getColorManager))
    handlePartition(ScalaPartitions.XML_COMMENT, new XmlCommentScanner(getColorManager))
    handlePartition(ScalaPartitions.XML_CDATA, new XmlCDATAScanner(getColorManager))
    handlePartition(ScalaPartitions.XML_PI, new XmlPIScanner(getColorManager))
    
    reconciler
  }

  override def getTextHover(sv : ISourceViewer, contentType : String, stateMask : Int) = new ScalaHover(getCodeAssist)

  override def getHyperlinkDetectors(sv : ISourceViewer) = {
    val shd = new ScalaHyperlinkDetector
    shd.setContext(editor)
    Array(shd)
  }
  
  def getCodeAssist : Option[ICodeAssist] = Option(editor) map { editor =>
    val input = editor.getEditorInput
    val provider = editor.getDocumentProvider

    (provider, input) match {
      case (icudp : ICompilationUnitDocumentProvider, _) => icudp getWorkingCopy input
   	  case (_, icfei : IClassFileEditorInput) => icfei.getClassFile
   	  case _ => null
    }
  }

  def getProject : IJavaProject = {
	getCodeAssist map (_.asInstanceOf[IJavaElement].getJavaProject) orNull
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

  override def getContentFormatter(sourceViewer: ISourceViewer) = {
	val contentFormatter = new ContentFormatter
    contentFormatter.enablePartitionAwareFormatting( false );
    contentFormatter.setFormattingStrategy(new ScalaFormattingStrategy(sourceViewer), IDocument.DEFAULT_CONTENT_TYPE)
	contentFormatter
  }
}
