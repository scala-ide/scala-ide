package scala.tools.eclipse.debug.model

import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import com.sun.jdi.Type

/** A Reference type in the Scala debug model. Represente an array, an interface or a class type.
 */
class ScalaReferenceType(jdiType: ReferenceType, debugTarget: ScalaDebugTarget) extends ScalaDebugElement(debugTarget) {

  /** Return the value of the static field with the given name.
   * 
   * @throws IllegalArgumentException if the no field with the given name exists.
   */
  def fieldValue(fieldName: String): ScalaValue = {
    val field= jdiType.fieldByName(fieldName)
    if (field == null) {
      throw new IllegalArgumentException("Field '%s' doesn't exist for '%s'".format(fieldName, jdiType.name()))
    }
    ScalaValue(jdiType.getValue(field), debugTarget)
  }

}

/** A Class type in the Scala debug model
 */
class ScalaClassType(jdiType: ClassType, debugTarget: ScalaDebugTarget) extends ScalaReferenceType(jdiType, debugTarget) {
  
  /** Invoke the static method with given name, using the given arguments.
   * 
   * @throws IllegalArgumentException if no method with given name exists, or more than one.
   */
  def invokeMethod(methodName: String, thread: ScalaThread, args: ScalaValue*): ScalaValue = {
    val methods= jdiType.methodsByName(methodName)
    methods.size match {
      case 0 =>
        throw new IllegalArgumentException("Method '%s(..)' doesn't exist for '%s'".format(methodName, jdiType.name()))
      case 1 =>
        thread.invokeStaticMethod(jdiType, methods.get(0), args:_*)
      case _ =>
        throw new IllegalArgumentException("More than on method '%s(..)' for '%s'".format(methodName, jdiType.name()))
        
    }
  }
}

object ScalaType {
  
  /** Return the given JDI Type wrapped inside a Scala debug model type.
   */
  def apply(t: Type, debugTarget: ScalaDebugTarget): ScalaReferenceType = {
    t match {
      case c: ClassType =>
        new ScalaClassType(c, debugTarget)
      case r: ReferenceType =>
        new ScalaReferenceType(r, debugTarget)
    }
  }
}
