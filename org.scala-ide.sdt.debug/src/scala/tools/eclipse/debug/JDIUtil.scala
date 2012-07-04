package scala.tools.eclipse.debug

import com.sun.jdi.{ Method, AbsentInformationException, ReferenceType, Location }

object JDIUtil {

  /**
   * Return the list of executable lines of the given method.
   */
  def methodToLines(m: Method): Seq[Int] = {
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

  /**
   * Return the valid locations in the given reference type, without
   * throwing AbsentInformationException if the information is missing.
   */
  def referenceTypeToLocations(t: ReferenceType): Seq[Location] = {
    import scala.collection.JavaConverters._
    t.methods.asScala.flatMap(
      method =>
        try {
          method.allLineLocations.asScala
        } catch {
          case e: AbsentInformationException =>
            Nil
          case e =>
            throw e
        })
  }

}