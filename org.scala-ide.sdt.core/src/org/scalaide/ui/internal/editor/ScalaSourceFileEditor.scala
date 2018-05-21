package org.scalaide.ui.internal.editor

import java.util.ResourceBundle

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.dom.AST
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
import org.eclipse.jface.text.ITextOperationTarget
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.jface.text.contentassist.IContentAssistant
import org.eclipse.jface.text.information.InformationPresenter
import org.eclipse.jface.text.source.IOverviewRuler
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.source.IVerticalRuler
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.custom.VerifyKeyListener
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.IWorkbenchCommandConstants
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.part.WorkbenchPart
import org.eclipse.ui.texteditor.IAbstractTextEditorHelpContextIds
import org.eclipse.ui.texteditor.ITextEditorActionConstants
import org.eclipse.ui.texteditor.TextOperationAction
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.extensions.SemanticHighlightingParticipants
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.logging.HasLogger
import org.scalaide.refactoring.internal.OrganizeImports
import org.scalaide.refactoring.internal.RefactoringHandler
import org.scalaide.refactoring.internal.RefactoringMenu
import org.scalaide.refactoring.internal.source.GenerateHashcodeAndEquals
import org.scalaide.refactoring.internal.source.IntroduceProductNTrait
import org.scalaide.ui.editor.hover.IScalaHover
import org.scalaide.ui.internal.actions
import org.scalaide.ui.internal.editor.decorators.semantichighlighting.TextPresentationEditorHighlighter
import org.scalaide.ui.internal.editor.decorators.semantichighlighting.TextPresentationHighlighter
import org.scalaide.ui.internal.editor.hover.FocusedControlCreator
import org.scalaide.ui.internal.editor.outline.OutlinePageEditorExtension
import org.scalaide.ui.internal.preferences.EditorPreferencePage
import org.scalaide.util.Utils
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.util.ui.DisplayThread

class ScalaSourceFileEditor
    extends CompilationUnitEditor
    with ScalaCompilationUnitEditor
    with MarkOccurrencesEditorExtension
    with OutlinePageEditorExtension
    with HasLogger { self ⇒

  import ScalaSourceFileEditor._

  private val reconcilingListeners: ReconcilingListeners = new ScalaSourceFileEditor.ReconcilingListeners

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
   * These extensions are mainly provided by users, therefore any accesses need
   * to be wrapped in a safe runner.
   */
  private lazy val semanticHighlightingParticipants = new IJavaReconcilingListener {

    def nameOf[A](a: A) = a.getClass().getName()

    val exts = getSourceViewer() match {
      case jsv: JavaSourceViewer => SemanticHighlightingParticipants.extensions flatMap { ext =>
        EclipseUtils.withSafeRunner(s"Error occurred while creating semantic action of '${nameOf(ext)}'") {
          ext.participant(jsv)
        }
      }
    }

    override def aboutToBeReconciled() = ()
    override def reconciled(ast: CompilationUnit, forced: Boolean, progressMonitor: IProgressMonitor) = {
      getInteractiveCompilationUnit() match {
        case scu: ScalaCompilationUnit => exts foreach { ext =>
          EclipseUtils.withSafeRunner(s"Error occurred while executing '${nameOf(ext)}'") {
            ext(scu)
          }
        }
        case _ =>
      }
    }
  }

  setPartName("Scala Editor")
  setDocumentProvider(ScalaPlugin().documentProvider)

  override def dispose() = {
    super.dispose()
    semanticHighlightingParticipants.exts foreach (_.dispose())
  }

  override protected def createActions(): Unit = {
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
        Option(getInteractiveCompilationUnit) map (ScalaCompilationUnit.castFrom)

      override def run(): Unit = {
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
   * therefore we disable it here. There is a save action available that handles
   * the conversion.
   */
  override def isTabsToSpacesConversionEnabled(): Boolean =
    false

  override def createJavaSourceViewer(
      parent: Composite, verticalRuler: IVerticalRuler, overviewRuler: IOverviewRuler,
      isOverviewRulerVisible: Boolean, styles: Int, store: IPreferenceStore) =
    new ScalaSourceViewer(
        parent, verticalRuler, overviewRuler,
        isOverviewRulerVisible, styles, store)

  override protected def initializeKeyBindingScopes(): Unit = {
    setKeyBindingScopes(Array(SCALA_EDITOR_SCOPE))
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

    def removeBrokenMenuItems(): Unit = {
      def isBroken(item: IContributionItem) = item.getId match {
        case "OpenTypeHierarchy" => true
        case IJavaEditorActionDefinitionIds.OPEN_HIERARCHY => true
        case _ => false
      }

      menu.getItems.toList.foreach { item =>
        if (isBroken(item)) {
          menu.remove(item)
        }
      }
    }

    def fixBrokenGroups(): Unit = {
      val items = menu.getItems

      // hide quick fix entry from menu since it invokes JDT quick fixes
      items.find(_.getId == "QuickAssist").foreach(_.setVisible(false))

      findJdtSourceMenuManager(items) foreach { mm =>

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
    }

    removeBrokenMenuItems()
    fixBrokenGroups()
    RefactoringMenu.fillContextMenu(menu, this)
  }

  override def createPartControl(parent: org.eclipse.swt.widgets.Composite): Unit = {
    super.createPartControl(parent)
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
        // we don't want it. We use a save action to describe the behavior of
        // tab to space conversions.
        sourceViewer.setTabsToSpacesConverter(null)
    }
  }

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
    getInteractiveCompilationUnit() match {
      case unit: ScalaSourceFile => // only do this for source files, not class files
        // trigger Java structure building
        // this should be fast, the whole unit was fully type-checked
        Utils.debugTimed("makeConsistent after reconcile") {
          unit.makeConsistent(
            AST.JLS10, // anything other than NO_AST would work
            true, // resolve bindings
            0, // don't perform statements recovery
            null, // don't collect problems but report them
            progressMonitor)
        }
      case _ =>
    }
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
      setSelectedRange(cursorPos, 0)
    }

    override def smartBackspaceManager: SmartBackspaceManager =
      (self: WorkbenchPart).getAdapter(classOf[SmartBackspaceManager]).asInstanceOf[SmartBackspaceManager]

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

  private[editor] val OCCURRENCE_ANNOTATION = "org.eclipse.jdt.ui.occurrences"

  /** A thread-safe object for keeping track of Java reconciling listeners.*/
  private class ReconcilingListeners extends IJavaReconcilingListener {
    import java.util.concurrent.ConcurrentLinkedQueue

    private val reconcilingListeners = new ConcurrentLinkedQueue[IJavaReconcilingListener]()

    /** Return a snapshot of the currently registered `reconcilingListeners`. This is useful to avoid concurrency hazards when iterating on the `reconcilingListeners`. */
    private def currentReconcilingListeners = {
      import scala.collection.JavaConverters._
      reconcilingListeners.asScala.toList
    }

    override def aboutToBeReconciled(): Unit =
      for (listener <- currentReconcilingListeners) listener.aboutToBeReconciled()

    override def reconciled(ast: CompilationUnit, forced: Boolean, progressMonitor: IProgressMonitor): Unit =
      for (listener <- currentReconcilingListeners) listener.reconciled(ast, forced, progressMonitor)

    def addReconcileListener(listener: IJavaReconcilingListener): Unit = reconcilingListeners.add(listener)

    def removeReconcileListener(listener: IJavaReconcilingListener): Unit = reconcilingListeners.remove(listener)
  }
}
