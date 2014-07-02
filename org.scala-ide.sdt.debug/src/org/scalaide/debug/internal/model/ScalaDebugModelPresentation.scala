package org.scalaide.debug.internal.model

import org.scalaide.debug.internal.ScalaDebugger
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.debug.core.model.IValue
import org.eclipse.debug.internal.ui.views.variables.IndexedVariablePartition
import org.eclipse.debug.ui.IValueDetailListener
import org.eclipse.debug.ui.IDebugUIConstants
import org.eclipse.debug.ui.IDebugModelPresentation
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.ui.IEditorInput
import org.eclipse.jface.viewers.ILabelProviderListener
import org.eclipse.debug.core.model.IVariable
import scala.util.Try

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
        v.underlying.value
      case v: ScalaNullValue =>
        "null"
      case arrayReference: ScalaArrayReference =>
        computeDetail(arrayReference)
      case objecReference: ScalaObjectReference =>
        computeDetail(objecReference)
      case _ =>
        ???
    }
  }

  def textFor(variable: IVariable): String = {
    val name = Try{variable.getName} getOrElse "Unavailable Name"
    val value = Try{variable.getValue} map {computeDetail(_)} getOrElse "Unavailable Value"
    s"$name = $value"
  }

  /** Return the a toString() equivalent for an Array
   */
  private def computeDetail(arrayReference: ScalaArrayReference): String = {
    import scala.collection.JavaConverters._
    // There's a bug in the JDI implementation provided by the JDT, calling getValues()
    // on an array of size zero generates a java.lang.IndexOutOfBoundsException
    val array= arrayReference.underlying
    if (array.length == 0) {
      "Array()"
    } else {
      array.getValues.asScala.map(value => computeDetail(ScalaValue(value, arrayReference.getDebugTarget()))).mkString("Array(", ", ", ")")
    }
  }

  /** Return the value produced by calling toString() on the object.
   */
  private def computeDetail(objectReference: ScalaObjectReference): String = {
    try {
      objectReference.invokeMethod("toString", "()Ljava/lang/String;", ScalaDebugger.currentThread) match {
        case s: ScalaStringReference =>
          s.underlying.value
        case n: ScalaNullValue =>
          "null"
      }
    } catch {
      case e: Exception =>
        "exception while invoking toString(): %s\n%s".format(e.getMessage(), e.getStackTraceString)
    }
  }

}

/**
 * Generate the elements used by the UI.
 * This class doesn't use any internal field, and is thread safe.
 */
class ScalaDebugModelPresentation extends IDebugModelPresentation {

  // Members declared in org.eclipse.jface.viewers.IBaseLabelProvider

  override def addListener(listener: ILabelProviderListener): Unit = ???
  override def dispose(): Unit = {} // TODO: need real logic
  override def isLabelProperty(element: Any, property: String): Boolean = ???
  override def removeListener(listener: ILabelProviderListener): Unit = ???

  // Members declared in org.eclipse.debug.ui.IDebugModelPresentation

  override def computeDetail(value: IValue, listener: IValueDetailListener): Unit = {
    new Job("Computing Scala debug details") {
      override def run(progressMonitor: IProgressMonitor): IStatus = {
        // TODO: support error cases
        listener.detailComputed(value, ScalaDebugModelPresentation.computeDetail(value))
        Status.OK_STATUS
      }
    }.schedule()
  }

  override def getImage(element: Any): org.eclipse.swt.graphics.Image = {
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

      case _ => DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_VARIABLE)
    }
  }

  override def getText(element: Any): String = {
    element match {
      case target: ScalaDebugTarget =>
        target.getName // TODO: everything
      case thread: ScalaThread =>
        getScalaThreadText(thread)
      case stackFrame: ScalaStackFrame =>
        getScalaStackFrameText(stackFrame)
      case variable: IVariable =>
        ScalaDebugModelPresentation.textFor(variable)
    }
  }

  /** Currently we don't support any attributes. The standard one,
   *  `show type names`, might get here but we ignore it.
   */
  override def setAttribute(key: String, value: Any): Unit = {}

  // Members declared in org.eclipse.debug.ui.ISourcePresentation

  override def getEditorId(input: IEditorInput, element: Any): String = {
    EditorUtility.getEditorID(input)
  }

  override def getEditorInput(input: Any): IEditorInput = {
    EditorUtility.getEditorInput(input)
  }

  // ----

  /*
   * TODO: add support for thread state (running, suspended at ...)
   */
  def getScalaThreadText(thread: ScalaThread): String = {
    if (thread.isSystemThread)
      "Daemon System Thread [%s]".format(thread.getName)
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
