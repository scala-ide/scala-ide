package scala.tools.eclipse.reconciler

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor;
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jface.text._
import org.eclipse.jface.text.reconciler.{IReconcilingStrategy, IReconcilingStrategyExtension, DirtyRegion}
import org.eclipse.jface.text.source._
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor
import org.eclipse.ui.texteditor._
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.ui.InteractiveCompilationUnitEditor

class ScalaReconcilingStrategy(icuEditor: InteractiveCompilationUnitEditor) extends IReconcilingStrategy with IReconcilingStrategyExtension with HasLogger {

  private var progressMonitor : IProgressMonitor = _
  private var document: IDocument = _
  private def icUnit = icuEditor.getInteractiveCompilationUnit()

  // That ain't pretty, but it's the Java way, see org.eclipse,jdt.internal.ui.text.java.JavaReconcilingStrategy
  private val isTriggerableEditor : Boolean = (icuEditor.isInstanceOf[CompilationUnitEditor])
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
    if (isTriggerableEditor)
      listeningEditor.aboutToBeReconciled()
    icUnit.scalaProject.doWithPresentationCompiler(_.flushScheduledReloads())
    val errors = icUnit.reconcile(document.get)
    // we only update the edited compilation unit
    icuEditor.updateErrorAnnotations(errors)
    if (isTriggerableEditor)
      listeningEditor.reconciled(icUnit.asInstanceOf[CompilationUnit], false, new NullProgressMonitor())
  }

  override def initialReconcile() {
    // an askReload there adds the scUnit to the list of managed CUs
    icUnit.scheduleReconcile()
  }

}
