package scala.tools.eclipse.debug

import com.sun.jdi.{Method, AbsentInformationException}

object JDIUtil {

  /**
   * Return the list of executable lines of the given method.
   */
  def methodToLines(m: Method) = {
    import scala.collection.JavaConverters._

    try {
      m.allLineLocations.asScala.map(_.lineNumber)
    } catch {
      case e: AbsentInformationException =>
        Nil
      case e =>
        throw e
    }
  }

}