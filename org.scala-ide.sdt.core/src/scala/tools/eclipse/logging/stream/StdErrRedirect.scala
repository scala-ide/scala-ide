package scala.tools.eclipse.logging
package stream

import java.io.PrintStream

/** Redirect to the {{{logger}}} all messages sent in the Standard Error.*/
object StdErrRedirect extends HasLogger {
  def enable() {
    val errStream = new PrintStream(System.err) {
      override def print(message: String) {
        logger.error(message)
      }
    }
    
    System.setErr(errStream)
  }
}