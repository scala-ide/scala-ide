package org.scalaide.core.internal.logging

import org.scalaide.util.internal.Suppress

private[logging] object StreamRedirect {
  import java.io.OutputStream
  import java.io.PrintStream

  private final val defaultStdOut: PrintStream = System.out
  private final val defaultStdErr: PrintStream = System.err

  private var isStdOutRedirected = false
  private var isStdErrRedirected = false

  def redirectStdOutput(): Unit = synchronized {
    if (!isStdOutRedirected) {
      val logger = LogManager.getLogger("System.out")
      val outStream = redirect(msg => logger.debug(msg))
      System.setOut(outStream)
      Suppress.DeprecatedWarning.`Console.setOut`(outStream)
      isStdOutRedirected = true
    }
  }

  def disableRedirectStdOutput(): Unit = synchronized {
    if(isStdOutRedirected) {
      System.setOut(defaultStdOut)
      Suppress.DeprecatedWarning.`Console.setOut`(defaultStdOut)
      isStdOutRedirected = false
    }
  }

  def redirectStdError(): Unit = synchronized {
    if (!isStdErrRedirected) {
      val logger = LogManager.getLogger("System.err")
      val errStream = redirect(msg => logger.error(msg))
      System.setErr(errStream)
      Suppress.DeprecatedWarning.`Console.setErr`(errStream)
      isStdErrRedirected = true
    }
  }

  def disableRedirectStdError(): Unit = synchronized {
    if(isStdErrRedirected) {
      System.setErr(defaultStdErr)
      Suppress.DeprecatedWarning.`Console.setErr`(defaultStdErr)
      isStdErrRedirected = false
    }
  }

  private def redirect(to: Any => Unit): PrintStream =
    new PrintStream(new Redirect(to), /*autoFlush = */true)

  private class Redirect(to: Any => Unit) extends OutputStream {
    override def write(b: Int): Unit = {
      to(String.valueOf(b.toChar))
    }

    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      val str = new String(b, off, len).trim
      if (str.length > 0)
        to(str);
    }

    override def write(b: Array[Byte]): Unit = {
      write(b, 0, b.size);
    }
  }
}
