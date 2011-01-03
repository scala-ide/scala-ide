/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.util.HashMap

import org.eclipse.core.runtime.{ CoreException, IProgressMonitor, IStatus, Status }
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.jdt.core.{ IJavaElement, IMember, IType }
import org.eclipse.jdt.debug.core.JDIDebugModel
import org.eclipse.jdt.internal.debug.ui.{ BreakpointUtils, JDIDebugUIPlugin }
import org.eclipse.jdt.internal.debug.ui.actions.{ ActionMessages, ToggleBreakpointAdapter }
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.text.{ BadLocationException, ITextSelection }
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IWorkbenchPart

import scala.tools.eclipse.util.ReflectionUtils

class ScalaToggleBreakpointAdapter extends ToggleBreakpointAdapter { self =>
  import ScalaToggleBreakpointAdapterUtils._
  
  private def toggleLineBreakpointsImpl(part : IWorkbenchPart, selection : ISelection) {
    val job = new Job("Toggle Line Breakpoint") {
      override def run(monitor : IProgressMonitor) : IStatus = {
        val editor = self.getTextEditor(part)
        if (editor != null && selection.isInstanceOf[ITextSelection]) {
          if (monitor.isCanceled)
            return Status.CANCEL_STATUS
          try {
            report(null, part)
            val sel = 
              if(!selection.isInstanceOf[IStructuredSelection])
                translateToMembers(part, selection)
              else
                selection
            
              if(sel.isInstanceOf[IStructuredSelection]) {
                val member = sel.asInstanceOf[IStructuredSelection].getFirstElement.asInstanceOf[IMember]
                val tpe =
                  if(member.getElementType == IJavaElement.TYPE)
                    member.asInstanceOf[IType]
                  else
                    member.getDeclaringType

                val tname = {
                  val qtname = createQualifiedTypeName(self, tpe)
                  val emptyPackagePrefix = "<empty>." 
                  if (qtname startsWith emptyPackagePrefix) qtname.substring(emptyPackagePrefix.length) else qtname
                }
                val resource = BreakpointUtils.getBreakpointResource(tpe)
                val lnumber = selection.asInstanceOf[ITextSelection].getStartLine+1
                val existingBreakpoint = JDIDebugModel.lineBreakpointExists(resource, tname, lnumber)
                if (existingBreakpoint != null) {
                  DebugPlugin.getDefault().getBreakpointManager.removeBreakpoint(existingBreakpoint, true)
                  return Status.OK_STATUS
                }
                val attributes = new HashMap[AnyRef, AnyRef](10)
                val documentProvider = editor.getDocumentProvider
                if (documentProvider == null)
                  return Status.CANCEL_STATUS
                val document = documentProvider.getDocument(editor.getEditorInput)
                try {
                  val line = document.getLineInformation(lnumber-1)
                  val start = line.getOffset
                  val end = start+line.getLength-1
                  BreakpointUtils.addJavaBreakpointAttributesWithMemberDetails(attributes, tpe, start, end)
                } catch {
                  case ble : BadLocationException => JDIDebugUIPlugin.log(ble)
                }
                JDIDebugModel.createLineBreakpoint(resource, tname, lnumber, -1, -1, 0, true, attributes)
              } else {
                report(ActionMessages.ToggleBreakpointAdapter_3, part)
                return Status.OK_STATUS
              }
            } catch {
              case ce : CoreException => return ce.getStatus
            }
          }
          return Status.OK_STATUS
        }
      }
    
    job.setSystem(true)
    job.schedule
  }

  override def toggleBreakpoints(part : IWorkbenchPart, selection : ISelection) {
    val sel = translateToMembers(part, selection)
    if(sel.isInstanceOf[IStructuredSelection]) {
      val member = sel.asInstanceOf[IStructuredSelection].getFirstElement.asInstanceOf[IMember]
      val mtype = member.getElementType
      if(mtype == IJavaElement.FIELD || mtype == IJavaElement.METHOD) {
        if (selection.isInstanceOf[ITextSelection]) {
          val ts = selection.asInstanceOf[ITextSelection]
          toggleLineBreakpointsImpl(part, ts)
        } 
      }
      else if(member.getElementType == IJavaElement.TYPE)
        toggleClassBreakpoints(part, sel)
      else
        toggleLineBreakpointsImpl(part, selection)
    }
  }
  
  override def toggleLineBreakpoints(part : IWorkbenchPart, selection : ISelection) {
    toggleLineBreakpointsImpl(part, selection)
  }
  
  /** override from protected to public method to be accessible from Job created in toggleLineBreakpointsImpl*/
  override def report(message : String, part : IWorkbenchPart) = super.report(message, part)
  /** override from protected to public method to be accessible from Job created in toggleLineBreakpointsImpl*/
  override def getTextEditor(part : IWorkbenchPart) = super.getTextEditor(part)
  /** override from protected to public method to be accessible from Job created in toggleLineBreakpointsImpl*/
  override def translateToMembers(part : IWorkbenchPart, selection : ISelection) = super.translateToMembers(part, selection)
}

object ScalaToggleBreakpointAdapterUtils extends ReflectionUtils {
  val toggleBreakpointAdapterClazz = classOf[ToggleBreakpointAdapter]
  val createQualifiedTypeNameMethod = getDeclaredMethod(toggleBreakpointAdapterClazz, "createQualifiedTypeName", classOf[IType])
  
  def createQualifiedTypeName(tba : ToggleBreakpointAdapter, tpe : IType) = createQualifiedTypeNameMethod.invoke(tba, tpe).asInstanceOf[String]
}
