package org.scalaide.ui.internal.editor

import java.util.ResourceBundle
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.SynchronizedBuffer
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.SelectionHistory
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectHistoryAction
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectionAction
import org.eclipse.jdt.internal.ui.text.ContentAssistPreference
import org.eclipse.jdt.internal.ui.text.SmartBackspaceManager
import org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IContributionItem
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.action.Separator
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocumentExtension4
import org.eclipse.jface.text.ITextOperationTarget
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.ITextViewerExtension
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.jface.text.contentassist.IContentAssistant
import org.eclipse.jface.text.information.InformationPresenter
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.source.IOverviewRuler
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.source.IVerticalRuler
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.viewers.ISelection
import org.eclipse.swt.custom.VerifyKeyListener
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.IWorkbenchCommandConstants
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.texteditor.IAbstractTextEditorHelpContextIds
import org.eclipse.ui.texteditor.ITextEditorActionConstants
import org.eclipse.ui.texteditor.TextOperationAction
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.decorators.markoccurrences.Occurrences
import org.scalaide.core.internal.decorators.markoccurrences.ScalaOccurrencesFinder
import org.scalaide.core.internal.extensions.SemanticHighlightingParticipants
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.logging.HasLogger
import org.scalaide.refactoring.internal.OrganizeImports
import org.scalaide.refactoring.internal.RefactoringHandler
import org.scalaide.refactoring.internal.RefactoringMenu
import org.scalaide.refactoring.internal.source.GenerateHashcodeAndEquals
import org.scalaide.refactoring.internal.source.IntroduceProductNTrait
import org.scalaide.ui.internal.actions
import org.scalaide.ui.internal.editor.autoedits._
import org.scalaide.ui.internal.editor.decorators.semantichighlighting.TextPresentationEditorHighlighter
import org.scalaide.ui.internal.editor.decorators.semantichighlighting.TextPresentationHighlighter
import org.scalaide.ui.internal.editor.hover.FocusedControlCreator
import org.scalaide.ui.internal.preferences.EditorPreferencePage
import org.scalaide.util.Utils
import org.scalaide.util.internal.eclipse.AnnotationUtils.RichModel
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.util.ui.DisplayThread
import org.scalaide.ui.editor.hover.IScalaHover

class ScalaSourceFileEditor extends CompilationUnitEditor with ScalaCompilationUnitEditor with HasLogger { self =>
  import ScalaSourceFileEditor._

  private var occurrenceAnnotations: Set[Annotation] = Set()
  private var occurrencesFinder: ScalaOccurrencesFinder = _
  private var occurencesFinderInstalled = false

  private val reconcilingListeners: ReconcilingListeners = new ScalaSourceFileEditor.ReconcilingListeners

  private lazy val selectionListener = new ISelectionListener() {
    override def selectionChanged(part: IWorkbenchPart, selection: ISelection) {
      selection match {
        case textSel: ITextSelection => requireOccurrencesUpdate(textSel)
        case _ =>
      }
    }
  }

  private lazy val tpePresenter = {
    val infoPresenter = new InformationPresenter(new FocusedControlCreator(IScalaHover.HoverFontId))
    infoPresenter.install(getSourceViewer)
    infoPresenter.setInformationProvider(actions.TypeOfExpressionProvider, IDocument.DEFAULT_CONTENT_TYPE)
    infoPresenter
  }

  /**
   * Contains references to all extensions provided by the extension point
   * `org.scala-ide.sdt.core.semanticHighlightingParticipants`.
   *
   * These extensions are mainly provided by users, therefore any accesss need
   * to be wrapped in a safe runner.
   */
  private lazy val semanticHighlightingParticipants = new IJavaReconcilingListener {

    def nameOf[A](a: A) = a.getClass().getName()

    val exts = getSourceViewer() match {
      case jsv: JavaSourceViewer => SemanticHighlightingParticipants.extensions flatMap { ext =>
        EclipseUtils.withSafeRunner(s"Error occurred while creating semantic action of '${nameOf(ext)}'.") {
          ext.participant(jsv)
        }
      }
    }

    override def aboutToBeReconciled() = ()
    override def reconciled(ast: CompilationUnit, forced: Boolean, progressMonitor: IProgressMonitor) = {
      self.getInteractiveCompilationUnit() match {
        case scu: ScalaCompilationUnit => exts foreach { ext =>
          EclipseUtils.withSafeRunner(s"Error occurred while executing '${nameOf(ext)}'.") {
            ext(scu)
          }
        }
        case _ =>
      }
    }
  }

  setPartName("Scala Editor")
  setDocumentProvider(ScalaPlugin().documentProvider)

  override protected def createActions() {
    super.createActions()

    val cutAction = new TextOperationAction(bundleForConstructedKeys, "Editor.Cut.", this, ITextOperationTarget.CUT) //$NON-NLS-1$
    cutAction.setHelpContextId(IAbstractTextEditorHelpContextIds.CUT_ACTION)
    cutAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_CUT)
    setAction(ITextEditorActionConstants.CUT, cutAction)

    val copyAction = new TextOperationAction(bundleForConstructedKeys, "Editor.Copy.", this, ITextOperationTarget.COPY, true) //$NON-NLS-1$
    copyAction.setHelpContextId(IAbstractTextEditorHelpContextIds.COPY_ACTION)
    copyAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_COPY)
    setAction(ITextEditorActionConstants.COPY, copyAction)

    val pasteAction = new TextOperationAction(bundleForConstructedKeys, "Editor.Paste.", this, ITextOperationTarget.PASTE) //$NON-NLS-1$
    pasteAction.setHelpContextId(IAbstractTextEditorHelpContextIds.PASTE_ACTION)
    pasteAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_PASTE)
    setAction(ITextEditorActionConstants.PASTE, pasteAction)

    val selectionHistory = new SelectionHistory(this)

    val historyAction = new StructureSelectHistoryAction(this, selectionHistory)
    historyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELECT_LAST)
    setAction(StructureSelectionAction.HISTORY, historyAction)
    selectionHistory.setHistoryAction(historyAction)

    // disable Java indent logic, which is otherwise invoked when the tab key is entered
    setAction("IndentOnTab", null)

    val selectEnclosingAction = new actions.ScalaStructureSelectEnclosingAction(this, selectionHistory)
    selectEnclosingAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELECT_ENCLOSING)
    setAction(StructureSelectionAction.ENCLOSING, selectEnclosingAction)

    val openAction = new Action {
      private def scalaCompilationUnit: Option[ScalaCompilationUnit] =
        Option(getInteractiveCompilationUnit) map (_.asInstanceOf[ScalaCompilationUnit])

      override def run {
        scalaCompilationUnit foreach { scu =>
          scu.followDeclaration(ScalaSourceFileEditor.this, getSelectionProvider.getSelection.asInstanceOf[ITextSelection])
        }
      }
    }
    openAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_EDITOR)
    setAction("OpenEditor", openAction)
  }

  /**
   * The tabs to spaces converter of the editor is not partition aware,
   * therefore we disable it here. There is an auto edit strategy configured in
   * the [[ScalaSourceViewerConfiguration]] that handles the conversion for each
   * partition separately.
   */
  override def isTabsToSpacesConversionEnabled(): Boolean =
    false

  override def createJavaSourceViewer(
      parent: Composite, verticalRuler: IVerticalRuler, overviewRuler: IOverviewRuler,
      isOverviewRulerVisible: Boolean, styles: Int, store: IPreferenceStore) =
    new ScalaSourceViewer(
        parent, verticalRuler, overviewRuler,
        isOverviewRulerVisible, styles, store)

  override protected def initializeKeyBindingScopes() {
    setKeyBindingScopes(Array(SCALA_EDITOR_SCOPE))
  }

  override def updateOccurrenceAnnotations(selection: ITextSelection, astRoot: CompilationUnit): Unit = {
    requireOccurrencesUpdate(selection)
  }

  /** Returns the annotation model of the current document provider.
   */
  private def getAnnotationModelOpt: Option[IAnnotationModel] = {
    for {
      documentProvider <- Option(getDocumentProvider)
      annotationModel <- Option(documentProvider.getAnnotationModel(getEditorInput))
    } yield annotationModel
  }

  private def performOccurrencesUpdate(selection: ITextSelection, documentLastModified: Long) {
    val annotations = getAnnotations(selection, documentLastModified)
    for(annotationModel <- getAnnotationModelOpt) annotationModel.withLock {
      annotationModel.replaceAnnotations(occurrenceAnnotations, annotations)
      occurrenceAnnotations = annotations.keySet
    }
  }

  private def getAnnotations(selection: ITextSelection, documentLastModified: Long): Map[Annotation, Position] = {
    val region = EditorUtils.textSelection2region(selection)
    val occurrences = occurrencesFinder.findOccurrences(region, documentLastModified)
    for {
      Occurrences(name, locations) <- occurrences.toList
      location <- locations
      annotation = new Annotation(OCCURRENCE_ANNOTATION, false, "Occurrence of '" + name + "'")
      position = new Position(location.getOffset, location.getLength)
    } yield annotation -> position
  }.toMap

  private def requireOccurrencesUpdate(selection: ITextSelection) {

    if (selection.getLength < 0 || selection.getOffset < 0)
      return

    if (getDocumentProvider == null || !isActiveEditor)
      return

    val lastModified = getSourceViewer.getDocument match {
      case document: IDocumentExtension4 =>
        document.getModificationStamp
      case _ => return
    }

    EclipseUtils.scheduleJob("Updating occurrence annotations", priority = Job.DECORATE) { monitor =>
      val fileName = getInteractiveCompilationUnit.file.name
      Utils.debugTimed("Time elapsed for \"updateOccurrences\" in source " + fileName) {
        performOccurrencesUpdate(selection, lastModified)
      }
      Status.OK_STATUS
    }
  }

  override def doSelectionChanged(selection: ISelection) {
    super.doSelectionChanged(selection)
    val selectionProvider = getSelectionProvider
    if (selectionProvider != null)
      selectionProvider.getSelection match {
        case textSel: ITextSelection => requireOccurrencesUpdate(textSel)
        case _ =>
      }
  }

  override def installOccurrencesFinder(forceUpdate: Boolean) {
    if (!occurencesFinderInstalled) {
      super.installOccurrencesFinder(forceUpdate)
      getEditorSite.getPage.addPostSelectionListener(selectionListener)
      occurencesFinderInstalled = true
    }
  }

  override def uninstallOccurrencesFinder() {
    occurencesFinderInstalled = false
    getEditorSite.getPage.removePostSelectionListener(selectionListener)
    super.uninstallOccurrencesFinder
    removeScalaOccurrenceAnnotations()
  }

  /** Clear the existing Mark Occurrences annotations.
   */
  def removeScalaOccurrenceAnnotations() {
    for (annotationModel <- getAnnotationModelOpt) annotationModel.withLock {
      annotationModel.replaceAnnotations(occurrenceAnnotations, Map())
      occurrenceAnnotations = Set()
    }
  }

  /** Return the `InformationPresenter` used to display the type of the selected expression.*/
  def typeOfExpressionPresenter: InformationPresenter = tpePresenter

  override def editorContextMenuAboutToShow(menu: org.eclipse.jface.action.IMenuManager): Unit = {
    super.editorContextMenuAboutToShow(menu)

    def groupMenuItemsByGroupId(items: Seq[IContributionItem]) = {
      // the different groups (as indicated by separators) and
      // contributions in a menu are originally just a flat list
      val groups = items.foldLeft(Nil: List[(String, List[IContributionItem])]) {

        // start a new group
        case (others, group: Separator) => (group.getId, Nil) :: others

        // append contribution to the current group
        case ((group, others) :: rest, element) => (group, element :: others) :: rest

        // the menu does not start with a group, this shouldn't happen, but if
        // it does we just skip this element, so it will stay in the menu.
        case (others, _) => others
      }
      groups.toMap
    }

    def findJdtSourceMenuManager(items: Seq[IContributionItem]) = {
      items.collect {
        case mm: MenuManager if mm.getId == "org.eclipse.jdt.ui.source.menu" => mm
      }
    }

    findJdtSourceMenuManager(menu.getItems) foreach { mm =>

      val groups = groupMenuItemsByGroupId(mm.getItems)

      // these contributions won't work on Scala files, so we remove them
      val blacklist = List("codeGroup", "importGroup", "generateGroup", "externalizeGroup")
      blacklist.flatMap(groups.get).flatten.foreach(mm.remove)

      def action(h: RefactoringHandler, text: String) = new Action {
        setText(text)
        override def run(): Unit = h.perform()
      }

      // and provide our own organize imports instead
      mm.appendToGroup("importGroup", action(new OrganizeImports, "Organize Imports"))

      // add GenerateHashcodeAndEquals and IntroductProductN source generators
      mm.appendToGroup("generateGroup", action(new GenerateHashcodeAndEquals, "Generate hashCode() and equals()..."))
      mm.appendToGroup("generateGroup", action(new IntroduceProductNTrait, "Introduce ProductN trait..."))
    }

    RefactoringMenu.fillContextMenu(menu, this)
  }

  override def createPartControl(parent: org.eclipse.swt.widgets.Composite) {
    super.createPartControl(parent)
    occurrencesFinder = new ScalaOccurrencesFinder(getInteractiveCompilationUnit)
    RefactoringMenu.fillQuickMenu(this)
    reconcilingListeners.addReconcileListener(semanticHighlightingParticipants)

    /*
     * Removes the Java component that provides the "automatically close ..."
     * behavior. The component is accessed with reflection, because
     * [[super.createPartControl]], which defines it, needs to be called.
     */
    def removeBracketInserter() = {
      try {
        val fBracketInserter = classOf[CompilationUnitEditor].getDeclaredField("fBracketInserter")
        fBracketInserter.setAccessible(true)
        sourceViewer.removeVerifyKeyListener(fBracketInserter.get(this).asInstanceOf[VerifyKeyListener])
      } catch {
        case e: NoSuchFieldException =>
          logger.error("The name of field 'fBracketInserter' has changed", e)
      }
    }

    removeBracketInserter()
    getSourceViewer match {
      case sourceViewer: ITextViewerExtension =>
        sourceViewer.prependVerifyKeyListener(new SurroundSelectionStrategy(getSourceViewer))
      case _ =>
    }
  }

  override def handlePreferenceStoreChanged(event: PropertyChangeEvent) = {
    import org.scalaide.core.internal.formatter.FormatterPreferences._
    import scalariform.formatter.preferences._

    val IndentSpacesKey = IndentSpaces.eclipseKey
    val IndentWithTabsKey = IndentWithTabs.eclipseKey

    event.getProperty match {
      case PreferenceConstants.EDITOR_MARK_OCCURRENCES =>
      // swallow the event. We don't want 'mark occurrences' to be linked to the Java editor preference
      case EditorPreferencePage.P_ENABLE_MARK_OCCURRENCES =>
        (event.getNewValue: Any) match {
          case true =>
            installOccurrencesFinder(true)
          case _ =>
            uninstallOccurrencesFinder()
        }

      case IndentSpacesKey | IndentWithTabsKey =>
        val tabWidth = getSourceViewerConfiguration().getTabWidth(sourceViewer)
        sourceViewer.getTextWidget().setTabs(tabWidth)
        updateIndentPrefixes()

      case _ =>

        def executeSuperImplementation() =
          try super.handlePreferenceStoreChanged(event) catch {
            case _: ClassCastException =>
              // I don't know any better than to ignore this exception. It happens
              // because the CompilationUnitEditor assumes that the source viewer
              // is of a different type. And we can't directly call the implementation
              // of the JavaEditor here.
        }

        // This code is also called in the implementation of CompilationUnitEditor,
        // but because this implementation doesn't work anymore (see executeSuperImplementation)
        // we have to do it here.
        sourceViewer match {
          case ssv: ScalaSourceViewer =>
            ssv.contentAssistant match {
              case ca: ContentAssistant =>
                ContentAssistPreference.changeConfiguration(ca, getPreferenceStore(), event)
              case _ =>
            }
          case _ =>
        }

        // those events will trigger an UI change
        if (affectsTextPresentation(event))
          DisplayThread.asyncExec(executeSuperImplementation())
        else
          executeSuperImplementation()

        // whatever event occurs that leads to the creation of the converter,
        // we don't want it. We use auto edits to describe the behavior of
        // tab-space conversions.
        sourceViewer.setTabsToSpacesConverter(null)
    }
  }

  override def isMarkingOccurrences =
    scalaPrefStore.getBoolean(EditorPreferencePage.P_ENABLE_MARK_OCCURRENCES)

  override def createSemanticHighlighter: TextPresentationHighlighter =
    TextPresentationEditorHighlighter(this, semanticHighlightingPreferences, addReconcilingListener _, removeReconcilingListener _)

  override def forceSemanticHighlightingOnInstallment: Boolean = false // relies on the Java reconciler to refresh the highlights

  def addReconcilingListener(listener: IJavaReconcilingListener): Unit =
    reconcilingListeners.addReconcileListener(listener)

  def removeReconcilingListener(listener: IJavaReconcilingListener): Unit =
    reconcilingListeners.removeReconcileListener(listener)

  override def aboutToBeReconciled(): Unit = {
    super.aboutToBeReconciled()
    reconcilingListeners.aboutToBeReconciled()
  }

  override def reconciled(ast: CompilationUnit, forced: Boolean, progressMonitor: IProgressMonitor): Unit = {
    super.reconciled(ast, forced, progressMonitor)
    reconcilingListeners.reconciled(ast, forced, progressMonitor)
  }

  /**
   * This class partly overwrites the implementations in
   * [[org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor.AdaptedSourceViewer]]
   *
   * `AdaptedSourceViewer` couldn't be subclassed because it is not public. The
   * main purpose of this class is to catch a [[org.eclipse.swt.events.VerifyEvent]]
   * and forward it to the auto edit logic.
   */
  class ScalaSourceViewer(
      parent: Composite, verticalRuler: IVerticalRuler, overviewRuler: IOverviewRuler,
      isOverviewRulerVisible: Boolean, styles: Int, store: IPreferenceStore)
        extends JavaSourceViewer(
            parent, verticalRuler, overviewRuler,
            isOverviewRulerVisible, styles, store)
        with AutoEditExtensions {

    def contentAssistant: IContentAssistant = fContentAssistant

    override def sourceViewer: ISourceViewer = this

    override def documentPartitioning: String = getDocumentPartitioning()

    override def updateTextViewer(cursorPos: Int): Unit = {
      val widgetCaret = modelOffset2WidgetOffset(cursorPos)
      setSelectedRange(widgetCaret, 0)
    }

    override def smartBackspaceManager: SmartBackspaceManager =
      self.getAdapter(classOf[SmartBackspaceManager]).asInstanceOf[SmartBackspaceManager]

    /** Calls auto edits and if they produce no changes the super implementation. */
    override def handleVerifyEvent(e: VerifyEvent): Unit = {
      applyVerifyEvent(e, event2ModelRange(e))

      if (e.doit)
        super.handleVerifyEvent(e)
    }

    /** Implementation copied from [[AdaptedSourceViewer]]. */
    override def doOperation(operation: Int): Unit = {
      if (getTextWidget() != null) {
        operation match {
          case ISourceViewer.CONTENTASSIST_PROPOSALS =>
            val msg = fContentAssistant.showPossibleCompletions()
            setStatusLineErrorMessage(msg)

          case ISourceViewer.QUICK_ASSIST =>
            val msg = fQuickAssistAssistant.showPossibleQuickAssists()
            setStatusLineMessage(msg)

          case _ =>
            super.doOperation(operation)
        }
      }
    }

    /** Implementation copied from [[AdaptedSourceViewer]]. */
    override def requestWidgetToken(requester: org.eclipse.jface.text.IWidgetTokenKeeper): Boolean =
      if (PlatformUI.getWorkbench().getHelpSystem().isContextHelpDisplayed())
        false
      else
        super.requestWidgetToken(requester)

    /** Implementation copied from [[AdaptedSourceViewer]]. */
    override def requestWidgetToken(requester: org.eclipse.jface.text.IWidgetTokenKeeper, priority: Int): Boolean =
      if (PlatformUI.getWorkbench().getHelpSystem().isContextHelpDisplayed())
        false
      else
        super.requestWidgetToken(requester, priority)
  }

}

object ScalaSourceFileEditor {
  private val EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS = "org.eclipse.ui.texteditor.ConstructedEditorMessages"
  private val bundleForConstructedKeys = ResourceBundle.getBundle(EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS)

  private val SCALA_EDITOR_SCOPE = "scala.tools.eclipse.scalaEditorScope"

  private val OCCURRENCE_ANNOTATION = "org.eclipse.jdt.ui.occurrences"

  /** A thread-safe object for keeping track of Java reconciling listeners.*/
  private class ReconcilingListeners extends IJavaReconcilingListener {
    private val reconcilingListeners = new ArrayBuffer[IJavaReconcilingListener] with SynchronizedBuffer[IJavaReconcilingListener]

    /** Return a snapshot of the currently registered `reconcilingListeners`. This is useful to avoid concurrency hazards when iterating on the `reconcilingListeners`. */
    private def currentReconcilingListeners: List[IJavaReconcilingListener] = reconcilingListeners.toList

    override def aboutToBeReconciled(): Unit =
      for (listener <- currentReconcilingListeners) listener.aboutToBeReconciled()

    override def reconciled(ast: CompilationUnit, forced: Boolean, progressMonitor: IProgressMonitor): Unit =
      for (listener <- currentReconcilingListeners) listener.reconciled(ast, forced, progressMonitor)

    def addReconcileListener(listener: IJavaReconcilingListener): Unit = reconcilingListeners += listener

    def removeReconcileListener(listener: IJavaReconcilingListener): Unit = reconcilingListeners -= listener
  }
}
