package scala.tools.eclipse.debug.model

import scala.tools.eclipse.debug.ScalaDebugger

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.debug.core.model.IValue
import org.eclipse.debug.internal.ui.views.variables.IndexedVariablePartition
import org.eclipse.debug.ui.{ IValueDetailListener, IDebugUIConstants, IDebugModelPresentation, DebugUITools }
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.ui.IEditorInput

import com.sun.jdi.ArrayReference
import com.sun.jdi.BooleanValue
import com.sun.jdi.ByteValue
import com.sun.jdi.CharValue
import com.sun.jdi.ClassType
import com.sun.jdi.DoubleValue
import com.sun.jdi.FloatValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.PrimitiveValue
import com.sun.jdi.ShortValue
import com.sun.jdi.StringReference
import com.sun.jdi.Value
import com.sun.jdi.VoidValue

/**
 * Utility methods for the ScalaDebugModelPresentation class
 * This object doesn't use any internal field, and is thread safe.
 */
object ScalaDebugModelPresentation {
  def computeDetail(value: IValue): String = {
    value match {
      case v: ScalaPrimitiveValue =>
        v.getValueString
      case v: ScalaStringReference =>
        v.stringReference.value
      case v: ScalaNullValue =>
        "null"
      case arrayReference: ScalaArrayReference =>
        computeDetail(arrayReference.arrayReference)
      case objecReference: ScalaObjectReference =>
        computeDetail(objecReference.objectReference)
      case _ =>
        ???
    }
  }
  
  def computeDetail(arrayReference: ArrayReference): String = {
    import scala.collection.JavaConverters._
    // There's a bug in the JDI implementation provided by the JDT, calling getValues()
    // on an array of size zero generates a java.lang.IndexOutOfBoundsException
    if (arrayReference.length == 0) {
      "Array()"
    } else {
      arrayReference.getValues.asScala.map(computeDetail(_)).mkString("Array(", ", ", ")")
    }
  }
  
  def computeDetail(objectReference: ObjectReference): String = {
    val method= objectReference.referenceType.asInstanceOf[ClassType].concreteMethodByName("toString", "()Ljava/lang/String;")
    // TODO: check toString() return null
    ScalaDebugger.currentThread.invokeMethod(objectReference, method).asInstanceOf[StringReference].value
  }
  
  def computeDetail(value: Value): String = {
    // TODO: some of this is duplicate of ScalaValue#apply()
    value match {
      case primitiveValue: PrimitiveValue =>
        computeDetail(primitiveValue)
      case arrayReference: ArrayReference =>
        computeDetail(arrayReference)
      case stringReference: StringReference =>
        stringReference.value
      case objectReference: ObjectReference => // include ClassLoaderReference, ClassObjectReference, ThreadGroupReference, ThreadReference
        computeDetail(objectReference)
      case null =>
        // TODO : cache
        "null"
      case voidValue: VoidValue =>
        ??? // TODO: in what cases do we get this value ?
      case _ =>
        ???
    }
  }
  
  def computeDetail(value: PrimitiveValue): String = {
    value match {
      case booleanValue: BooleanValue =>
        booleanValue.value.toString
      case byteValue: ByteValue =>
        byteValue.value.toString
      case charValue: CharValue =>
        charValue.value.toString
      case doubleValue: DoubleValue =>
        doubleValue.value.toString
      case floatValue: FloatValue =>
        floatValue.value.toString
      case integerValue: IntegerValue =>
        integerValue.value.toString
      case longValue: LongValue =>
        longValue.value.toString
      case shortValue: ShortValue =>
        shortValue.value.toString
    }
  }
}

/**
 * Generate the elements used by the UI.
 * This class doesn't use any internal field, and is thread safe.
 */
class ScalaDebugModelPresentation extends IDebugModelPresentation {

  // Members declared in org.eclipse.jface.viewers.IBaseLabelProvider

  def addListener(x$1: org.eclipse.jface.viewers.ILabelProviderListener): Unit = ???
  def dispose(): Unit = {} // TODO: need real logic
  def isLabelProperty(x$1: Any, x$2: String): Boolean = ???
  def removeListener(x$1: org.eclipse.jface.viewers.ILabelProviderListener): Unit = ???

  // Members declared in org.eclipse.debug.ui.IDebugModelPresentation

  def computeDetail(value: IValue, listener: IValueDetailListener): Unit = {
    new Job("Computing Scala debug details") {
      override def run(progressMonitor: IProgressMonitor): IStatus = {
        // TODO: support error cases
        listener.detailComputed(value, ScalaDebugModelPresentation.computeDetail(value))
        Status.OK_STATUS
      }
    }.schedule
  }

  def getImage(element: Any): org.eclipse.swt.graphics.Image = {
    element match {
      case target: ScalaDebugTarget =>
        // TODO: right image depending of state
        DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_DEBUG_TARGET)
      case thread: ScalaThread =>
        // TODO: right image depending of state
        DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_THREAD_RUNNING)
      case stackFrame: ScalaStackFrame =>
        // TODO: right image depending of state
        DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_STACKFRAME)
      case variable: ScalaVariable =>
        // TODO: right image depending on ?
        DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_VARIABLE)
      case variable: IndexedVariablePartition =>
        // variable used to split large arrays
        // TODO: see ScalaVariable before
        DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_VARIABLE)
    }
  }

  def getText(element: Any): String = {
    element match {
      case target: ScalaDebugTarget =>
        target.getName // TODO: everything
      case thread: ScalaThread =>
        getScalaThreadText(thread)
      case stackFrame: ScalaStackFrame =>
        getScalaStackFrameText(stackFrame)
    }
  }

  /** Currently we don't support any attributes. The standard one,
   *  `show type names`, might get here but we ignore it.
   */
  def setAttribute(key: String, value: Any): Unit = {}

  // Members declared in org.eclipse.debug.ui.ISourcePresentation

  def getEditorId(input: IEditorInput, element: Any): String = {
    EditorUtility.getEditorID(input)
  }

  def getEditorInput(input: Any): IEditorInput = {
    EditorUtility.getEditorInput(input)
  }

  // ----

  /*
   * TODO: add support for thread state (running, suspended at ...)
   */
  def getScalaThreadText(thread: ScalaThread): String = {
    if (thread.isSystemThread)
      "Deamon System Thread [%s]".format(thread.getName)
    else
      "Thread [%s]".format(thread.getName)
  }

  /*
   * TODO: support for missing line numbers
   */
  def getScalaStackFrameText(stackFrame: ScalaStackFrame): String = {
    "%s line: %s".format(stackFrame.getMethodFullName, {
      val lineNumber = stackFrame.getLineNumber
      if (lineNumber == -1) {
        "not available"
      } else {
        lineNumber.toString
      }
    })
  }

}