package org.scalaide.ui.internal.editor.outline

import org.eclipse.jface.text.reconciler.IReconcilingStrategy
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension
import org.scalaide.logging.HasLogger
import org.eclipse.jface.text._
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.text.reconciler.DirtyRegion
import org.scalaide.util.ui.DisplayThread

class ScalaOutlineReconcilingStrategy(icuEditor: OutlinePageEditorExtension) extends IReconcilingStrategy with IReconcilingStrategyExtension with HasLogger {
  private def icUnit = icuEditor.getInteractiveCompilationUnit()

  override def setDocument(doc: IDocument): Unit = {}

  override def setProgressMonitor(pMonitor: IProgressMonitor): Unit = {}

  override def reconcile(dirtyRegion: DirtyRegion, subRegion: IRegion): Unit = {
    logger.debug("Incremental reconciliation not implemented.")
  }

  override def reconcile(partition: IRegion): Unit = {
    val sop = Option(icuEditor.getOutlinePage)
    if (!sop.isEmpty) {
      val oldRoot = sop.get.getInput
      icUnit.scalaProject.presentationCompiler.apply(comp => {
        val rootNode = ModelBuilder.buildTree(comp, icUnit.sourceMap(icuEditor.getViewer.getDocument.get.toCharArray()).sourceFile)
        val delta = if (oldRoot != null) oldRoot.updateAll(rootNode) else null
        DisplayThread.asyncExec(
          if (delta eq null)
            sop.get.setInput(rootNode)
          else
            sop.get.update(delta))
      })
    }
  }

  override def initialReconcile(): Unit = {
    reconcile(null)
  }

}