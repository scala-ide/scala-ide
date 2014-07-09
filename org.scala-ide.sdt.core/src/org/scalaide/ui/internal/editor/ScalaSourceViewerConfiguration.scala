package org.scalaide.ui.internal.editor

import org.scalaide.core.internal.formatter.ScalaFormattingStrategy
import org.scalaide.core.hyperlink.detector.CompositeHyperlinkDetector
import org.scalaide.core.hyperlink.detector.DeclarationHyperlinkDetector
import org.scalaide.core.hyperlink.detector.ImplicitHyperlinkDetector
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.internal.lexical._
import org.scalaide.ui.syntax.{ScalaSyntaxClasses => SSC}
import org.scalaide.ui.internal.reconciliation.ScalaReconcilingStrategy
import org.scalaide.ui.internal.editor.autoedits._
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICodeAssist
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput
import org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider
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
import scalariform.ScalaVersions
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.formatter.FormatterPreferences._
import scalariform.formatter.preferences._
import org.eclipse.ui.texteditor.ChainedPreferenceStore
import org.scalaide.ui.editor.extensionpoints.ScalaHoverDebugOverrideExtensionPoint

class ScalaSourceViewerConfiguration(
  javaPreferenceStore: IPreferenceStore,
  scalaPreferenceStore: IPreferenceStore,
  editor: ScalaEditor)
    extends JavaSourceViewerConfiguration(
      JavaPlugin.getDefault.getJavaTextTools.getColorManager,
      javaPreferenceStore,
      editor,
      IJavaPartitions.JAVA_PARTITIONING) {

  private val combinedPrefStore = new ChainedPreferenceStore(
      Array(scalaPreferenceStore, javaPreferenceStore))

  private val codeHighlightingScanners = {
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

  override def getTabWidth(sourceViewer: ISourceViewer): Int =
    scalaPreferenceStore.getInt(IndentSpaces.eclipseKey)

  /**
   * Indent prefixes are all possible variations of strings of a given
   * 'indent' length that can be inserted as indent.
   *
   * As an example, when the indent depth is 4, these are the prefixes:
   *
   * '\t', ' \t', '  \t', '   \t', '    ', '' (when only tabs should be inserted)
   * '    ', '\t', ' \t', '  \t', '   \t', '' (when only spaces should be inserted)
   *
   * The array always contains 2 + indent depth elements, where the last element
   * is always the empty string. The first element describes a full indent depth,
   * whereas the remaining elements describe a combination of spaces + a tab to
   * fill a full indent depth.
   */
  override def getIndentPrefixes(sourceViewer: ISourceViewer, contentType: String): Array[String] = {
    val spaceWidth = scalaPreferenceStore.getInt(IndentSpaces.eclipseKey)
    val useTabs = scalaPreferenceStore.getBoolean(IndentWithTabs.eclipseKey)

    val spacePrefix = " " * spaceWidth
    val prefixes = 0 until spaceWidth map (i => " " * i + "\t")

    if (useTabs)
      (prefixes :+ spacePrefix :+ "").toArray
    else
      (spacePrefix +: prefixes :+ "").toArray
  }

  override def getReconciler(sourceViewer: ISourceViewer): IReconciler =
    if (editor ne null) {
      val reconciler = new MonoReconciler(new ScalaReconcilingStrategy(editor), /*isIncremental = */ false)
      // FG: I don't know any better that to defer this to the MonoReconciler constructor's default value
      // reconciler.setDelay(500)
      reconciler.install(sourceViewer)
      reconciler.setProgressMonitor(new NullProgressMonitor())
      reconciler
    } else
      null // the editor is null for the Syntax coloring previewer pane, (so no reconciliation)

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
      case Some(scu: ScalaCompilationUnit) => ScalaHoverDebugOverrideExtensionPoint.hoverFor(scu).getOrElse(new ScalaHover(scu))
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
        Array(new CommentAutoIndentStrategy(combinedPrefStore, partitioning))

      case ScalaPartitions.SCALA_MULTI_LINE_STRING =>
        Array(
          new SmartSemicolonAutoEditStrategy(partitioning),
          new MultiLineStringAutoIndentStrategy(partitioning, ScalaPlugin.prefStore),
          new MultiLineStringAutoEditStrategy(partitioning, ScalaPlugin.prefStore))

      case IJavaPartitions.JAVA_STRING =>
        Array(
          new SmartSemicolonAutoEditStrategy(partitioning),
          new StringAutoEditStrategy(partitioning, ScalaPlugin.prefStore))

      case IJavaPartitions.JAVA_CHARACTER | IDocument.DEFAULT_CONTENT_TYPE =>
        Array(
          new SmartSemicolonAutoEditStrategy(partitioning),
          new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, prefProvider),
          new AutoIndentStrategy(ScalaPlugin.prefStore),
          new BracketAutoEditStrategy(ScalaPlugin.prefStore),
          new LiteralAutoEditStrategy(ScalaPlugin.prefStore))

      case _ =>
        Array(
            new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, prefProvider),
            new AutoIndentStrategy(ScalaPlugin.prefStore))
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
