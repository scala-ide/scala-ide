package scala.tools.eclipse.reconciler

import org.eclipse.core.runtime.IProgressMonitor
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jface.text._
import org.eclipse.jface.text.reconciler.{IReconcilingStrategy, IReconcilingStrategyExtension, DirtyRegion}
import org.eclipse.jface.text.source._
import org.eclipse.ui.texteditor._
import scala.tools.eclipse.ScalaEditor
import scala.tools.eclipse.ScalaSourceFileEditor
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.javaelements.ScalaCompilationUnit

class ScalaReconcilingStrategy(scEditor: ScalaEditor) extends IReconcilingStrategy with IReconcilingStrategyExtension with HasLogger {

  private var progressMonitor : IProgressMonitor = _
  private var document: IDocument = _
  private lazy val scUnit = scEditor.getInteractiveCompilationUnit().asInstanceOf[ScalaCompilationUnit]

  override def setDocument(doc: IDocument) {
    document = doc
  }

  override def setProgressMonitor(pMonitor : IProgressMonitor) {
    progressMonitor = pMonitor
  }

  override def reconcile(dirtyRegion: DirtyRegion, subRegion: IRegion) {
    logger.debug("Incremental reconciliation not implemented.")
  }

  override def reconcile(partition: IRegion) {
    scUnit.scalaProject.doWithPresentationCompiler(_.flushScheduledReloads())
    val errors = scUnit.reconcile(document.get)
  }

  override def initialReconcile() {
    // an askReload there adds the scUnit to the list of managed CUs
    scUnit.scheduleReconcile()
  }

}
