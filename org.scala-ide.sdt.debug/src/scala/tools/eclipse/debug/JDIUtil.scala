package scala.tools.eclipse.debug

import com.sun.jdi.{ Method, AbsentInformationException, ReferenceType, Location }

object JDIUtil {

  /**
   * Return the list of executable lines of the given method.
   */
  def methodToLines(method: Method): Seq[Int] = {
    import scala.collection.JavaConverters._

    try {
      method.allLineLocations.asScala.map(_.lineNumber)
    } catch {
      case e: AbsentInformationException =>
        Nil
    }
  }

  /**
   * Return the valid locations in the given reference type, without
   * throwing AbsentInformationException if the information is missing.
   */
  def referenceTypeToLocations(referenceType: ReferenceType): Seq[Location] = {
    import scala.collection.JavaConverters._
    referenceType.methods.asScala.flatMap(
      method =>
        try {
          method.allLineLocations.asScala
        } catch {
          case e: AbsentInformationException =>
            Nil
        })
  }

}