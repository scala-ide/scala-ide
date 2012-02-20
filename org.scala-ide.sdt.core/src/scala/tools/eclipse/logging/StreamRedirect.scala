package scala.tools.eclipse.logging

private[logging] object StreamRedirect {
  import java.io.{ OutputStream, PrintStream }

  private final val defaultStdOut: PrintStream = System.out
  private final val defaultStdErr: PrintStream = System.err
  
  private var isStdOutRedirected = false
  private var isStdErrRedirected = false

  def redirectStdOutput(): Unit = synchronized {
    if (!isStdOutRedirected) {
      val logger = LogManager.getLogger("System.out")
      val outStream = redirect(msg => logger.debug(msg))
      System.setOut(outStream)
      isStdOutRedirected = true
    }
  }
  
  def disableRedirectStdOutput(): Unit = synchronized {
    if(isStdOutRedirected) {
      System.setOut(defaultStdOut)
      isStdOutRedirected = false
    }
  }

  def redirectStdError(): Unit = synchronized {
    if (!isStdErrRedirected) {
      val logger = LogManager.getLogger("System.err")
      val errStream = redirect(msg => logger.error(msg))
      System.setErr(errStream)
      isStdErrRedirected = true
    }
  }
  
  def disableRedirectStdError(): Unit = synchronized {
    if(isStdErrRedirected) {
      System.setErr(defaultStdErr)
      isStdErrRedirected = false
    }
  }

  private def redirect(to: Any => Unit): PrintStream = 
    new PrintStream(new Redirect(to), /*autoFlush = */true)

  private class Redirect(to: Any => Unit) extends OutputStream {
    override def write(b: Int) {
      to(String.valueOf(b.asInstanceOf[Char]))
    }

    override def write(b: Array[Byte], off: Int, len: Int) {
      to(new String(b, off, len));
    }

    override def write(b: Array[Byte]) {
      write(b, 0, b.size);
    }
  }
}