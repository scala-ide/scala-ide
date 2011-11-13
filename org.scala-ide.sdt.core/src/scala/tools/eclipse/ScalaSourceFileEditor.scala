/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.util.IPropertyChangeListener
import java.util.ResourceBundle
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions._
import org.eclipse.jdt.internal.ui.search.IOccurrencesFinder._
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration
import org.eclipse.jface.action._
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text._
import org.eclipse.jface.text.source.{ Annotation, IAnnotationModelExtension, SourceViewerConfiguration }
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.{ IWorkbenchPart, ISelectionListener, IFileEditorInput }
import org.eclipse.ui.editors.text.{ ForwardingDocumentProvider, TextFileDocumentProvider }
import org.eclipse.ui.texteditor.{ IAbstractTextEditorHelpContextIds, ITextEditorActionConstants, IUpdate, IWorkbenchActionDefinitionIds, TextOperationAction }
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.tools.eclipse.javaelements.{ ScalaSourceFile, ScalaCompilationUnit }
import scala.tools.eclipse.markoccurrences.{ ScalaOccurrencesFinder, Occurrences }
import scala.tools.eclipse.semicolon._
import scala.tools.eclipse.util.Utils

class ScalaSourceFileEditor extends CompilationUnitEditor with ScalaEditor {

  import ScalaSourceFileEditor._

  setPartName("Scala Editor")

  override protected def createActions() {
    super.createActions()

    val cutAction = new TextOperationAction(bundleForConstructedKeys, "Editor.Cut.", this, ITextOperationTarget.CUT) //$NON-NLS-1$
    cutAction.setHelpContextId(IAbstractTextEditorHelpContextIds.CUT_ACTION)
    cutAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.CUT)
    setAction(ITextEditorActionConstants.CUT, cutAction)

    val copyAction = new TextOperationAction(bundleForConstructedKeys, "Editor.Copy.", this, ITextOperationTarget.COPY, true) //$NON-NLS-1$
    copyAction.setHelpContextId(IAbstractTextEditorHelpContextIds.COPY_ACTION)
    copyAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.COPY)
    setAction(ITextEditorActionConstants.COPY, copyAction)

    val pasteAction = new TextOperationAction(bundleForConstructedKeys, "Editor.Paste.", this, ITextOperationTarget.PASTE) //$NON-NLS-1$
    pasteAction.setHelpContextId(IAbstractTextEditorHelpContextIds.PASTE_ACTION)
    pasteAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.PASTE)
    setAction(ITextEditorActionConstants.PASTE, pasteAction)

    val selectionHistory = new SelectionHistory(this)

    val historyAction = new StructureSelectHistoryAction(this, selectionHistory)
    historyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELECT_LAST)
    setAction(StructureSelectionAction.HISTORY, historyAction)
    selectionHistory.setHistoryAction(historyAction)

    val selectEnclosingAction = new ScalaStructureSelectEnclosingAction(this, selectionHistory)
    selectEnclosingAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELECT_ENCLOSING)
    setAction(StructureSelectionAction.ENCLOSING, selectEnclosingAction)

    val showInferredSemicolons = new ShowInferredSemicolonsAction(ShowInferredSemicolonsBundle.PREFIX, this, ScalaPlugin.plugin.getPreferenceStore)
    showInferredSemicolons.setActionDefinitionId(ShowInferredSemicolonsAction.ACTION_DEFINITION_ID)
    setAction(ShowInferredSemicolonsAction.ACTION_ID, showInferredSemicolons)

    val openAction = new Action {
      override def run {
        Option(getInputJavaElement) map (_.asInstanceOf[ScalaCompilationUnit]) foreach { scu =>
          scu.followReference(ScalaSourceFileEditor.this, getSelectionProvider.getSelection.asInstanceOf[ITextSelection])
        }
      }
    }
    openAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_EDITOR)
    setAction("OpenEditor", openAction)
  }

  override protected def initializeKeyBindingScopes() {
    setKeyBindingScopes(Array(SCALA_EDITOR_SCOPE))
  }

  override def createJavaSourceViewerConfiguration: JavaSourceViewerConfiguration =
    new ScalaSourceViewerConfiguration(getPreferenceStore, ScalaPlugin.plugin.getPreferenceStore, this)

  override def setSourceViewerConfiguration(configuration: SourceViewerConfiguration) {
    super.setSourceViewerConfiguration(
      configuration match {
        case svc: ScalaSourceViewerConfiguration => svc
        case _ => new ScalaSourceViewerConfiguration(getPreferenceStore, ScalaPlugin.plugin.getPreferenceStore, this)
      })
  }

  private[eclipse] def sourceViewer = getSourceViewer.asInstanceOf[JavaSourceViewer]

  private var occurrenceAnnotations: Array[Annotation] = _

  override def updateOccurrenceAnnotations(selection: ITextSelection, astRoot: CompilationUnit) {
    askForOccurrencesUpdate(selection, astRoot)
    super.updateOccurrenceAnnotations(selection, astRoot)
  }

  private def performOccurrencesUpdate(selection: ITextSelection, astRoot: CompilationUnit) {
    import ScalaPlugin.{ plugin => thePlugin }

    val documentProvider = getDocumentProvider
    if (documentProvider eq null)
      return

    //  TODO: find out why this code does a cast to IAdaptable before calling getAdapter 
    val adaptable = getEditorInput.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement])
    // logger.info("adaptable: " + adaptable.getClass + " : " + adaptable.toString)

    adaptable match {
      case scalaSourceFile: ScalaSourceFile =>
        val annotations = getAnnotations(selection, scalaSourceFile)
        val annotationModel = documentProvider.getAnnotationModel(getEditorInput)
        if (annotationModel eq null)
          return
        annotationModel.asInstanceOf[ISynchronizable].getLockObject() synchronized {
          val annotationModelExtension = annotationModel.asInstanceOf[IAnnotationModelExtension]
          annotationModelExtension.replaceAnnotations(occurrenceAnnotations, annotations)
          occurrenceAnnotations = annotations.keySet.toArray
        }

      case _ =>
        // TODO: pop up a dialog explaining what needs to be fixed or fix it ourselves
        Utils tryExecute (adaptable.asInstanceOf[ScalaSourceFile], // trigger the exception, so as to get a diagnostic stack trace 
          msgIfError = Some("Could not recompute occurrence annotations: configuration problem"))
    }
  }

  private def getAnnotations(selection: ITextSelection, scalaSourceFile: ScalaSourceFile): mutable.Map[Annotation, Position] = {
    val annotations = for {
      Occurrences(name, locations) <- new ScalaOccurrencesFinder(scalaSourceFile, selection.getOffset, selection.getLength).findOccurrences.toList
      location <- locations
      val offset = location.getOffset
      val length = location.getLength
      val position = new Position(location.getOffset, location.getLength)
    } yield new Annotation(OCCURRENCE_ANNOTATION, false, "Occurrence of '" + name + "'") -> position
    mutable.Map(annotations: _*)
  }

  def askForOccurrencesUpdate(selection: ITextSelection, astRoot: CompilationUnit) {

    if (selection.getLength < 0 || selection.getOffset < 0) return

    import org.eclipse.core.runtime.jobs.Job
    import org.eclipse.core.runtime.IProgressMonitor
    import org.eclipse.core.runtime.{ IStatus, Status }

    val job = new Job("updateOccurrenceAnnotations") {
      def run(monitor: IProgressMonitor): IStatus = {
        performOccurrencesUpdate(selection, astRoot)
        Status.OK_STATUS
      }
    }

    job.setPriority(Job.INTERACTIVE)
    job.schedule()
  }

  override def doSelectionChanged(selection: ISelection) {
    super.doSelectionChanged(selection)
    val selectionProvider = getSelectionProvider
    if (selectionProvider != null)
      selectionProvider.getSelection match {
        case textSel: ITextSelection => askForOccurrencesUpdate(textSel, null)
        case _ =>
      }
  }

  lazy val selectionListener = new ISelectionListener() {
    def selectionChanged(part: IWorkbenchPart, selection: ISelection) {
      selection match {
        case textSel: ITextSelection => askForOccurrencesUpdate(textSel, null)
        case _ =>
      }
    }
  }

  override def installOccurrencesFinder(forceUpdate: Boolean) {
    super.installOccurrencesFinder(forceUpdate)
    getEditorSite.getPage.addPostSelectionListener(selectionListener)
  }

  override def uninstallOccurrencesFinder() {
    getEditorSite.getPage.removePostSelectionListener(selectionListener)
    super.uninstallOccurrencesFinder
  }

  private val preferenceListener = new IPropertyChangeListener() {
    def propertyChange(event: PropertyChangeEvent) {
      handlePreferenceStoreChanged(event)
    }
  }
  ScalaPlugin.plugin.getPreferenceStore.addPropertyChangeListener(preferenceListener)

  override def dispose() {
    super.dispose()
    ScalaPlugin.plugin.getPreferenceStore.removePropertyChangeListener(preferenceListener)
  }

  override def editorContextMenuAboutToShow(menu: org.eclipse.jface.action.IMenuManager): Unit = {
    super.editorContextMenuAboutToShow(menu)

    def groupMenuItemsByGroupId(items: Seq[IContributionItem]) = {
      // the different groups (as indicated by separators) and 
      // contributions in a menu are originally just a flat list
      items.foldLeft(Nil: List[(String, List[IContributionItem])]) {

        // start a new group
        case (others, group: Separator) => (group.getId, Nil) :: others

        // append contribution to the current group
        case ((group, others) :: rest, element) => (group, element :: others) :: rest

        // the menu does not start with a group, this shouldn't happen, but if
        // it does we just skip this element, so it will stay in the menu.
        case (others, _) => others
      } toMap
    }

    def findJdtSourceMenuManager(items: Seq[IContributionItem]) = {
      items.collect {
        case mm: MenuManager if mm.getId == "org.eclipse.jdt.ui.source.menu" => mm
      }
    }

    findJdtSourceMenuManager(menu.getItems) foreach { mm =>

      val groups = groupMenuItemsByGroupId(mm.getItems)

      // these two contributions won't work on Scala files, so we remove them
      val blacklist = List("codeGroup", "importGroup")

      // and provide our own organize imports instead
      mm.appendToGroup("importGroup", new refactoring.OrganizeImportsAction { setText("Organize Imports") })

      blacklist.flatMap(groups.get).flatten.foreach(mm.remove)
    }

    refactoring.RefactoringMenu.fillContextMenu(menu, this)
  }

  override def createPartControl(parent: org.eclipse.swt.widgets.Composite) {
    super.createPartControl(parent)
    refactoring.RefactoringMenu.fillQuickMenu(this)
  }

  override def handlePreferenceStoreChanged(event: PropertyChangeEvent) =
    event.getProperty match {
      case ShowInferredSemicolonsAction.PREFERENCE_KEY =>
        getAction(ShowInferredSemicolonsAction.ACTION_ID).asInstanceOf[IUpdate].update()
      case _ =>
        super.handlePreferenceStoreChanged(event)
    }

}

object ScalaSourceFileEditor {

  private val EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS = "org.eclipse.ui.texteditor.ConstructedEditorMessages"
  private val bundleForConstructedKeys = ResourceBundle.getBundle(EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS)

  private val SCALA_EDITOR_SCOPE = "scala.tools.eclipse.scalaEditorScope"

  private val OCCURRENCE_ANNOTATION = "org.eclipse.jdt.ui.occurrences"
}
