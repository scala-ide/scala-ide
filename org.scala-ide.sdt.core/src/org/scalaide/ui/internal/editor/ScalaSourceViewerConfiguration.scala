package org.scalaide.ui.internal.editor

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.ITypeRoot
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput
import org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider
import org.eclipse.jdt.internal.ui.text.CompositeReconcilingStrategy
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration
import org.eclipse.jface.internal.text.html.BrowserInformationControl
import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.AbstractReusableInformationControlCreator
import org.eclipse.jface.text.DefaultInformationControl
import org.eclipse.jface.text.DefaultTextHover
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IInformationControl
import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.jface.text.ITextHover
import org.eclipse.jface.text.formatter.IContentFormatter
import org.eclipse.jface.text.formatter.MultiPassContentFormatter
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector
import org.eclipse.jface.text.hyperlink.URLHyperlinkDetector
import org.eclipse.jface.text.information.InformationPresenter
import org.eclipse.jface.text.reconciler.IReconciler
import org.eclipse.jface.text.rules.DefaultDamagerRepairer
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.DefaultAnnotationHover
import org.eclipse.jface.text.source.IAnnotationHoverExtension
import org.eclipse.jface.text.source.ILineRange
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.source.LineRange
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.editors.text.EditorsUI
import org.eclipse.ui.texteditor.ChainedPreferenceStore
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.hyperlink._
import org.scalaide.core.internal.formatter.FormatterPreferences._
import org.scalaide.core.internal.formatter.ScalaFormattingStrategy
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.ui.editor.extensionpoints.ScalaHoverDebugOverrideExtensionPoint
import org.scalaide.ui.internal.editor.autoedits._
import org.scalaide.ui.internal.editor.hover.BrowserControlAdditions
import org.scalaide.ui.internal.editor.hover.HoverInformationProvider
import org.scalaide.ui.internal.editor.hover.HtmlHover
import org.scalaide.ui.internal.editor.spelling.ScalaSpellingEngine
import org.scalaide.ui.internal.editor.spelling.SpellingReconcileStrategy
import org.scalaide.ui.internal.editor.spelling.SpellingService
import org.scalaide.ui.internal.reconciliation.ScalaReconcilingStrategy
import org.scalaide.ui.syntax.{ ScalaSyntaxClasses => SSC }
import scalariform.ScalaVersions
import scalariform.formatter.preferences._
import org.scalaide.core.internal.ScalaPlugin
import org.eclipse.jface.util.IPropertyChangeListener
import org.scalaide.core.lexical.ScalaCodeScanners
import org.scalaide.core.lexical.ScalaPartitions
import org.scalaide.ui.editor.hover.IScalaHover
import org.scalaide.ui.internal.reconciliation.ScalaReconciler
import org.scalaide.ui.internal.editor.outline.ScalaOutlineReconcilingStrategy
import org.scalaide.ui.internal.editor.outline.OutlinePageEditorExtension
import org.scalaide.ui.internal.editor.outline.ScalaOutlineInformationControl
import org.eclipse.jface.text.information.IInformationPresenter
import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.jdt.internal.ui.text.JavaElementProvider
import org.eclipse.swt.SWT
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds
import org.eclipse.jface.text.AbstractInformationControlManager
import org.scalaide.ui.internal.editor.outline.ScalaOutlineModelProvider
import org.scalaide.util.eclipse.EditorUtils
import org.eclipse.jface.text.reconciler.IReconcilingStrategy

class ScalaSourceViewerConfiguration(
  javaPreferenceStore: IPreferenceStore,
  scalaPreferenceStore: IPreferenceStore,
  editor: ScalaEditor)
    extends JavaSourceViewerConfiguration(
      JavaPlugin.getDefault.getJavaTextTools.getColorManager,
      javaPreferenceStore,
      editor,
      IJavaPartitions.JAVA_PARTITIONING) with IPropertyChangeListener {

  private val combinedPrefStore = new ChainedPreferenceStore(
    Array(scalaPreferenceStore, javaPreferenceStore))

  private val codeHighlightingScanners = ScalaCodeScanners.codeHighlightingScanners(combinedPrefStore)

  private def getOutlinePresenterControlCreator(sourceViewer: ISourceViewer, commandId: String): IInformationControlCreator =
    new IInformationControlCreator {
      def createInformationControl(parent: Shell): IInformationControl = {
        val shellStyle = SWT.RESIZE;
        val treeStyle = SWT.V_SCROLL | SWT.H_SCROLL;
        new ScalaOutlineInformationControl(parent, shellStyle, treeStyle, commandId, editor.asInstanceOf[OutlinePageEditorExtension]);
      }
    }

  override def getOutlinePresenter(sourceViewer: ISourceViewer, doCodeResolve: Boolean): IInformationPresenter = {

    val presenter = new InformationPresenter(getOutlinePresenterControlCreator(sourceViewer, IJavaEditorActionDefinitionIds.SHOW_OUTLINE))
    presenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer))
    presenter.setAnchor(AbstractInformationControlManager.ANCHOR_GLOBAL)
    val provider = new ScalaOutlineModelProvider(editor.asInstanceOf[ScalaCompilationUnitEditor])
    getConfiguredContentTypes(sourceViewer).foreach { presenter.setInformationProvider(provider, _) }
    presenter.setSizeConstraints(50, 20, true, false)
    presenter
  }
  override def getTabWidth(sourceViewer: ISourceViewer): Int =
    combinedPrefStore.getInt(IndentSpaces.eclipseKey)

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
    val spaceWidth = combinedPrefStore.getInt(IndentSpaces.eclipseKey)
    val useTabs = combinedPrefStore.getBoolean(IndentWithTabs.eclipseKey)

    val spacePrefix = " " * spaceWidth
    val prefixes = 0 until spaceWidth map (i => " " * i + "\t")

    if (useTabs)
      (prefixes :+ spacePrefix :+ "").toArray
    else
      (spacePrefix +: prefixes :+ "").toArray
  }

  /**
   * This annotation hover needs to trim error messages to a single line due to
   * some stylistic limitations in the logic that computes the size of the
   * hover. The method that computes the size is:
   *
   * [[org.eclipse.jface.internal.text.html.BrowserInformationControl.computeSizeHint()]]
   *
   * It basically converts HTML to text whose size is then considered. This
   * works only for a very basic form of HTML - CSS specifications like margins
   * can not be considered. Therefore, we need to remove as much text as
   * possible and show only single lines to keep the error rate of line
   * measurements low.
   *
   * This is also what the JDT is doing - but they don't even use HTML but only
   * the converted text form. This way they don't have any stylistic problems
   * but they also can't apply there Java hover specific configurations
   * anymore.
   */
  private val annotationHover = new DefaultAnnotationHover( /* showLineNumber */ false) with IAnnotationHoverExtension with HtmlHover {

    override def isIncluded(a: Annotation) =
      isShowInVerticalRuler(a)

    val UnimplementedMembers = """(class .* needs to be abstract, since:\W*it has \d+ unimplemented members\.)[\S\s]*""".r

    val msgFormatter: String => String = {
      case UnimplementedMembers(errorMsg) =>
        convertContentToHtml(errorMsg)
      case str =>
        convertContentToHtml(str)
    }

    override def formatSingleMessage(msg: String) = {
      createHtmlOutput { sb =>
        sb.append(msgFormatter(msg))
      }
    }

    override def formatMultipleMessages(msgs: java.util.List[_]) = {
      createHtmlOutput { sb =>
        import HTMLPrinter._
        import collection.JavaConverters._
        addParagraph(sb, "Multiple markers at this line:")
        startBulletList(sb)
        msgs.asScala foreach (msg => addBullet(sb, msgFormatter(msg.asInstanceOf[String])))
        endBulletList(sb)
      }
    }

    override def canHandleMouseCursor(): Boolean =
      false

    override def getHoverControlCreator(): IInformationControlCreator = new AbstractReusableInformationControlCreator {
      override def doCreateInformationControl(parent: Shell): IInformationControl = {
        if (BrowserInformationControl.isAvailable(parent))
          new BrowserInformationControl(parent, IScalaHover.HoverFontId, /* resizable */ false) with BrowserControlAdditions
        else
          new DefaultInformationControl(parent, /* resizable */ false)
      }
    }

    override def getHoverInfo(sourceViewer: ISourceViewer, lineRange: ILineRange, visibleNumberOfLines: Int): Object =
      getHoverInfo(sourceViewer, lineRange.getStartLine())

    override def getHoverLineRange(sourceViewer: ISourceViewer, lineNumber: Int): ILineRange =
      new LineRange(lineNumber, /* numberOfLines */ 1)

  }

  override def getInformationControlCreator(sourceViewer: ISourceViewer) =
    annotationHover.getHoverControlCreator()

  override def getInformationPresenter(sourceViewer: ISourceViewer) = {
    val p = new InformationPresenter(getInformationControlCreator(sourceViewer))
    val ip = new HoverInformationProvider(Some(IScalaHover(editor)))

    p.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer))
    getConfiguredContentTypes(sourceViewer) foreach (p.setInformationProvider(ip, _))
    p
  }

  override def getOverviewRulerAnnotationHover(sourceViewer: ISourceViewer) = annotationHover

  override def getAnnotationHover(sourceViewer: ISourceViewer) = annotationHover

  /**
   * Creates a reconciler with a delay of 500ms.
   */
  override def getReconciler(sourceViewer: ISourceViewer): IReconciler =
    // the editor is null for the Syntax coloring previewer pane (so no reconciliation)
    Option(editor).map { editor =>
      val s = new CompositeReconcilingStrategy

      val fileInSourcePath = (for {
        f <- EditorUtils.file(editor)
        scalaProject <- IScalaPlugin().asScalaProject(f.getProject)
      } yield
        scalaProject.allSourceFiles().contains(f)).getOrElse(false)


      val baseReconcilers: Array[IReconcilingStrategy] = Array(
        new ScalaOutlineReconcilingStrategy(editor.asInstanceOf[OutlinePageEditorExtension]),
        new SpellingReconcileStrategy(
          editor,
          editor.getViewer(),
          new SpellingService(EditorsUI.getPreferenceStore(), new ScalaSpellingEngine),
          ScalaPlugin().scalaSourceFileContentType,
          EditorsUI.getPreferenceStore()))

      s.setReconcilingStrategies(
        if (fileInSourcePath)
          new ScalaReconcilingStrategy(editor) +: baseReconcilers
        else
          baseReconcilers)

      val reconciler = new ScalaReconciler(editor, s, isIncremental = false)
      reconciler.setDelay(500)
      reconciler.setProgressMonitor(new NullProgressMonitor())
      reconciler
    }.orNull

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
    compilationUnit.map { scu =>
      val hover = IScalaHover(editor)
      ScalaHoverDebugOverrideExtensionPoint.hoverFor(scu).getOrElse(hover)
    }.getOrElse(new DefaultTextHover(sv))

  override def getHyperlinkDetectors(sv: ISourceViewer): Array[IHyperlinkDetector] = {
    val detectors = List(DeclarationHyperlinkDetector(), ImplicitHyperlinkDetector(), new URLHyperlinkDetector())
    if (editor != null)
      detectors.foreach { d => d.setContext(editor) }

    detectors.toArray
  }

  private def getTypeRoot: Option[ITypeRoot] = Option(editor) map { editor =>
    val input = editor.getEditorInput
    val provider = editor.getDocumentProvider

    (provider, input) match {
      case (icudp: ICompilationUnitDocumentProvider, _) => icudp getWorkingCopy input
      case (_, icfei: IClassFileEditorInput) => icfei.getClassFile
      case _ => null
    }
  }

  private def compilationUnit: Option[ScalaCompilationUnit] =
    getTypeRoot collect { case scu: ScalaCompilationUnit => scu }

  private def getProject: IJavaProject =
    getTypeRoot.map(_.asInstanceOf[IJavaElement].getJavaProject).orNull

  /**
   * Replica of JavaSourceViewerConfiguration#getAutoEditStrategies that returns
   * a ScalaAutoIndentStrategy instead of a JavaAutoIndentStrategy.
   *
   * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getAutoEditStrategies(org.eclipse.jface.text.source.ISourceViewer, java.lang.String)
   *
   * @note The usage of this method in Scala IDE is deprecated. We use auto
   * edits instead. No new auto edit strategies should be added but the existing
   * one should be converted to auto edits.
   */
  override def getAutoEditStrategies(sourceViewer: ISourceViewer, contentType: String): Array[IAutoEditStrategy] = {
    def prefProvider = new JdtPreferenceProvider(getProject)
    val partitioning = getConfiguredDocumentPartitioning(sourceViewer)

    contentType match {
      case IJavaPartitions.JAVA_DOC | IJavaPartitions.JAVA_MULTI_LINE_COMMENT | ScalaPartitions.SCALADOC_CODE_BLOCK =>
        Array(new CommentAutoIndentStrategy(combinedPrefStore, partitioning))

      case ScalaPartitions.SCALA_MULTI_LINE_STRING =>
        Array(
          new MultiLineStringAutoIndentStrategy(partitioning, combinedPrefStore),
          new MultiLineStringAutoEditStrategy(partitioning, combinedPrefStore))

      case IJavaPartitions.JAVA_STRING =>
        Array(
          new StringAutoEditStrategy(partitioning, combinedPrefStore))

      case IJavaPartitions.JAVA_CHARACTER | IDocument.DEFAULT_CONTENT_TYPE =>
        Array(
          new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, prefProvider),
          new AutoIndentStrategy(combinedPrefStore),
          new LiteralAutoEditStrategy(combinedPrefStore))

      case _ =>
        Array(
          new ScalaAutoIndentStrategy(partitioning, getProject, sourceViewer, prefProvider),
          new AutoIndentStrategy(combinedPrefStore))
    }
  }

  override def getContentFormatter(sourceViewer: ISourceViewer): IContentFormatter = {
    val formatter = new MultiPassContentFormatter(getConfiguredDocumentPartitioning(sourceViewer), IDocument.DEFAULT_CONTENT_TYPE)
    formatter.setMasterStrategy(new ScalaFormattingStrategy(editor))
    formatter
  }

  override def handlePropertyChangeEvent(event: PropertyChangeEvent): Unit = {
    super.handlePropertyChangeEvent(event)
    codeHighlightingScanners.values foreach (_ adaptToPreferenceChange event)
  }

  override def propertyChange(event: PropertyChangeEvent): Unit = {
    handlePropertyChangeEvent(event)
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
