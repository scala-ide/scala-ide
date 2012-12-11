package scala.tools.eclipse.debug.model

import scala.tools.eclipse.debug.ScalaDebugPlugin
import scala.tools.eclipse.debug.ScalaDebugger
import scala.tools.eclipse.debug.ScalaDebugger.modelProvider
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.debug.core.model.{ITerminate, DebugElement}

import com.sun.jdi.ClassType
import com.sun.jdi.Field
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

/**
 * Base class for debug elements in the Scala debug model
 * This class is thread safe.
 */
abstract class ScalaDebugElement(debugTarget: ScalaDebugTarget) extends DebugElement(debugTarget) with ITerminate with HasLogger {

  // Members declared in org.eclipse.core.runtime.IAdaptable

  override def getAdapter(adapter: Class[_]): Object = {
    adapter match {
      case ScalaDebugger.classIDebugModelProvider =>
        modelProvider
      case _ =>
        super.getAdapter(adapter)
    }
  }
  
  override def getDebugTarget: ScalaDebugTarget = debugTarget

  // Members declared in org.eclipse.debug.core.model.IDebugElement

  override val getModelIdentifier: String = ScalaDebugPlugin.id

  // Members declared in org.eclipse.debug.core.model.ITerminate

  override def canTerminate: Boolean = debugTarget.canTerminate
  override def isTerminated: Boolean = debugTarget.isTerminated
  override def terminate(): Unit = debugTarget.terminate()

  // ----

}

trait HasFieldValue {
  self: ScalaDebugElement =>

  protected[model] def referenceType(): ReferenceType

  /** Return the JDI value for the given field.
   */
  protected[model] def jdiFieldValue(field: Field): Value

  /** Return the value of the field with the given name.
   *
   *  @throws IllegalArgumentException if the no field with the given name exists.
   */
  def fieldValue(fieldName: String): ScalaValue = {
    val field = referenceType().fieldByName(fieldName)
    if (field == null) {
      throw new IllegalArgumentException("Field '%s' doesn't exist for '%s'".format(fieldName, referenceType().name()))
    }
    ScalaValue(jdiFieldValue(field), getDebugTarget)
  }
}

trait HasMethodInvocation {
  self: ScalaDebugElement =>

  protected[model] def classType(): ClassType

  /** Invoke the given method.
   */
  protected[model] def jdiInvokeMethod(method: Method, thread: ScalaThread, args: Value*): Value

  /** Invoke the method with given name, using the given arguments.
   *
   *  @throws IllegalArgumentException if no method with given name exists, or more than one.
   */
  def invokeMethod(methodName: String, thread: ScalaThread, args: ScalaValue*): ScalaValue = {
    val methods = classType().methodsByName(methodName)
    methods.size match {
      case 0 =>
        throw new IllegalArgumentException("Method '%s(..)' doesn't exist for '%s'".format(methodName, classType.name()))
      case 1 =>
        ScalaValue(jdiInvokeMethod(methods.get(0), thread, args.map(_.underlying): _*), getDebugTarget)
      case _ =>
        throw new IllegalArgumentException("More than on method '%s(..)' for '%s'".format(methodName, classType.name()))
    }
  }

  /** Invoke the method with given name and signature, using the given arguments.
   *
   *  @throws IllegalArgumentException if no method with given name and signature exists.
   */
  def invokeMethod(methodName: String, methodSignature: String, thread: ScalaThread, args: ScalaValue*): ScalaValue = {
    val method = classType().concreteMethodByName(methodName, methodSignature)
    if (method == null) {
      throw new IllegalArgumentException("Method '%s%s' doesn't exist for '%s'".format(methodName, methodSignature, classType().name()))
    }
    ScalaValue(jdiInvokeMethod(method, thread, args.map(_.underlying): _*), getDebugTarget)
  }
}