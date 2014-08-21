package org.scalaide.core.internal.builder

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor

/**
 * Wrapper for a progress monitor that can access the state of an incremental
 * builder.
 */
class BuildMonitor(m: IProgressMonitor, builder: IncrementalProjectBuilder) extends IProgressMonitor {

  private val underlying = if (m != null) m else new NullProgressMonitor

  override def beginTask(name: String, totalWork: Int): Unit =
    underlying.beginTask(name, totalWork)

  override def done(): Unit =
    underlying.done()

  override def internalWorked(work: Double): Unit =
    underlying.internalWorked(work)

  /**
   * Returns `true` if either the progress monitor is cancelled or the builder
   * is interrupted.
   */
  override def isCanceled(): Boolean =
    underlying.isCanceled() || builder.isInterrupted()

  override def setCanceled(value: Boolean): Unit =
    underlying.setCanceled(value)

  override def setTaskName(name: String): Unit =
    underlying.setTaskName(name)

  override def subTask(name: String): Unit =
    underlying.subTask(name)

  override def worked(work: Int): Unit =
    underlying.worked(work)
}
