package scala.tools.eclipse.debug.evaluation

import org.eclipse.jdt.debug.eval.IEvaluationEngine
import org.eclipse.jdt.debug.core.IJavaStackFrame
import org.eclipse.jdt.debug.eval.IEvaluationListener
import org.eclipse.jdt.debug.core.IJavaObject
import org.eclipse.jdt.debug.core.IJavaThread
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.debug.core.IJavaDebugTarget
import scala.tools.eclipse.debug.model.ScalaStackFrame
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import scala.tools.eclipse.debug.model.ScalaThread
import scala.tools.eclipse.debug.model.ScalaObjectReference
import scala.tools.eclipse.debug.model.ScalaClassType
import com.sun.jdi.Value
import scala.tools.eclipse.debug.model.ScalaValue
import com.sun.jdi.ClassType
import scala.tools.eclipse.debug.model.ScalaStringReference
import com.sun.jdi.BooleanValue
import com.sun.jdi.ObjectReference
import scala.tools.eclipse.debug.model.ScalaObjectReference
import scala.tools.eclipse.debug.model.ScalaPrimitiveValue
import scala.tools.eclipse.debug.model.ScalaObjectReference
import scala.tools.eclipse.debug.model.ScalaPrimitiveValue
import org.eclipse.debug.core.model.IValue

object ScalaEvaluationEngine {
  def booleanValue(evaluation: ScalaValue, thread: ScalaThread): Option[Boolean] = {
    evaluation match {
      case bool: ScalaPrimitiveValue if bool.underlying.isInstanceOf[BooleanValue] =>
        Some(bool.underlying.asInstanceOf[BooleanValue].value)
      case obj: ScalaObjectReference if obj.getReferenceTypeName == "java.lang.Boolean" =>
        booleanValue(obj.invokeMethod("booleanValue", thread), thread)
      case _ => None
    }
  }

  def constructorJNISig[T <: AnyVal](arg: T) = {
    val char = arg match {
      case _: Boolean => "Z"
      case _: Byte => "B"
      case _: Char => "C"
      case _: Double => "D"
      case _: Float => "F"
      case _: Int => "I"
      case _: Long => "L"
      case _: Short => "S"
    }
    s"($char)V"
  }
}

class ScalaEvaluationEngine(classpath: Seq[String], val target: ScalaDebugTarget, val thread: ScalaThread) {
  private val repl = {
    val replAssistance = target.objectByName("scala.tools.eclipse.debug.debugged.ReplAssistance", true, thread)
    val cp = createStringList(classpath)
    replAssistance.invokeMethod("createRepl", thread, cp).asInstanceOf[ScalaObjectReference]
  }

  def evaluate(expression: String): Option[ScalaObjectReference] = {
    val expr = ScalaValue(expression, target)
    val interpResult = repl.invokeMethod("interpret", "(Ljava/lang/String;)Lscala/tools/nsc/interpreter/Results$Result;", thread, List(expr): _*).asInstanceOf[ScalaObjectReference]
    val interpResultStr = interpResult.invokeMethod("toString", thread).asInstanceOf[ScalaStringReference]
    (interpResultStr.underlying.value() == "Success") match {
      case false => None
      case true => {
        val lastRequest = repl.invokeMethod("lastRequest", thread).asInstanceOf[ScalaObjectReference]
        val lineRep = lastRequest.invokeMethod("lineRep", thread).asInstanceOf[ScalaObjectReference]
        val nil = target.objectByName("scala.collection.immutable.Nil", true, thread)
        val callOpt = lineRep.invokeMethod("callOpt", thread, ScalaValue("$result", target), nil).asInstanceOf[ScalaObjectReference]
        val call = callOpt.invokeMethod("getOrElse", thread, ScalaValue(null, target)).asInstanceOf[ScalaObjectReference]
        Some(call)
      }
    }
  }

  def bind(name: String, boundType: String, value: ScalaValue, modifiers: List[String] = Nil) {
    val modifiersValue = createStringList(modifiers)
    val jdiSig = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Lscala/collection/immutable/List;)Lscala/tools/nsc/interpreter/Results$Result;"
    repl.invokeMethod("bind", jdiSig, thread, ScalaValue(name, target), ScalaValue(boundType, target), value, modifiersValue)
  }

  def createObject(typeName: String): ScalaObjectReference = {
    import scala.collection.JavaConverters._
    val classObject = target.classByName(typeName, true, thread).asInstanceOf[ScalaClassType]
    val constructor = classObject.classType.concreteMethodByName("<init>", "()V")
    new ScalaObjectReference(classObject.classType.newInstance(thread.threadRef, constructor, List[Value]().asJava, ClassType.INVOKE_SINGLE_THREADED), target)
  }

  private def createStringList(strs: Seq[String]) = {
    val lb = createObject("scala.collection.mutable.ListBuffer")
    for (str <- strs)
      lb.invokeMethod("$plus$eq", "(Ljava/lang/Object;)Lscala/collection/mutable/ListBuffer;", thread, ScalaValue(str, target))
    lb.invokeMethod("toList", thread)
  }

  // FIXME: Not currently working.. Throws an exception
  private def vmPrintln(str: String) {
    val predef = target.objectByName("scala.Predef", true, thread)
    predef.invokeMethod("println", "(Ljava/lang/Object;)V", thread, ScalaValue(str, target))
  }
}