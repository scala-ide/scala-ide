package scala.tools.eclipse.debug.model

import scala.collection.JavaConverters.asScalaBufferConverter
import org.eclipse.debug.core.model.IStackFrame
import com.sun.jdi.StackFrame
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.Method
import com.sun.jdi.Type
import com.sun.jdi.ReferenceType
import com.sun.jdi.BooleanType
import com.sun.jdi.ByteType
import com.sun.jdi.CharType
import com.sun.jdi.DoubleType
import com.sun.jdi.IntegerType
import com.sun.jdi.FloatType
import com.sun.jdi.LongType
import com.sun.jdi.ShortType
import com.sun.jdi.ArrayType
import scala.reflect.NameTransformer

object ScalaStackFrame {
  
  def getSimpleName(tpe: Type): String = {
    tpe match {
      case booleanType: BooleanType =>
        "Boolean"
      case byteType: ByteType =>
        "Byte"
      case charType: CharType =>
        "Char"
      case doubleType: DoubleType =>
        "Double"
      case floatType: FloatType =>
        "Float"
      case intType: IntegerType =>
        "Int"
      case longType: LongType =>
        "Long"
      case shortType: ShortType =>
        "Short"
      case arrayType: ArrayType =>
        "Array[%s]".format(getSimpleName(arrayType.componentType))
      case refType: ReferenceType =>
        NameTransformer.decode(refType.name.split('.').last)
      case _ =>
        ???
    }
  }
  
  def getFullName(method: Method): String = {
    import scala.collection.JavaConverters._
    "%s.%s(%s)".format(
        getSimpleName(method.declaringType),
        NameTransformer.decode(method.name),
        method.argumentTypes.asScala.map(getSimpleName(_)).mkString(", "))
  }
}

class ScalaStackFrame(val thread: ScalaThread, var stackFrame: StackFrame) extends ScalaDebugElement(thread.getScalaDebugTarget) with IStackFrame {
  import ScalaStackFrame._

  // Members declared in org.eclipse.debug.core.model.IStackFrame

  def getCharEnd(): Int = -1
  def getCharStart(): Int = -1
  def getLineNumber(): Int = stackFrame.location.lineNumber // TODO: cache data ?
  def getName(): String = stackFrame.location.declaringType.name // TODO: cache data ?
  def getRegisterGroups(): Array[org.eclipse.debug.core.model.IRegisterGroup] = ???
  def getThread(): org.eclipse.debug.core.model.IThread = thread
  def getVariables(): Array[org.eclipse.debug.core.model.IVariable] = variables.toArray // TODO: need real logic
  def hasRegisterGroups(): Boolean = ???
  def hasVariables(): Boolean = ???

  // Members declared in org.eclipse.debug.core.model.IStep

  def canStepInto(): Boolean = false // TODO: need real logic
  def canStepOver(): Boolean = true // TODO: need real logic
  def canStepReturn(): Boolean = false // TODO: need real logic
  def isStepping(): Boolean = ???
  def stepInto(): Unit = ???
  def stepOver(): Unit = thread.stepOver
  def stepReturn(): Unit = ???

  // Members declared in org.eclipse.debug.core.model.ISuspendResume

  def canResume(): Boolean = true
  def canSuspend(): Boolean = false
  def isSuspended(): Boolean = true
  def resume(): Unit = thread.resume
  def suspend(): Unit = ???

  // ---

  fireCreationEvent

  val variables: Seq[ScalaVariable] = {
    import scala.collection.JavaConverters._
    val visibleVariables= try {
      stackFrame.visibleVariables.asScala.map(new ScalaLocalVariable(_, this))
    } catch {
      case e: AbsentInformationException => Seq()
    }
    if (stackFrame.location.method.isStatic) {
      visibleVariables
    } else {
      new ScalaThisVariable(stackFrame.thisObject, this) +: visibleVariables
    }
  }

  def getSourceName(): String = stackFrame.location.sourceName
  
  def getMethodFullName(): String = {
    getFullName(stackFrame.location.method)
  }
  
  def rebind(newStackFrame: StackFrame) {
    stackFrame= newStackFrame
  }

}