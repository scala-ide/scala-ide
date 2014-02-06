package org.scalaide.core

import org.scalaide.logging.HasLogger

object FlakyTest extends HasLogger {
  private final val RetryFlakyTests = "retryFlakyTests"
  private lazy val retryFlakyTests =
    "true".equalsIgnoreCase(System.getProperty(RetryFlakyTests))

  /** Retry to execute the passed `test` up to N `times`.
    *
    * @param methodName The test's method name.
    * @param errorMsg   The error message returned when the test fails.
    * @param times      The total number of attempts that will be performed before bailing out.
    * @param test       The actual test code to execute.
    */
  def retry(methodName: String, errorMsg: String = "", times: Int = 5)(test: => Unit): Unit = {
    @annotation.tailrec
    def loop(attempt: Int): Unit = {
      try {
        logger.debug(s"Test run number: ${attempt})")
        test
        logger.debug(s"Test `${methodName}` was successful!")
      } catch {
        case e: Throwable =>
          if (attempt < times)
            loop(attempt + 1)
          else {
            logger.debug(s"Bailing out after ${attempt} attempts. The test is failing consistenly, this may actually be a real regression!?")
            throw e
          }
      }
    }

    logger.debug(s"About to start executing test `${methodName}`, which is known to be flaky.")
    if(errorMsg.nonEmpty)
      logger.debug(s"""When the test fails, it usually reports the following error: \"${errorMsg}\"""")

    if(retryFlakyTests) loop(1)
    else test
  }
}