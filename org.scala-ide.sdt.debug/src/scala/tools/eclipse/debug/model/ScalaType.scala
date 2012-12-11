package scala.tools.eclipse.debug.model

import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import com.sun.jdi.Type
import com.sun.jdi.Field
import com.sun.jdi.Value
import com.sun.jdi.Method

/** A Reference type in the Scala debug model. Represente an array, an interface or a class type.
 */
class ScalaReferenceType(jdiType: ReferenceType, debugTarget: ScalaDebugTarget) extends ScalaDebugElement(debugTarget) with HasFieldValue {

  // Members declared in scala.tools.eclipse.debug.model.HasFieldValue
  
  protected[model] override def referenceType = jdiType
  
  protected[model] override def jdiFieldValue(field: Field) = jdiType.getValue(field)
  
}

/** A Class type in the Scala debug model
 */
class ScalaClassType(jdiType: ClassType, debugTarget: ScalaDebugTarget) extends ScalaReferenceType(jdiType, debugTarget) with HasMethodInvocation {
  
  // Members declared in scala.tools.eclipse.debug.model.HasMethodInvocation
  
  protected[model] def classType() = jdiType
  
  protected[model] def jdiInvokeMethod(method: Method, thread: ScalaThread, args: Value*) = thread.invokeStaticMethod(jdiType, method, args:_*)
  
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
