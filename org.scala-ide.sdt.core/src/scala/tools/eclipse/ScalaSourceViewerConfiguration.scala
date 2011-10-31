/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse;

import org.eclipse.jface.text.formatter.MultiPassContentFormatter
import org.eclipse.jface.util.PropertyChangeEvent
import scala.tools.eclipse.semicolon.InferredSemicolonPainter
import org.eclipse.jface.text.ITextViewerExtension2
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
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.ui.texteditor.{ HyperlinkDetectorDescriptor, ITextEditor }
import org.eclipse.swt.SWT
import scala.tools.eclipse.ui.{ JdtPreferenceProvider, ScalaAutoIndentStrategy, ScalaIndenter }
import scala.tools.eclipse.util.ReflectionUtils
import scala.tools.eclipse.lexical._
import scala.tools.eclipse.formatter.ScalaFormattingStrategy
import scala.tools.eclipse.properties.ScalaSyntaxClasses
import scala.tools.eclipse.ui.AutoCloseBracketStrategy

class ScalaSourceViewerConfiguration(store: IPreferenceStore, scalaPreferenceStore: IPreferenceStore, editor: ITextEditor)
   extends JavaSourceViewerConfiguration(JavaPlugin.getDefault.getJavaTextTools.getColorManager, store, editor, IJavaPartitions.JAVA_PARTITIONING) {

   private val codeScanner = new ScalaCodeScanner(getColorManager, store)

   override def getPresentationReconciler(sv: ISourceViewer) = {
      val reconciler = super.getPresentationReconciler(sv).asInstanceOf[PresentationReconciler]
      val dr = new ScalaDamagerRepairer(codeScanner)

      reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE)
      reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE)

      def handlePartition(partitionType: String, tokenScanner: ITokenScanner) {
         val dr = new DefaultDamagerRepairer(tokenScanner)
         reconciler.setDamager(dr, partitionType)
         reconciler.setRepairer(dr, partitionType)
      }

      handlePartition(IDocument.DEFAULT_CONTENT_TYPE, scalaCodeScanner)
      handlePartition(IJavaPartitions.JAVA_DOC, scaladocScanner)
      handlePartition(IJavaPartitions.JAVA_SINGLE_LINE_COMMENT, singleLineCommentScanner)
      handlePartition(IJavaPartitions.JAVA_MULTI_LINE_COMMENT, multiLineCommentScanner)
      handlePartition(IJavaPartitions.JAVA_STRING, stringScanner)
      handlePartition(ScalaPartitions.SCALA_MULTI_LINE_STRING, multiLineStringScanner)
      handlePartition(ScalaPartitions.XML_TAG, xmlTagScanner)
      handlePartition(ScalaPartitions.XML_COMMENT, xmlCommentScanner)
      handlePartition(ScalaPartitions.XML_CDATA, xmlCDATAScanner)
      handlePartition(ScalaPartitions.XML_PCDATA, xmlPCDATAScanner)
      handlePartition(ScalaPartitions.XML_PI, xmlPIScanner)

      reconciler
   }

   private val scalaCodeScanner = new ScalaCodeScanner(getColorManager, scalaPreferenceStore)
   private val singleLineCommentScanner = new SingleTokenScanner(ScalaSyntaxClasses.SINGLE_LINE_COMMENT, getColorManager, scalaPreferenceStore)
   private val multiLineCommentScanner = new SingleTokenScanner(ScalaSyntaxClasses.MULTI_LINE_COMMENT, getColorManager, scalaPreferenceStore)
   private val scaladocScanner = new SingleTokenScanner(ScalaSyntaxClasses.SCALADOC, getColorManager, scalaPreferenceStore)
   private val stringScanner = new SingleTokenScanner(ScalaSyntaxClasses.STRING, getColorManager, scalaPreferenceStore)
   private val multiLineStringScanner = new SingleTokenScanner(ScalaSyntaxClasses.MULTI_LINE_STRING, getColorManager, scalaPreferenceStore)
   private val xmlTagScanner = new XmlTagScanner(getColorManager, scalaPreferenceStore)
   private val xmlCommentScanner = new XmlCommentScanner(getColorManager, scalaPreferenceStore)
   private val xmlCDATAScanner = new XmlCDATAScanner(getColorManager, scalaPreferenceStore)
   private val xmlPCDATAScanner = new SingleTokenScanner(ScalaSyntaxClasses.DEFAULT, getColorManager, scalaPreferenceStore)
   private val xmlPIScanner = new XmlPIScanner(getColorManager, scalaPreferenceStore)

   override def getTextHover(sv: ISourceViewer, contentType: String, stateMask: Int) = {
     new ScalaHover(getCodeAssist _)
   }

   override def getHyperlinkDetectors(sv: ISourceViewer) = {
      val shd = new ScalaHyperlinkDetector
      if (editor != null)
         shd.setContext(editor)
      Array(shd)
   }

   def getCodeAssist: Option[ICodeAssist] = Option(editor) map { editor =>
      val input = editor.getEditorInput
      val provider = editor.getDocumentProvider

      (provider, input) match {
         case (icudp: ICompilationUnitDocumentProvider, _) => icudp getWorkingCopy input
         case (_, icfei: IClassFileEditorInput) => icfei.getClassFile
         case _ => null
      }
   }

   def getProject: IJavaProject = {
      getCodeAssist map (_.asInstanceOf[IJavaElement].getJavaProject) orNull
   }

   /**
    * Replica of JavaSourceViewerConfiguration#getAutoEditStrategies that returns
    * a ScalaAutoIndentStrategy instead of a JavaAutoIndentStrategy.
    *
    * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getAutoEditStrategies(org.eclipse.jface.text.source.ISourceViewer, java.lang.String)
    */
   override def getAutoEditStrategies(sourceViewer: ISourceViewer, contentType: String): Array[IAutoEditStrategy] = {
      val partitioning = getConfiguredDocumentPartitioning(sourceViewer)
      contentType match {
         case IJavaPartitions.JAVA_DOC | IJavaPartitions.JAVA_MULTI_LINE_COMMENT =>
            Array(new JavaDocAutoIndentStrategy(partitioning))
         case IJavaPartitions.JAVA_STRING =>
            Array(new SmartSemicolonAutoEditStrategy(partitioning), new JavaStringAutoIndentStrategy(partitioning))
         case IJavaPartitions.JAVA_CHARACTER | IDocument.DEFAULT_CONTENT_TYPE =>
            Array(new SmartSemicolonAutoEditStrategy(partitioning), new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, new JdtPreferenceProvider(getProject)), new AutoCloseBracketStrategy)
         case _ =>
            Array(new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, new JdtPreferenceProvider(getProject)))
      }
   }

  override def getContentFormatter(sourceViewer: ISourceViewer) = {
    val formatter = new MultiPassContentFormatter(getConfiguredDocumentPartitioning(sourceViewer), IDocument.DEFAULT_CONTENT_TYPE)
    formatter.setMasterStrategy(new ScalaFormattingStrategy(editor))
    formatter
  }

   override def handlePropertyChangeEvent(event: PropertyChangeEvent) {
      super.handlePropertyChangeEvent(event)
      scalaCodeScanner.adaptToPreferenceChange(event)
      scaladocScanner.adaptToPreferenceChange(event)
      stringScanner.adaptToPreferenceChange(event)
      multiLineStringScanner.adaptToPreferenceChange(event)
      singleLineCommentScanner.adaptToPreferenceChange(event)
      multiLineCommentScanner.adaptToPreferenceChange(event)
      xmlTagScanner.adaptToPreferenceChange(event)
      xmlCommentScanner.adaptToPreferenceChange(event)
      xmlCDATAScanner.adaptToPreferenceChange(event)
      xmlPCDATAScanner.adaptToPreferenceChange(event)
      xmlPIScanner.adaptToPreferenceChange(event)
   }
   
   override def getConfiguredContentTypes(sourceViewer: ISourceViewer): Array[String] = {
     // Adds the SCALA_MULTI_LINE_STRING partition type to the list of configured content types, so it is
     // supported for the comment out and shift left/right actions
	 return super.getConfiguredContentTypes(sourceViewer) :+ ScalaPartitions.SCALA_MULTI_LINE_STRING
   }

   override def affectsTextPresentation(event: PropertyChangeEvent) = true

}
