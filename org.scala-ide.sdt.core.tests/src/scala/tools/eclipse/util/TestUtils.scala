package scala.tools.eclipse.util

import org.junit.Assert._

object TestUtils {

  /** Wait until `pred` is true, or timeout (in ms). */
  def waitUntil(timeout: Int)(pred: => Boolean) {
    val start = System.currentTimeMillis()
    while ((System.currentTimeMillis() < start + timeout) && !(pred)) {
      Thread.sleep(100)
    }
    assertTrue("Timed out before condition was true.", pred)
  }
}