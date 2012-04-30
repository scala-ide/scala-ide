package scala.tools.eclipse.scalatest.ui

/**
 * Location in source code about which an event concerns.
 */
sealed abstract class Location

/**
 * The location in a source file where the class whose by the fully qualified name
 * is passed as <code>className</code> is declared.
 */
final case class TopOfClass(className: String) extends Location

/**
 * The location in a source file where the method identified by the passed <code>methodId</code> 
 * in the class whose fully qualified name is pased as <code>className</code> is declared.  
 * The methodId is obtained by calling <code>toGenericString</code> on the <code>java.lang.reflect.Method</code> 
 * object representing the method.
 */
final case class TopOfMethod(className: String, methodId: String) extends Location

/**
 * An arbitrary line number in a named source file.
 */
final case class LineInFile(lineNumber: Int, fileName: String) extends Location

/**
 * Indicates the location should be taken from the stack depth exception, included elsewhere in 
 * the event that contained this location.
 */
final case object SeeStackDepthException extends Location