package scala.tools.eclipse.debug.model

import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import com.sun.jdi.Type
import com.sun.jdi.Field
import com.sun.jdi.Value
import com.sun.jdi.Method

/** A Reference type in the Scala debug model. Represente an array, an interface or a class type.
 */
class ScalaReferenceType(underlying: ReferenceType, debugTarget: ScalaDebugTarget) extends ScalaDebugElement(debugTarget) with HasFieldValue {

  // Members declared in scala.tools.eclipse.debug.model.HasFieldValue

  protected override def getReferenceType(): ReferenceType = underlying

  protected override def getJdiFieldValue(field: Field): Value = underlying.getValue(field)

}

/** A Class type in the Scala debug model
 */
class ScalaClassType(underlying: ClassType, debugTarget: ScalaDebugTarget) extends ScalaReferenceType(underlying, debugTarget) with HasMethodInvocation {

  // Members declared in scala.tools.eclipse.debug.model.HasMethodInvocation

  def classType(): ClassType = underlying

  protected[model] def jdiInvokeMethod(method: Method, thread: ScalaThread, args: Value*): Value = thread.invokeStaticMethod(underlying, method, args:_*)

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
