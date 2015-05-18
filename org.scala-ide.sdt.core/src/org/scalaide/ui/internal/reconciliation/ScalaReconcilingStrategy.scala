package org.scalaide.ui.internal.reconciliation

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor
import org.scalaide.logging.HasLogger
import org.eclipse.jface.text._
import org.eclipse.jface.text.reconciler.IReconcilingStrategy
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension
import org.eclipse.jface.text.reconciler.DirtyRegion
import org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener
import org.eclipse.jdt.core.ICompilationUnit
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor
import org.scalaide.util.Utils._

class ScalaReconcilingStrategy(icuEditor: InteractiveCompilationUnitEditor) extends IReconcilingStrategy with IReconcilingStrategyExtension with HasLogger {

  /**
   * The underlying compilation unit, in general implemented by a ScalaSourceFile.
   *
   * @note This member is a def, not a lazy val, to avoid doc/reconciler
   * desynchronizations if the underlying document is swapped.
   *  (see https://github.com/scala-ide/scala-ide/pull/309#discussion_r3048592)
   */
  private def icUnit = icuEditor.getInteractiveCompilationUnit()

  // Our icuEditor can be a source-attached binary, a.k.a ScalaClassFileEditor,
  // for which reconciliation of the locally opened editor makes little sense
  // (it's more properly a ScalaClassFileViewer) but we still want to flush
  // scheduled reloads nonetheless
  private val listeningEditor: Option[IJavaReconcilingListener] =
    icuEditor.asInstanceOfOpt[IJavaReconcilingListener]

  override def setDocument(doc: IDocument): Unit = {}

  override def setProgressMonitor(pMonitor: IProgressMonitor): Unit = {}

  override def reconcile(dirtyRegion: DirtyRegion, subRegion: IRegion): Unit = {
    logger.debug("Incremental reconciliation not implemented.")
  }

  override def reconcile(partition: IRegion): Unit = {
    listeningEditor.foreach(_.aboutToBeReconciled())
    val errors = icUnit.forceReconcile()

    // Some features, such as quick fixes, are dependent upon getting an ICompilationUnit there
    val cu: Option[ICompilationUnit] = icUnit.asInstanceOfOpt[ICompilationUnit]
    // we only update the edited compilation unit
    icuEditor.updateErrorAnnotations(errors, cu.orNull)

    // reconciled expects a jdt.core.dom.CompilationUnitEditor as first argument,
    // which ScalaSourceFileEditor and other ICU Editors aren't
    // it is possible we starve Java-Side IReconcilingListeners here
    listeningEditor.foreach(_.reconciled(null, false, new NullProgressMonitor()))
  }

  override def initialReconcile(): Unit = {
    // an askReload there adds the scUnit to the list of managed CUs
    icUnit.initialReconcile()
    reconcile(null)
  }

}
