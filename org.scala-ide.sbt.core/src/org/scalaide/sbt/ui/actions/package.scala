package org.scalaide.sbt.ui

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ui.actions.WorkspaceModifyOperation
import java.lang.reflect.InvocationTargetException
package object actions {
  def withWorkspaceModifyOperation(f: IProgressMonitor => Unit): WorkspaceModifyOperation = new WorkspaceModifyOperation() {
    @throws[InvocationTargetException]
    @throws[InterruptedException]
    override def execute(monitor: IProgressMonitor): Unit = f(monitor)
  }
}