
/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.util.ResourceBundle

import scala.Option.option2Iterable
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.markoccurrences.Occurrences
import scala.tools.eclipse.markoccurrences.ScalaOccurrencesFinder
import scala.tools.eclipse.semantichighlighting.SemanticHighlightingAnnotations
import scala.tools.eclipse.semicolon.ShowInferredSemicolonsAction
import scala.tools.eclipse.semicolon.ShowInferredSemicolonsBundle
import scala.tools.eclipse.ui.SurroundSelectionStrategy
import scala.tools.eclipse.util.EditorUtils
import scala.tools.eclipse.util.RichAnnotationModel.annotationModel2RichAnnotationModel
import scala.tools.eclipse.util.SWTUtils
import scala.tools.eclipse.util.SWTUtils.fnToPropertyChangeListener
import scala.tools.eclipse.util.Utils

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.SelectionHistory
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectHistoryAction
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectionAction
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IContributionItem
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.action.Separator
import org.eclipse.jface.text.AbstractReusableInformationControlCreator
import org.eclipse.jface.text.DefaultInformationControl
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocumentExtension4
import org.eclipse.jface.text.ITextOperationTarget
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.ITextViewerExtension
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.information.InformationPresenter
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.AnnotationPainter
import org.eclipse.jface.text.source.IAnnotationAccess
import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.source.IOverviewRuler
import org.eclipse.jface.text.source.ISharedTextColors
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.source.SourceViewerConfiguration
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.viewers.ISelection
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.ui.texteditor.IAbstractTextEditorHelpContextIds
import org.eclipse.ui.texteditor.ITextEditorActionConstants
import org.eclipse.ui.texteditor.IUpdate
import org.eclipse.ui.texteditor.IWorkbenchActionDefinitionIds
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport
import org.eclipse.ui.texteditor.TextOperationAction


class ScalaSourceFileEditor extends CompilationUnitEditor with ScalaEditor { self =>
  import ScalaSourceFileEditor._

  private var occurrenceAnnotations: Set[Annotation] = Set()
  private var occurrencesFinder: ScalaOccurrencesFinder = _  
  private val preferenceListener: IPropertyChangeListener = handlePreferenceStoreChanged _
  private lazy val selectionListener = new ISelectionListener() {
    def selectionChanged(part: IWorkbenchPart, selection: ISelection) {
      selection match {
        case textSel: ITextSelection => askForOccurrencesUpdate(textSel)
        case _ =>
      }
    }
  }
  private lazy val tpePresenter = {
    val infoPresenter = new InformationPresenter(controlCreator) 
    infoPresenter.install(getSourceViewer)
    infoPresenter.setInformationProvider(actions.TypeOfExpressionProvider, IDocument.DEFAULT_CONTENT_TYPE)
    infoPresenter
  }

  setPartName("Scala Editor")
  scalaPrefStore.addPropertyChangeListener(preferenceListener)
  
  def scalaPrefStore = ScalaPlugin.prefStore
  def javaPrefStore = getPreferenceStore

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

    val showInferredSemicolons = new ShowInferredSemicolonsAction(ShowInferredSemicolonsBundle.PREFIX, this, ScalaPlugin.prefStore)
    showInferredSemicolons.setActionDefinitionId(ShowInferredSemicolonsAction.ACTION_DEFINITION_ID)
    setAction(ShowInferredSemicolonsAction.ACTION_ID, showInferredSemicolons)

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

  override protected def initializeKeyBindingScopes() {
    setKeyBindingScopes(Array(SCALA_EDITOR_SCOPE))
  }

  override def createJavaSourceViewerConfiguration: JavaSourceViewerConfiguration =
    new ScalaSourceViewerConfiguration(javaPrefStore, scalaPrefStore, this)

  override def setSourceViewerConfiguration(configuration: SourceViewerConfiguration) {
    super.setSourceViewerConfiguration(
      configuration match {
        case svc: ScalaSourceViewerConfiguration => svc
        case _ => new ScalaSourceViewerConfiguration(javaPrefStore, scalaPrefStore, this)
      })
  }
  private[eclipse] def sourceViewer = getSourceViewer.asInstanceOf[JavaSourceViewer]

  override def updateOccurrenceAnnotations(selection: ITextSelection, astRoot: CompilationUnit): Unit = {
    askForOccurrencesUpdate(selection)
  }
  
  private def performOccurrencesUpdate(selection: ITextSelection, documentLastModified: Long) {
    def getAnnotationModelOpt: Option[IAnnotationModel] = {
      for {
        documentProvider <- Option(getDocumentProvider)
        annotationModel <- Option(documentProvider.getAnnotationModel(getEditorInput))
      } yield annotationModel
    }

    val annotations = getAnnotations(selection, getInteractiveCompilationUnit, documentLastModified)
    for(annotationModel <- getAnnotationModelOpt) annotationModel.withLock {
      annotationModel.replaceAnnotations(occurrenceAnnotations, annotations)
      occurrenceAnnotations = annotations.keySet
    }
  }

  private def getAnnotations(selection: ITextSelection, unit: InteractiveCompilationUnit, documentLastModified: Long): Map[Annotation, Position] = {
    val region = EditorUtils.textSelection2region(selection)
    val occurrences = occurrencesFinder.findOccurrences(region, documentLastModified)
    for {
      Occurrences(name, locations) <- occurrences.toList
      location <- locations
      annotation = new Annotation(OCCURRENCE_ANNOTATION, false, "Occurrence of '" + name + "'")
      position = new Position(location.getOffset, location.getLength)
    } yield annotation -> position
  }.toMap

  private def askForOccurrencesUpdate(selection: ITextSelection) {

    if (selection.getLength < 0 || selection.getOffset < 0) 
      return
    
    if (getDocumentProvider == null || !isActiveEditor)
      return
    
    val lastModified = getSourceViewer.getDocument match {
      case document: IDocumentExtension4 =>
        document.getModificationStamp
      case _ => return
    }

    import org.eclipse.core.runtime.jobs.Job
    import org.eclipse.core.runtime.IProgressMonitor
    import org.eclipse.core.runtime.Status

    val job = new Job("Updating occurrence annotations") {
      def run(monitor: IProgressMonitor) = {
        val fileName = getInteractiveCompilationUnit.file.name
        Utils.debugTimed("Time elapsed for \"updateOccurrences\" in source " + fileName) { 
          performOccurrencesUpdate(selection, lastModified)
        }
        Status.OK_STATUS
      }
    }
    // set low priority for update occurrences 
    job.setPriority(Job.DECORATE)
    job.schedule()
  }

  override def doSelectionChanged(selection: ISelection) {
    super.doSelectionChanged(selection)
    val selectionProvider = getSelectionProvider
    if (selectionProvider != null)
      selectionProvider.getSelection match {
        case textSel: ITextSelection => askForOccurrencesUpdate(textSel)
        case _ =>
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

  override def dispose() {
    super.dispose()
    scalaPrefStore.removePropertyChangeListener(preferenceListener)
  }

  /** Return the `InformationPresenter` used to display the type of the selected expression.*/
  def typeOfExpressionPresenter: InformationPresenter = tpePresenter

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
      val blacklist = List("codeGroup", "importGroup", "generateGroup", "externalizeGroup")
      blacklist.flatMap(groups.get).flatten.foreach(mm.remove)

      // and provide our own organize imports instead
      mm.appendToGroup("importGroup", new refactoring.OrganizeImportsAction { setText("Organize Imports") })
      
      // add GenerateHashcodeAndEquals and IntroductProductN source generators
      mm.appendToGroup("generateGroup", new refactoring.source.GenerateHashcodeAndEqualsAction { 
        setText("Generate hashCode() and equals()...") 
      })
      mm.appendToGroup("generateGroup", new refactoring.source.IntroduceProductNTraitAction {
        setText("Introduce ProductN trait...")
      })

    }

    refactoring.RefactoringMenu.fillContextMenu(menu, this)
  }

  override def createPartControl(parent: org.eclipse.swt.widgets.Composite) {
    super.createPartControl(parent)
    occurrencesFinder = new ScalaOccurrencesFinder(getInteractiveCompilationUnit)
    refactoring.RefactoringMenu.fillQuickMenu(this)
    getSourceViewer match {
      case sourceViewer: ITextViewerExtension =>
        sourceViewer.prependVerifyKeyListener(new SurroundSelectionStrategy(getSourceViewer))
      case _ =>
    }
  }

  override def handlePreferenceStoreChanged(event: PropertyChangeEvent) =
    event.getProperty match {
      case ShowInferredSemicolonsAction.PREFERENCE_KEY =>
        getAction(ShowInferredSemicolonsAction.ACTION_ID).asInstanceOf[IUpdate].update()
      case _ =>
        if (affectsTextPresentation(event)) {
          // those events will trigger a UI change
          SWTUtils.asyncExec(super.handlePreferenceStoreChanged(event))
        } else {
          super.handlePreferenceStoreChanged(event)
        }
    }

  override def configureSourceViewerDecorationSupport(support: SourceViewerDecorationSupport) {
    super.configureSourceViewerDecorationSupport(support)
    SemanticHighlightingAnnotations.addAnnotationPreferences(support)
  }

  override protected def getSourceViewerDecorationSupport(viewer: ISourceViewer): SourceViewerDecorationSupport = {
    if (fSourceViewerDecorationSupport == null) {
      fSourceViewerDecorationSupport = new ScalaSourceViewerDecorationSupport(viewer, getOverviewRuler, getAnnotationAccess, getSharedColors)
      configureSourceViewerDecorationSupport(fSourceViewerDecorationSupport)
    }
    fSourceViewerDecorationSupport
  }


  override def getInteractiveCompilationUnit(): InteractiveCompilationUnit = {
    // getInputJavaElement always returns the right value
    getInputJavaElement().asInstanceOf[InteractiveCompilationUnit]
  }
}

object ScalaSourceFileEditor {
  private val EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS = "org.eclipse.ui.texteditor.ConstructedEditorMessages"
  private val bundleForConstructedKeys = ResourceBundle.getBundle(EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS)

  private val SCALA_EDITOR_SCOPE = "scala.tools.eclipse.scalaEditorScope"

  private val OCCURRENCE_ANNOTATION = "org.eclipse.jdt.ui.occurrences"
    
  private object controlCreator extends AbstractReusableInformationControlCreator {
    override def doCreateInformationControl(shell: Shell) = 
      new DefaultInformationControl(shell, true)
  }
  
 private class ScalaSourceViewerDecorationSupport(viewer: ISourceViewer, overviewRuler: IOverviewRuler, annotationAccess: IAnnotationAccess, sharedTextColors: ISharedTextColors)
    extends SourceViewerDecorationSupport(viewer, overviewRuler, annotationAccess, sharedTextColors) {

    override protected def createAnnotationPainter(): AnnotationPainter = {
      val annotationPainter = super.createAnnotationPainter
      SemanticHighlightingAnnotations.addTextStyleStrategies(annotationPainter)
      annotationPainter
    }
  }
}
