package scala.tools.eclipse.debug.command

import org.eclipse.debug.ui.actions.IRunToLineTarget
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.jface.viewers.ISelection
import org.eclipse.debug.core.model.ISuspendResume
import org.eclipse.ui.texteditor.ITextEditor
import org.hamcrest.core.IsInstanceOf
import org.eclipse.jdt.debug.core.IJavaStackFrame
import org.eclipse.debug.core.model.IStackFrame
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jdt.debug.core.JDIDebugModel
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.jdt.internal.debug.ui.BreakpointUtils
import java.util.HashMap
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.debug.core.model.IDebugTarget
import org.eclipse.core.runtime.NullProgressMonitor

class RunToLineAdapter extends IRunToLineTarget {

  def runToLine(part: IWorkbenchPart, selection: ISelection, target: ISuspendResume): Unit = {
    val textEditor = getTextEditor(part);
    val isInstanceOfIJavaStackFrame = target.isInstanceOf[IJavaStackFrame]
    val isIStackFrame = target.isInstanceOf[IStackFrame]
    if (isInstanceOfIJavaStackFrame) println("dd")
    val theType = if (isInstanceOfIJavaStackFrame)
      (target.asInstanceOf[IJavaStackFrame]).getReceivingTypeName()
    else if (isIStackFrame)
      (target.asInstanceOf[IStackFrame]).getName()
    else null

    val typeNames = new Array[String](1);
    typeNames(0) = theType
    val textSelection = selection.asInstanceOf[ITextSelection];
    val lineNumber = new Array[Int](1);
    lineNumber(0) = textSelection.getStartLine() + 1;

    val attributes = new HashMap[String, Object]()
    BreakpointUtils.addRunToLineAttributes(attributes);
    val breakpoint = JDIDebugModel.createLineBreakpoint(ResourcesPlugin.getWorkspace().getRoot(), typeNames(0), lineNumber(0), -1, -1, 1, false, attributes);
    if (target.isInstanceOf[IAdaptable]) {
      val debugTarget = ((target.asInstanceOf[IAdaptable]).getAdapter(classOf[IDebugTarget])).asInstanceOf[IDebugTarget]
      if (debugTarget != null) {
        val handler = new RunToLineHandler(debugTarget, target, breakpoint);
        handler.run(new NullProgressMonitor());
        return ;
      }
    }
  }

  def canRunToLine(part: IWorkbenchPart, selection: ISelection, target: ISuspendResume): Boolean = {
    true
  }

  def getTextEditor(part: IWorkbenchPart): ITextEditor = {
    if (part.isInstanceOf[ITextEditor]) part.asInstanceOf[ITextEditor]
    else (part.getAdapter(classOf[ITextEditor])).asInstanceOf[ITextEditor];
  }
}