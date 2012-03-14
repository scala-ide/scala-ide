package scala.tools.eclipse.semantichighlighting

import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.util.Utils._
import scala.tools.eclipse.util.EclipseUtils
import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.IPartListener
import org.eclipse.ui.IWorkbenchPart
import scala.tools.eclipse.semantichighlighting.implicits.ImplicitHighlightingPresenter

/**
 * Manages the SemanticHighlightingPresenter instances for the open editors.
 *
 * Each ScalaCompilationUnit has one associated SemanticHighlightingPresenter,
 * which is created the first time a reconciliation is performed for a
 * compilation unit. When the editor (respectively the IWorkbenchPart) is closed,
 * the SemanticHighlightingPresenter is removed.
 *
 * @author Mirko Stocker
 */
object SemanticHighlightingReconciliation {

  private case class SemanticDecorationManagers(
    implicitHighlightingPresenter: ImplicitHighlightingPresenter,
    semanticHighlightingAnnotationsManager: SemanticHighlightingAnnotationsManager)

  private val semanticDecorationManagers =
    new HashMap[ScalaCompilationUnit, SemanticDecorationManagers] with SynchronizedMap[ScalaCompilationUnit, SemanticDecorationManagers]

  /**
   *  A listener that removes a  SemanticHighlightingPresenter when the part is closed.
   */
  private class UnregisteringPartListener(scu: ScalaCompilationUnit) extends IPartListener {

    override def partClosed(part: IWorkbenchPart) {
      semanticDecorationManagers.remove(scu)
    }

    override def partActivated(part: IWorkbenchPart) {}
    override def partBroughtToTop(part: IWorkbenchPart) {}
    override def partDeactivated(part: IWorkbenchPart) {}
    override def partOpened(part: IWorkbenchPart) {}
  }

  /**
   * Searches for the Editor that currently displays the compilation unit, then creates
   * an instance of SemanticHighlightingPresenter. A listener is registered at the editor
   * to remove the SemanticHighlightingPresenter when the editor is closed.
   */
  private def createSemanticDecorationManagers(scu: ScalaCompilationUnit): Option[SemanticDecorationManagers] = {
    val presenters =
      for {
        page <- EclipseUtils.getWorkbenchPages
        editorReference <- page.getEditorReferences
        editor <- Option(editorReference.getEditor(false))
        scalaEditor <- editor.asInstanceOfOpt[ScalaSourceFileEditor]
        editorInput <- Option(scalaEditor.getEditorInput)
        fileEditorInput <- editorInput.asInstanceOfOpt[FileEditorInput]
        if fileEditorInput.getPath == scu.getResource.getLocation
      } yield {
        page.addPartListener(new UnregisteringPartListener(scu))
        SemanticDecorationManagers(
          new ImplicitHighlightingPresenter(fileEditorInput, scalaEditor.sourceViewer),
          new SemanticHighlightingAnnotationsManager(scalaEditor.sourceViewer))
      }
    presenters.headOption
  }

  def afterReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {

    val firstTimeReconciliation = !semanticDecorationManagers.contains(scu)

    if (firstTimeReconciliation) {
      for (semanticDecorationManager <- createSemanticDecorationManagers(scu))
        semanticDecorationManagers(scu) = semanticDecorationManager
    }

    // sometimes we reconcile compilation units that are not open in an editor,
    // so we need to guard against the case where there's no semantic highlighter 
    for (semanticDecorationManager <- semanticDecorationManagers.get(scu)) {
      semanticDecorationManager.implicitHighlightingPresenter.update(scu)
      semanticDecorationManager.semanticHighlightingAnnotationsManager.updateSymbolAnnotations(scu)
    }
  }
}