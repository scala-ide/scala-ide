package scala.tools.eclipse.semantichighlighting

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.reconciliation.ReconciliationParticipant
import scala.tools.eclipse.ScalaPlugin

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner

/**
 * This class is instantiated by the reconciliationParticipants extension point and
 * simply forwards to the SemanticHighlightingReconciliation object.
 *
 * Deprecating this class since only the implicit highlighting component is using it, and I'm quite convinced that implicit highlighting
 * should be enabled via the editor, just like we do for semantic highlighting.
 */
@deprecated("This is not needed and should be removed the moment implicit highlighting is hooked in the editor","2.1.0")
class SemanticHighlightingReconciliationParticipant extends ReconciliationParticipant {

  private val reconciler: SemanticHighlightingReconciliation = new SemanticHighlightingReconciliation

  override def beforeReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    if (shouldRunReconciler(scu))
      reconciler.beforeReconciliation(scu, monitor, workingCopyOwner)
  }

  override def afterReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    if (shouldRunReconciler(scu))
      reconciler.afterReconciliation(scu, monitor, workingCopyOwner)
  }

  private def shouldRunReconciler(scu: ScalaCompilationUnit): Boolean = {
    def checkProjectExists(scu: ScalaCompilationUnit): Boolean = {
      val project = scu.getResource.getProject
      project != null && project.isOpen() && project.exists()
    }
    !ScalaPlugin.plugin.headlessMode && checkProjectExists(scu)
  }
}
