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
import com.sun.jdi.InvalidStackFrameException
import com.sun.jdi.NativeMethodException

object ScalaStackFrame {

  def apply(thread: ScalaThread, stackFrame: StackFrame): ScalaStackFrame = {
    new ScalaStackFrame(thread, stackFrame)
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
    val argumentsInMethodSignature(argString) = methodSignature

    def parseArguments(args: String) : List[String] = {
      if (args.isEmpty) {
        Nil
      } else {
        args.charAt(0) match {
          case 'L' =>
            val typeSignatureLength = args.indexOf(';') + 1
            getSimpleName(args.substring(0, typeSignatureLength)) +: parseArguments(args.substring(typeSignatureLength))
          case '[' =>
            val parsedArguments = parseArguments(args.tail)
            "Array[%s]".format(parsedArguments.head) +: parsedArguments.tail
          case c =>
            getSimpleName(c.toString) +: parseArguments(args.tail)
        }
      }
    }

    parseArguments(argString)
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

  override def getCharEnd(): Int = -1
  override def getCharStart(): Int = -1
  override def getLineNumber(): Int = {
    (safeStackFrameCalls(-1) or wrapJDIException("Exception while retrieving stack frame's line number")) {
      stackFrame.location.lineNumber // TODO: cache data ?
    }
  }
  override def getName(): String = {
    (safeStackFrameCalls("Error retrieving name") or wrapJDIException("Exception while retrieving stack frame's name")) {
      stackFrame.location.declaringType.name // TODO: cache data ?
    }
  }
  override def getRegisterGroups(): Array[IRegisterGroup] = ???
  override def getThread(): IThread = thread
  override def getVariables(): Array[IVariable] = variables.toArray // TODO: need real logic
  override def hasRegisterGroups(): Boolean = ???
  override def hasVariables(): Boolean = ???

  // Members declared in org.eclipse.debug.core.model.IStep

  override def canStepInto(): Boolean = true // TODO: need real logic
  override def canStepOver(): Boolean = true // TODO: need real logic
  override def canStepReturn(): Boolean = true // TODO: need real logic
  override def isStepping(): Boolean = ???
  override def stepInto(): Unit = thread.stepInto
  override def stepOver(): Unit = thread.stepOver
  override def stepReturn(): Unit = thread.stepReturn

  // Members declared in org.eclipse.debug.core.model.ISuspendResume

  override def canResume(): Boolean = true
  override def canSuspend(): Boolean = false
  override def isSuspended(): Boolean = true
  override def resume(): Unit = thread.resume()
  override def suspend(): Unit = ???

  // ---

  import scala.tools.eclipse.debug.JDIUtil._
  import scala.util.control.Exception
  import Exception.Catch

  lazy val variables: Seq[ScalaVariable] = {
    (safeStackFrameCalls(Nil) or wrapJDIException("Exception while retrieving stack frame's visible variables")) {
      import scala.collection.JavaConverters._
      val visibleVariables = {
        (Exception.handling(classOf[AbsentInformationException]) by (_ => Seq.empty)) {
          stackFrame.visibleVariables.asScala.map(new ScalaLocalVariable(_, this))
        }
      }

      val currentMethod = stackFrame.location.method
      if (currentMethod.isNative || currentMethod.isStatic) {
        // 'this' is not available for native and static methods
        visibleVariables
      } else {
        new ScalaThisVariable(stackFrame.thisObject, this) +: visibleVariables
      }
    }
  }

  private def getSourceName(): String =
    safeStackFrameCalls("Source name not available")(stackFrame.location.sourceName)

  /**
   * Return the source path based on source name and the package.
   * Segments are separated by '/'.
   *
   * @throws DebugException
   */
  def getSourcePath(): String = {
    wrapJDIException("Exception while retrieving source path") {
      // we shoudn't use location#sourcePath, as it is platform dependent
      stackFrame.location.declaringType.name.split('.').init match {
        case Array() =>
          getSourceName
        case packageSegments =>
          packageSegments.mkString("", "/", "/") + getSourceName
      }
    }
  }

  def getMethodFullName(): String = {
    def getFullName(method: Method): String = {
      "%s.%s(%s)".format(
        getSimpleName(method.declaringType.signature),
        NameTransformer.decode(method.name),
        getArgumentSimpleNames(method.signature).mkString(", "))
    }
    safeStackFrameCalls("Error retrieving full name") { getFullName(stackFrame.location.method) }
  }

  /** Set the current stack frame to `newStackFrame`. The `ScalaStackFrame.variables` don't need
    *  to be recomputed because a variable (i.e., a `ScalaLocalVariable`) always uses the latest
    *  stack frame to compute its value, as it can be checked by looking at the implementation of
    *  `ScalaLocalVariable.getValue`
    */
  def rebind(newStackFrame: StackFrame) {
    stackFrame = newStackFrame
  }

  /** Wrap calls to the underlying VM stack frame to handle exceptions gracefully. */
  private def safeStackFrameCalls[A](defaultValue: A): Catch[A] =
    (safeVmCalls(defaultValue)
      or Exception.failAsValue(
        classOf[InvalidStackFrameException],
        classOf[AbsentInformationException],
        classOf[NativeMethodException])(defaultValue))
}