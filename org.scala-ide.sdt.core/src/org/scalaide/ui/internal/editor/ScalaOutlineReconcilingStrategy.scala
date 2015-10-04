package org.scalaide.ui.internal.editor

import org.eclipse.jface.text.reconciler.IReconcilingStrategy
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension
import org.scalaide.logging.HasLogger
import org.eclipse.jface.text._
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.text.reconciler.DirtyRegion


class ScalaOutlineReconcilingStrategy(icuEditor:ScalaSourceFileEditor) extends IReconcilingStrategy with IReconcilingStrategyExtension with HasLogger{
  private def icUnit = icuEditor.getInteractiveCompilationUnit()

  override def setDocument(doc: IDocument): Unit = {}

  override def setProgressMonitor(pMonitor: IProgressMonitor): Unit = {}

  override def reconcile(dirtyRegion: DirtyRegion, subRegion: IRegion): Unit = {
    logger.debug("Incremental reconciliation not implemented.")
  }

  override def reconcile(partition: IRegion): Unit = {
    val sop = icuEditor.getOutlinePage
    logger.info(sop + " outline reconcile for " + partition)
    val start = System.currentTimeMillis()
    try {
      if (sop != null) {
        val rootNode = sop.getInput
        icUnit.scalaProject.presentationCompiler.apply(comp => {
          val builder = new ModelBuilder(comp)
          val delta = builder.updateTree(rootNode, icUnit.sourceMap(icuEditor.getViewer.getDocument.get.toCharArray()).sourceFile)
          logger.info(delta)
          logger.info("It took "+(System.currentTimeMillis() - start))
          icuEditor.getEditorSite.getShell.getDisplay.asyncExec(new Runnable() {
            def run = {
              sop.update(delta)
            }
          })
        })
      }
    } catch {
      case e: Exception => logger.error(e)
    }
  }

  override def initialReconcile(): Unit = {
    reconcile(null)
  }

}