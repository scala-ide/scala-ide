package scala.tools.eclipse.debug.model

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.reflect.NameTransformer

import org.eclipse.debug.core.model.IRegisterGroup
import org.eclipse.debug.core.model.IStackFrame
import org.eclipse.debug.core.model.IThread
import org.eclipse.debug.core.model.IVariable

import com.sun.jdi.AbsentInformationException
import com.sun.jdi.Method
import com.sun.jdi.StackFrame

object ScalaStackFrame {
  
  def apply(thread: ScalaThread, stackFrame: StackFrame): ScalaStackFrame = {
    val scalaStackFrame= new ScalaStackFrame(thread, stackFrame)
    scalaStackFrame.fireCreationEvent()
    scalaStackFrame
  }

  // regexp for JNI signature
  private val typeSignature = """L([^;]*);""".r
  private val arraySignature = """\[(.*)""".r
  private val argumentsInMethodSignature = """\(([^\)]*)\).*""".r
  
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
    "%s.%s(%s)".format(
      getSimpleName(method.declaringType.signature),
      NameTransformer.decode(method.name),
      getArgumentSimpleNames(method.signature).mkString(", "))
  }
}

/**
 * A stack frame in the Scala debug model.
 * This class is NOT thread safe. 'stackFrame' variable can be 're-bound' at any time.
 * Instances have be created through its companion object.
 */
class ScalaStackFrame private (val thread: ScalaThread, @volatile var stackFrame: StackFrame) extends ScalaDebugElement(thread.getDebugTarget) with IStackFrame {
  import ScalaStackFrame._

  // Members declared in org.eclipse.debug.core.model.IStackFrame

  def getCharEnd(): Int = -1
  def getCharStart(): Int = -1
  def getLineNumber(): Int = stackFrame.location.lineNumber // TODO: cache data ?
  def getName(): String = stackFrame.location.declaringType.name // TODO: cache data ?
  def getRegisterGroups(): Array[IRegisterGroup] = ???
  def getThread(): IThread = thread
  def getVariables(): Array[IVariable] = variables.toArray // TODO: need real logic
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

  val variables: Seq[ScalaVariable] = {
    import scala.collection.JavaConverters._
    val visibleVariables = try {
      stackFrame.visibleVariables.asScala.map(new ScalaLocalVariable(_, this))
    } catch {
      case e: AbsentInformationException => Seq()
    }
    val currentMethod = stackFrame.location.method
    if (currentMethod.isNative || currentMethod.isStatic) {
      // 'this' is not available for native and static methods
      visibleVariables
    } else {
      new ScalaThisVariable(stackFrame.thisObject, this) +: visibleVariables
    }
  }

  def getSourceName(): String = stackFrame.location.sourceName
  
  /**
   * Return the source path based on source name and the package.
   * Segments are separated by '/'.
   */
  def getSourcePath(): String = {
    // we shoudn't use location#sourcePath, as it is platform dependent
    stackFrame.location.declaringType.name.split('.').init match {
      case Array() =>
        getSourceName
      case packageSegments =>
        packageSegments.mkString("", "/", "/") + getSourceName
    }
  }

  def getMethodFullName(): String = {
    getFullName(stackFrame.location.method)
  }

  def rebind(newStackFrame: StackFrame) {
    stackFrame = newStackFrame
  }

}