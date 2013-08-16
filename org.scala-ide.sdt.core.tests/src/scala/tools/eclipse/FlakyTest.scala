package scala.tools.eclipse

import scala.tools.eclipse.logging.HasLogger

object FlakyTest extends HasLogger {
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
        logger.debug(s"Test `%{testName}` was successful!")
      }
      catch {
        case _: AssertionError if attempt < times => loop(attempt + 1)
        case e: AssertionError                    =>
          logger.debug(s"Bailing out after ${attempt} attempts. The test is failing consistenly, this may actually be a real regression!?")
          throw e
      }
    }

    logger.debug(s"About to start executing test `${methodName}`, which is known to be flacky.")
    if(errorMsg.nonEmpty)
      logger.debug(s"""When the test fails, it usually reports the following error: \"${errorMsg}\"""")

    loop(1)
  }
}