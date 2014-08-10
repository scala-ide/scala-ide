/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.editor.decorators.semantichighlighting

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.extensions.ReconciliationParticipant
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.ui.internal.editor.decorators.implicits.ImplicitHighlightingPresenter

@deprecated("This is not needed and should be removed the moment semantic highlighting extensions are fully hooked into the editor", "2.1.0")
class SemanticHighlightingReconciliationParticipant(private val reconciler: SemanticHighlightingReconciliation) extends ReconciliationParticipant {

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
    !IScalaPlugin().headlessMode && checkProjectExists(scu)
  }
}
