package scala.tools.eclipse.semantichighlighting

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
import java.util.concurrent.ConcurrentHashMap
import scala.tools.eclipse.semantic.SemanticAction
import scala.tools.eclipse.ui.PartAdapter

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
class SemanticHighlightingReconciliation {

  private case class SemanticDecorationManagers(actions: List[SemanticAction])

  private val semanticDecorationManagers: java.util.Map[ScalaCompilationUnit, SemanticDecorationManagers] = new ConcurrentHashMap

  /** A listener that removes a  SemanticHighlightingPresenter when the part is closed. */
  private class UnregisteringPartListener(scu: ScalaCompilationUnit) extends PartAdapter {
    override def partClosed(part: IWorkbenchPart) {
      for {
        scalaEditor <- part.asInstanceOfOpt[ScalaSourceFileEditor]
        editorInput <- Option(scalaEditor.getEditorInput)
        fileEditorInput <- editorInput.asInstanceOfOpt[FileEditorInput]
        if fileEditorInput.getPath == scu.getResource.getLocation
      } { 
        semanticDecorationManagers.remove(scu)
      }
    }
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
        val semanticActions = List(
            new ImplicitHighlightingPresenter(fileEditorInput, scalaEditor.sourceViewer), 
            new SemanticHighlightingAnnotationsManager(scalaEditor.sourceViewer)
        )
        SemanticDecorationManagers(semanticActions)
      }
    presenters.headOption
  }

  def afterReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {

    val firstTimeReconciliation = !semanticDecorationManagers.containsKey(scu)

    if (firstTimeReconciliation) {
      for (semanticDecorationManager <- createSemanticDecorationManagers(scu))
        semanticDecorationManagers.put(scu, semanticDecorationManager)
    }

    // sometimes we reconcile compilation units that are not open in an editor,
    // so we need to guard against the case where there's no semantic highlighter 
    for {
      semanticDecorationManager <- Option(semanticDecorationManagers.get(scu))
      action <- semanticDecorationManager.actions
    } action(scu)
  }
}