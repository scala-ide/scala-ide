package org.scalaide.core.testsetup

import org.eclipse.core.runtime.NullProgressMonitor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CancellationException

object BlockingProgressMonitor {
  class CancellationException extends RuntimeException("Operation cancelled")
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
