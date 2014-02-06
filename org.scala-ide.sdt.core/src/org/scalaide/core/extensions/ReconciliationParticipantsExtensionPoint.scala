package org.scalaide.core.extensions

import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.Platform
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.Utils

/**
 * The implementation for the org.scala-ide.sdt.core.reconciliationParticipants
 * extension points which gets the registered extensions and invokes them.
 *
 * The runBefore and runAfter methods are themselves invoked by the
 * ScalaSourceFile.reconcile method.
 *
 */
object ReconciliationParticipantsExtensionPoint extends HasLogger {

  final val PARTICIPANTS_ID = "org.scala-ide.sdt.core.reconciliationParticipants"

  lazy val extensions: List[ReconciliationParticipant] = {
    val configs = Platform.getExtensionRegistry.getConfigurationElementsFor(PARTICIPANTS_ID).toList

    configs map { e =>
      Utils.tryExecute {
        e.createExecutableExtension("class")
      }
    } collect {
      case Some(p: ReconciliationParticipant) => p
    }
  }

  def runBefore(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    extensions foreach { extension =>
      Utils.tryExecute {
        extension.beforeReconciliation(scu, monitor, workingCopyOwner)
      }
    }
  }

  def runAfter(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    extensions foreach { extension =>
      Utils.tryExecute {
        extension.afterReconciliation(scu, monitor, workingCopyOwner)
      }
    }
  }
}
