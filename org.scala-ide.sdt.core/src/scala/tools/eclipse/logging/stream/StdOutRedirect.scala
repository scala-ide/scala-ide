package scala.tools.eclipse.logging
package stream

import java.io.PrintStream

/** Redirect to the {{{logger}}} all messages sent in the Standard Output.*/
object StdOutRedirect extends HasLogger {
  def enable() {
    val outStream = new PrintStream(System.out) {
      override def print(message: String) {
        logger.info(message)
      }
    }
    
    System.setOut(outStream)
  }
}