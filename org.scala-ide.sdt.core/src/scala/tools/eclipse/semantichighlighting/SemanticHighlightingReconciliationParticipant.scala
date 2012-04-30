package scala.tools.eclipse.semantichighlighting

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.reconciliation.ReconciliationParticipant
import scala.tools.eclipse.ScalaPlugin

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner

/**
 * This class is instantiated by the reconciliationParticipants extension point and
 * simply forwards to the SemanticHighlightingReconciliation object.
 */
class SemanticHighlightingReconciliationParticipant extends ReconciliationParticipant {

  private val reconciler: SemanticHighlightingReconciliation = new SemanticHighlightingReconciliation
  
  override def afterReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    if (!ScalaPlugin.plugin.headlessMode)
      reconciler.afterReconciliation(scu, monitor, workingCopyOwner)
  }
}
