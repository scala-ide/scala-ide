package org.scalaide.core.testsetup

import java.util.concurrent.CountDownLatch

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor

object BlockingProgressMonitor {
  class CancellationException extends RuntimeException("Operation cancelled")

  /**
   * Executes the given operation with a [[IProgressMonitor]] and waits until it is done or cancelled.
   */
  def waitUntilDone[T](op: IProgressMonitor => T): T = {
    val monitor = new BlockingProgressMonitor
    val res = op(monitor)
    monitor.waitUntilDone()
    res
  }
}

/**
 * A progress monitor that can be used to block until the associated operation is complete.
 */
class BlockingProgressMonitor extends NullProgressMonitor {
  private[this] val latch = new CountDownLatch(1)

  /**
   * Blocks until done, or the monitor is cancelled.
   */
  def waitUntilDone() {
    latch.await()

    if (isCanceled())
      throw new BlockingProgressMonitor.CancellationException
  }

  override def done() {
    latch.countDown()
  }

  override def setCanceled(cancelled: Boolean) {
    super.setCanceled(cancelled)

    if (cancelled)
      latch.countDown()
  }
}
