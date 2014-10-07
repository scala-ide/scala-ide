package org.scalaide.ui.editor

import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocumentListener
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.reconciler.DirtyRegion
import org.eclipse.jface.text.reconciler.IReconcilingStrategy
import org.scalaide.logging.HasLogger
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension
import org.eclipse.core.runtime.IProgressMonitor

class ReconcilingStrategy(sourceEditor: SourceCodeEditor, documentListener: IDocumentListener) extends IReconcilingStrategy with IReconcilingStrategyExtension with HasLogger {
  private var document: Option[IDocument] = None

  override def setDocument(doc: IDocument) {
    document.foreach(_.removeDocumentListener(documentListener))
    document = Option(doc)
    document.foreach(_.addDocumentListener(documentListener))
  }

  override def reconcile(dirtyRegion: DirtyRegion, subRegion: IRegion) {
    logger.debug("Incremental reconciliation not implemented.")
  }

  override def reconcile(partition: IRegion) {
    for (doc <- document) {
      val errors = sourceEditor.getInteractiveCompilationUnit.forceReconcile()
      sourceEditor.updateErrorAnnotations(errors)
    }
  }

  override def initialReconcile(): Unit = {
    sourceEditor.getInteractiveCompilationUnit().initialReconcile()
  }

  override def setProgressMonitor(pm: IProgressMonitor): Unit = {}
}
