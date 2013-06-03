package scala.tools.eclipse.debug

import com.sun.jdi.Method
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ReferenceType
import com.sun.jdi.Location
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.ObjectCollectedException
import com.sun.jdi.VMOutOfMemoryException

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

  import scala.util.control.Exception
  import Exception.Catch

  /** Catch the usual VM exceptions and return a default value.
   *
   *  It catches VMDisconnectedException, ObjectCollectedException, VMOutOfMemoryException
   *
   *  @note This method only catches non-checked exceptions. Combine with other
   *        catchers using the `or` combinator.
   */
  def safeVmCalls[A](defaultValue: A): Catch[A] =
    Exception.failAsValue(
      classOf[VMDisconnectedException],
      classOf[ObjectCollectedException],
      classOf[VMOutOfMemoryException])(defaultValue)
}