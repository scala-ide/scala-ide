package scala.tools.eclipse.reconciliation

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jface.text._
import org.eclipse.jface.text.reconciler.IReconcilingStrategy
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension
import org.eclipse.jface.text.reconciler.DirtyRegion
import org.eclipse.jface.text.source._
import org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener
import org.eclipse.ui.texteditor._
import scala.tools.eclipse.ui.InteractiveCompilationUnitEditor

class ScalaReconcilingStrategy(icuEditor: InteractiveCompilationUnitEditor) extends IReconcilingStrategy with IReconcilingStrategyExtension with HasLogger {

  private var progressMonitor : IProgressMonitor = _
  private var document: IDocument = _

  /**
   * The underlying compilation unit, in general implemented by a ScalaSourceFile.
   *
   * @note This member is a def, not a lazy val, to avoid doc/reconciler
   * desynchronizations if the underlying document is swapped.
   *  (see https://github.com/scala-ide/scala-ide/pull/309#discussion_r3048592)
   */
  private def icUnit = icuEditor.getInteractiveCompilationUnit()

  private val listeningEditor = icuEditor.asInstanceOf[IJavaReconcilingListener]

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
    listeningEditor.aboutToBeReconciled()
    icUnit.scalaProject.doWithPresentationCompiler(_.flushScheduledReloads())
    val errors = icUnit.reconcile(document.get)
    // we only update the edited compilation unit
    icuEditor.updateErrorAnnotations(errors)
    // reconciled expects a jdt.core.dom.CompilationUnitEditor as first argument,
    // which ScalaSourceFileEditor and other ICU Editors aren't
    // it is possible we starve Java-Side IReconcilingListeners here
    listeningEditor.reconciled(null, false, new NullProgressMonitor())
  }

  override def initialReconcile() {
    // an askReload there adds the scUnit to the list of managed CUs
    icUnit.scheduleReconcile()
  }

}
