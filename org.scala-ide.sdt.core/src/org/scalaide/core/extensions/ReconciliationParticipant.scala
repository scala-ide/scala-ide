package org.scalaide.core.extensions

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit

/**
 * The ReconciliationParticipant trait is used by the extension point
 * org.scala-ide.sdt.core.reconciliationParticipants
 *
 * Registered extenstion points will get called before and after each
 * reconciliation of a compilation unit.
 *
 */
trait ReconciliationParticipant {

  def beforeReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
  }

  def afterReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
  }
}
