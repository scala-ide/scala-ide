/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.editor.decorators.semantichighlighting

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.extensions.ReconciliationParticipant
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.ui.internal.editor.decorators.implicits.ImplicitHighlightingPresenter

/**
 * This class is instantiated by the reconciliationParticipants extension point and
 * simply forwards to the SemanticHighlightingReconciliation object.
 *
 * Deprecating this class since only the implicit highlighting component is using it, and I'm quite convinced that implicit highlighting
 * should be enabled via the editor, just like we do for semantic highlighting.
 */
class ImplicitHighlighter extends SemanticHighlightingReconciliationParticipant(
    reconciler = new SemanticHighlightingReconciliation(List(viewer => new ImplicitHighlightingPresenter(viewer))))

@deprecated("This is not needed and should be removed the moment implicit highlighting is hooked in the editor", "2.1.0")
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
    !ScalaPlugin.plugin.headlessMode && checkProjectExists(scu)
  }
}
