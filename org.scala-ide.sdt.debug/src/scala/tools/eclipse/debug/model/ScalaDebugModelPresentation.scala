package scala.tools.eclipse.debug.model

import org.eclipse.core.resources.IFile
import org.eclipse.debug.core.model.IValue
import org.eclipse.debug.ui.{IValueDetailListener, IDebugUIConstants, IDebugModelPresentation, DebugUITools}
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.{IFileEditorInput, IEditorInput}

class ScalaDebugModelPresentation extends IDebugModelPresentation {

  // Members declared in org.eclipse.jface.viewers.IBaseLabelProvider

  def addListener(x$1: org.eclipse.jface.viewers.ILabelProviderListener): Unit = ???
  def dispose(): Unit = {} // TODO: need real logic
  def isLabelProperty(x$1: Any, x$2: String): Boolean = ???
  def removeListener(x$1: org.eclipse.jface.viewers.ILabelProviderListener): Unit = ???

  // Members declared in org.eclipse.debug.ui.IDebugModelPresentation

  def computeDetail(value: IValue, listener: IValueDetailListener): Unit = {
    // TODO: the real work
    listener.detailComputed(value, null)
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
      case _ =>
        ???
    }
  }

  def getText(element: Any): String = {
    element match {
      case target: ScalaDebugTarget =>
        target.getName // TODO: everything
      case thread: ScalaThread =>
        thread.getName // TODO: everything
      case stackFrame: ScalaStackFrame =>
        stackFrame.getName // TODO: everything
      case _ =>
        ???
    }
  }

  def setAttribute(x$1: String, x$2: Any): Unit = ???

  // Members declared in org.eclipse.debug.ui.ISourcePresentation

  def getEditorId(input: IEditorInput, element: Any): String = {
    input match {
      case fileInput: IFileEditorInput =>
        IDE.getEditorDescriptor(fileInput.getFile).getId
      case _ =>
        null
    }
  }

  def getEditorInput(input: Any): IEditorInput = {
    input match {
      case file: IFile =>
        new FileEditorInput(file)
      case _ =>
        ???
    }
  }

  // ----

}