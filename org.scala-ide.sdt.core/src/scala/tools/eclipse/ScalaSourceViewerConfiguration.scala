/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.formatter.ScalaFormattingStrategy
import scala.tools.eclipse.hyperlink.text.detector.CompositeHyperlinkDetector
import scala.tools.eclipse.hyperlink.text.detector.DeclarationHyperlinkDetector
import scala.tools.eclipse.hyperlink.text.detector.ImplicitHyperlinkDetector
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.lexical.ScalaCodeScanner
import scala.tools.eclipse.lexical.ScalaCommentScanner
import scala.tools.eclipse.lexical.ScalaPartitions
import scala.tools.eclipse.lexical.ScaladocTokenScanner
import scala.tools.eclipse.lexical.SingleTokenScanner
import scala.tools.eclipse.lexical.StringTokenScanner
import scala.tools.eclipse.lexical.XmlCDATAScanner
import scala.tools.eclipse.lexical.XmlCommentScanner
import scala.tools.eclipse.lexical.XmlPIScanner
import scala.tools.eclipse.lexical.XmlTagScanner
import scala.tools.eclipse.ui.BracketAutoEditStrategy
import scala.tools.eclipse.ui.CommentAutoIndentStrategy
import scala.tools.eclipse.ui.JdtPreferenceProvider
import scala.tools.eclipse.ui.LiteralAutoEditStrategy
import scala.tools.eclipse.ui.MultiLineStringAutoEditStrategy
import scala.tools.eclipse.ui.ScalaAutoIndentStrategy
import scala.tools.eclipse.ui.StringAutoEditStrategy

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICodeAssist
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput
import org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider
import org.eclipse.jdt.internal.ui.javaeditor.JavaElementHyperlinkDetector
import org.eclipse.jdt.internal.ui.text.ContentAssistPreference
import org.eclipse.jdt.internal.ui.text.java.JavaAutoIndentStrategy
import org.eclipse.jdt.internal.ui.text.java.SmartSemicolonAutoEditStrategy
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DefaultTextHover
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.ITextHover
import org.eclipse.jface.text.formatter.IContentFormatter
import org.eclipse.jface.text.formatter.MultiPassContentFormatter
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector
import org.eclipse.jface.text.hyperlink.URLHyperlinkDetector
import org.eclipse.jface.text.reconciler.IReconciler
import org.eclipse.jface.text.reconciler.MonoReconciler
import org.eclipse.jface.text.rules.DefaultDamagerRepairer
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.ui.texteditor.ITextEditor

import scalariform.ScalaVersions
import org.eclipse.jface.text.DefaultTextHover
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.ui.CommentAutoIndentStrategy
import org.eclipse.jface.text.hyperlink.URLHyperlinkDetector
import scala.tools.eclipse.reconciler.ScalaReconcilingStrategy

class ScalaSourceViewerConfiguration(
  javaPreferenceStore: IPreferenceStore,
  scalaPreferenceStore: IPreferenceStore,
  editor: ScalaEditor)
    extends JavaSourceViewerConfiguration(
      JavaPlugin.getDefault.getJavaTextTools.getColorManager,
      javaPreferenceStore,
      editor,
      IJavaPartitions.JAVA_PARTITIONING) {

  private val codeHighlightingScanners = {
    import scala.tools.eclipse.properties.syntaxcolouring.{ ScalaSyntaxClasses => SSC }

    val scalaCodeScanner = new ScalaCodeScanner(scalaPreferenceStore, ScalaVersions.DEFAULT)
    val singleLineCommentScanner = new ScalaCommentScanner(scalaPreferenceStore, javaPreferenceStore, SSC.SINGLE_LINE_COMMENT, SSC.TASK_TAG)
    val multiLineCommentScanner = new ScalaCommentScanner(scalaPreferenceStore, javaPreferenceStore, SSC.MULTI_LINE_COMMENT, SSC.TASK_TAG)
    val scaladocScanner = new ScaladocTokenScanner(scalaPreferenceStore, javaPreferenceStore, SSC.SCALADOC, SSC.SCALADOC_ANNOTATION, SSC.SCALADOC_MACRO, SSC.TASK_TAG)
    val scaladocCodeBlockScanner = new SingleTokenScanner(scalaPreferenceStore, SSC.SCALADOC_CODE_BLOCK)
    val stringScanner = new StringTokenScanner(scalaPreferenceStore, SSC.ESCAPE_SEQUENCE, SSC.STRING)
    val characterScanner = new StringTokenScanner(scalaPreferenceStore, SSC.ESCAPE_SEQUENCE, SSC.CHARACTER)
    val multiLineStringScanner = new SingleTokenScanner(scalaPreferenceStore, SSC.MULTI_LINE_STRING)
    val xmlTagScanner = new XmlTagScanner(scalaPreferenceStore)
    val xmlCommentScanner = new XmlCommentScanner(scalaPreferenceStore)
    val xmlCDATAScanner = new XmlCDATAScanner(scalaPreferenceStore)
    val xmlPCDATAScanner = new SingleTokenScanner(scalaPreferenceStore, SSC.DEFAULT)
    val xmlPIScanner = new XmlPIScanner(scalaPreferenceStore)

    Map(
      IDocument.DEFAULT_CONTENT_TYPE -> scalaCodeScanner,
      IJavaPartitions.JAVA_DOC -> scaladocScanner,
      ScalaPartitions.SCALADOC_CODE_BLOCK -> scaladocCodeBlockScanner,
      IJavaPartitions.JAVA_SINGLE_LINE_COMMENT -> singleLineCommentScanner,
      IJavaPartitions.JAVA_MULTI_LINE_COMMENT -> multiLineCommentScanner,
      IJavaPartitions.JAVA_STRING -> stringScanner,
      IJavaPartitions.JAVA_CHARACTER -> characterScanner,
      ScalaPartitions.SCALA_MULTI_LINE_STRING -> multiLineStringScanner,
      ScalaPartitions.XML_TAG -> xmlTagScanner,
      ScalaPartitions.XML_COMMENT -> xmlCommentScanner,
      ScalaPartitions.XML_CDATA -> xmlCDATAScanner,
      ScalaPartitions.XML_PCDATA -> xmlPCDATAScanner,
      ScalaPartitions.XML_PI -> xmlPIScanner
    )
  }

  override def getReconciler(sourceViewer: ISourceViewer): IReconciler = {
    val reconciler = new MonoReconciler(new ScalaReconcilingStrategy(editor), /*isIncremental = */ false)
    reconciler.setDelay(500)
    reconciler.install(sourceViewer)
    reconciler.setProgressMonitor(new NullProgressMonitor())
    reconciler
  }

  override def getPresentationReconciler(sourceViewer: ISourceViewer): ScalaPresentationReconciler = {
    val reconciler = new ScalaPresentationReconciler()
    reconciler.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer))

    for ((partitionType, tokenScanner) <- codeHighlightingScanners) {
      val dr = new DefaultDamagerRepairer(tokenScanner)
      reconciler.setDamager(dr, partitionType)
      reconciler.setRepairer(dr, partitionType)
    }
    reconciler
  }

  override def getTextHover(sv: ISourceViewer, contentType: String, stateMask: Int): ITextHover =
    getCodeAssist match {
      case Some(scu: ScalaCompilationUnit) => new ScalaHover(scu)
      case _                               => new DefaultTextHover(sv)
    }

  override def getHyperlinkDetectors(sv: ISourceViewer): Array[IHyperlinkDetector] = {
    val strategies = List(DeclarationHyperlinkDetector(), ImplicitHyperlinkDetector())
    val detector = new CompositeHyperlinkDetector(strategies)
    if (editor != null) detector.setContext(editor)
    Array(detector, new URLHyperlinkDetector())
  }

  def getCodeAssist: Option[ICodeAssist] = Option(editor) map { editor =>
    val input = editor.getEditorInput
    val provider = editor.getDocumentProvider

    (provider, input) match {
      case (icudp: ICompilationUnitDocumentProvider, _) => icudp getWorkingCopy input
      case (_, icfei: IClassFileEditorInput)            => icfei.getClassFile
      case _                                            => null
    }
  }

  def getProject: IJavaProject =
    getCodeAssist.map(_.asInstanceOf[IJavaElement].getJavaProject).orNull

  /**
   * Replica of JavaSourceViewerConfiguration#getAutoEditStrategies that returns
   * a ScalaAutoIndentStrategy instead of a JavaAutoIndentStrategy.
   *
   * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getAutoEditStrategies(org.eclipse.jface.text.source.ISourceViewer, java.lang.String)
   */
  override def getAutoEditStrategies(sourceViewer: ISourceViewer, contentType: String): Array[IAutoEditStrategy] = {
    def prefProvider = new JdtPreferenceProvider(getProject)
    val partitioning = getConfiguredDocumentPartitioning(sourceViewer)

    contentType match {
      case IJavaPartitions.JAVA_DOC | IJavaPartitions.JAVA_MULTI_LINE_COMMENT | ScalaPartitions.SCALADOC_CODE_BLOCK =>
        Array(new CommentAutoIndentStrategy(ScalaPlugin.prefStore, partitioning))

      case ScalaPartitions.SCALA_MULTI_LINE_STRING =>
        Array(
          new SmartSemicolonAutoEditStrategy(partitioning),
          new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, prefProvider),
          new MultiLineStringAutoEditStrategy(partitioning, ScalaPlugin.prefStore))

      case IJavaPartitions.JAVA_STRING =>
        Array(
          new SmartSemicolonAutoEditStrategy(partitioning),
          new StringAutoEditStrategy(partitioning, ScalaPlugin.prefStore))

      case IJavaPartitions.JAVA_CHARACTER | IDocument.DEFAULT_CONTENT_TYPE =>
        Array(
          new SmartSemicolonAutoEditStrategy(partitioning),
          new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, prefProvider),
          new BracketAutoEditStrategy(ScalaPlugin.prefStore),
          new LiteralAutoEditStrategy(ScalaPlugin.prefStore))

      case _ =>
        Array(new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, prefProvider))
    }
  }

  override def getContentFormatter(sourceViewer: ISourceViewer): IContentFormatter = {
    val formatter = new MultiPassContentFormatter(getConfiguredDocumentPartitioning(sourceViewer), IDocument.DEFAULT_CONTENT_TYPE)
    formatter.setMasterStrategy(new ScalaFormattingStrategy(editor))
    formatter
  }

  override def handlePropertyChangeEvent(event: PropertyChangeEvent) {
    super.handlePropertyChangeEvent(event)
    codeHighlightingScanners.values foreach (_ adaptToPreferenceChange event)
  }

  /**
   * Adds Scala related partition types to the list of configured content types,
   * in order that they are available for several features of the IDE.
   */
  override def getConfiguredContentTypes(sourceViewer: ISourceViewer): Array[String] =
    super.getConfiguredContentTypes(sourceViewer) ++
      Seq(ScalaPartitions.SCALA_MULTI_LINE_STRING, ScalaPartitions.SCALADOC_CODE_BLOCK)

  override def affectsTextPresentation(event: PropertyChangeEvent) = true

}
