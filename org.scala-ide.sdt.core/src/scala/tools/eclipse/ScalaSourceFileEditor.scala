/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.swt.widgets.Composite
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
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text._
import org.eclipse.jface.text.source.{ Annotation, IAnnotationModelExtension, SourceViewerConfiguration }
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.{ IWorkbenchPart, ISelectionListener, IFileEditorInput }
import org.eclipse.ui.editors.text.{ ForwardingDocumentProvider, TextFileDocumentProvider }
import org.eclipse.ui.texteditor.{ IAbstractTextEditorHelpContextIds, ITextEditorActionConstants, IWorkbenchActionDefinitionIds, TextOperationAction }
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.tools.eclipse.javaelements.{ScalaSourceFile, ScalaCompilationUnit}
import scala.tools.eclipse.markoccurrences.{ ScalaOccurrencesFinder, Occurrences }
import scala.tools.eclipse.text.scala.ScalaTypeAutoCompletionProposalManager
import scala.tools.eclipse.util.IDESettings
import scala.tools.eclipse.util.{ Defensive, Tracer }
import org.eclipse.ui.texteditor.IDocumentProvider
import org.eclipse.ui.IEditorInput
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.action.Action

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

  // doSetInput is called in Thread "main" but can take time because some part require ScalaPresentationCompiler
  // but run it in other thread than current raise NPE later
  // keep the code block commented for memory (and may be later try to improve)
  //  /*
  //   * @see AbstractTextEditor#doSetInput
  //   * @throws CoreException
  //   */
  //  protected override def doSetInput(input: IEditorInput) = Defensive.askRunOutOfMain("ScalaSourceFileEditor.doSetInput") { super.doSetInput(input) }

  private[eclipse] def sourceViewer = getSourceViewer

  override def updateOccurrenceAnnotations(selection: ITextSelection, astRoot: CompilationUnit) = ScalaPlugin.plugin.updateOccurrenceAnnotationsService.askUpdateOccurrenceAnnotations(this, selection, astRoot)
  def superUpdateOccurrenceAnnotations(selection: ITextSelection, astRoot: CompilationUnit) = {} //super.updateOccurrenceAnnotations(selection, astRoot)

  override def doSelectionChanged(selection: ISelection) {
    Tracer.println("ScalaSourceFileEditor.doSelectionChanged")
    super.doSelectionChanged(selection)
    val selectionProvider = getSelectionProvider
    if (selectionProvider != null)
      selectionProvider.getSelection match {
        case textSel: ITextSelection => updateOccurrenceAnnotations(textSel, null)
        case _ =>
      }
  }

  override def installOccurrencesFinder(forceUpdate: Boolean) {
    //super.installOccurrencesFinder(forceUpdate)
    ScalaPlugin.plugin.updateOccurrenceAnnotationsService.installSelectionListener(getEditorSite)
  }
  override def uninstallOccurrencesFinder() {
    ScalaPlugin.plugin.updateOccurrenceAnnotationsService.uninstallSelectionListener(getEditorSite)
    //super.uninstallOccurrencesFinder
  }
  
  override def createPartControl(parent: Composite) {
    super.createPartControl(parent)
    val viewer = getSourceViewer()
    if (viewer ne null) {
      
      //FIXME : workaround for my limited knowledge about current presentation compiler
      JavaPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(getEditorInput()) match {
        case scu : ScalaCompilationUnit => viewer.getDocument().addPrenotifiedDocumentListener(ScalaTypeAutoCompletionProposalManager.getProposalFor(scu))
        case _ => ()
      }
    }

    refactoring.RefactoringMenu.fillQuickMenu(this)
  }

  override def editorContextMenuAboutToShow(menu: org.eclipse.jface.action.IMenuManager): Unit = {
    super.editorContextMenuAboutToShow(menu)
    refactoring.RefactoringMenu.fillContextMenu(menu, this)
  }

  // override to public scope (from protected)
  override def getElementAt(offset: Int, reconcile: Boolean) = super.getElementAt(offset, reconcile)

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

}

object ScalaSourceFileEditor {

  val EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS = "org.eclipse.ui.texteditor.ConstructedEditorMessages"
  val bundleForConstructedKeys = ResourceBundle.getBundle(EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS)

  val SCALA_EDITOR_SCOPE = "scala.tools.eclipse.scalaEditorScope"
}
