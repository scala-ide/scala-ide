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

  // regexp for JNI signature
  final val typeSignature = """L([^;]*);""".r
  final val arraySignature = """\[(.*)""".r
  final val argumentsInMethodSignature = """\(([^\)]*)\).*""".r
  
  def getSimpleName(signature: String): String = {
    signature match {
      case typeSignature(typeName) =>
        NameTransformer.decode(typeName.split('/').last)
      case arraySignature(elementSignature) =>
        "Array[%s]".format(getSimpleName(elementSignature))
      case "B" =>
        "Byte"
      case "C" =>
        "Char"
      case "D" =>
        "Double"
      case "F" =>
        "Float"
      case "I" =>
        "Int"
      case "J" =>
        "Long"
      case "S" =>
        "Short"
      case "Z" =>
        "Boolean"
    }
  }
  
  // TODO: need unit tests
  def getArgumentSimpleNames(methodSignature: String): List[String] = {
    val argumentsInMethodSignature(argString)= methodSignature
    
    def parseArguments(args: String) : List[String] = {
      if (args.isEmpty) {
        Nil
      } else {
        args.head match {
          case 'L' =>
            val typeSignatureLength= args.indexOf(';') + 1
            getSimpleName(args.substring(0, typeSignatureLength)) +: parseArguments(args.substring(typeSignatureLength))
          case '[' =>
            val parsedArguments= parseArguments(args.tail)
            "Array[%s]".format(parsedArguments.head) +: parsedArguments.tail
          case c =>
            getSimpleName(c.toString) +: parseArguments(args.tail)
        }
      }
    }
    
    parseArguments(argString)
  }

  def getFullName(method: Method): String = {
//    import scala.collection.JavaConverters._
    "%s.%s(%s)".format(
      getSimpleName(method.declaringType.signature),
      NameTransformer.decode(method.name),
      getArgumentSimpleNames(method.signature).mkString(", "))
//      method.arguments.asScala.map(a => getSimpleName(a.signature)).mkString(", "))
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

  def canStepInto(): Boolean = true // TODO: need real logic
  def canStepOver(): Boolean = true // TODO: need real logic
  def canStepReturn(): Boolean = true // TODO: need real logic
  def isStepping(): Boolean = ???
  def stepInto(): Unit = thread.stepInto
  def stepOver(): Unit = thread.stepOver
  def stepReturn(): Unit = thread.stepReturn

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
    val visibleVariables = try {
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
    stackFrame = newStackFrame
  }

}