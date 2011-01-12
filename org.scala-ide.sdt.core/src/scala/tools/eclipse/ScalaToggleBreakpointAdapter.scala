/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.util.HashMap

import org.eclipse.core.runtime.{ CoreException, IProgressMonitor, IStatus, Status }
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.resources.{IResource, ResourcesPlugin}
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.jdt.core.{ IJavaElement, IMember, IType }
import org.eclipse.jdt.debug.core.JDIDebugModel
import org.eclipse.jdt.internal.debug.ui.{ BreakpointUtils, JDIDebugUIPlugin }
import org.eclipse.jdt.internal.debug.ui.actions.{ ActionMessages, ToggleBreakpointAdapter }
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.text.{ BadLocationException, ITextSelection }
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.{IWorkbenchPart, IFileEditorInput, IPathEditorInput}

import scala.tools.eclipse.util.{ReflectionUtils, IDESettings}

class ScalaToggleBreakpointAdapter extends ToggleBreakpointAdapter { self =>
  import ScalaToggleBreakpointAdapterUtils._
  
  private def toggleLineBreakpointsImpl(part : IWorkbenchPart, selection : ISelection) {
    val job = new Job("Toggle Line Breakpoint") {
      override def run(monitor : IProgressMonitor) : IStatus = {
        if (monitor.isCanceled)
          return Status.CANCEL_STATUS
        try {
          selection match {
            case sel : ITextSelection => {
              toggleBreakPoint(part, sel)
              Status.OK_STATUS
            }
            case _ => {
              ScalaPlugin.plugin.logWarning("toggle BreakPoint on non ITextSelection is not supported")
              Status.CANCEL_STATUS
            }
          }
        } catch {
          case ce : CoreException => return ce.getStatus
        }
      }
      def toggleBreakPoint(part : IWorkbenchPart, selection : ITextSelection) {
        val otpe = findType(selection)
        val oresource = findResource(part) orElse otpe.flatMap(x => findResource(x))
        oresource match {
          case None => report(ActionMessages.ToggleBreakpointAdapter_3, part)
          case Some(resource : IResource) => {
            val tname = otpe.map(fqn).getOrElse("<type_undef>")
            val lnumber = selection.getStartLine + 1
            val existingBreakpoint = JDIDebugModel.lineBreakpointExists(resource, tname, lnumber)
            if (existingBreakpoint != null) {
              DebugPlugin.getDefault().getBreakpointManager.removeBreakpoint(existingBreakpoint, true)
            } else {
              val oattributes = otpe.map(x => findAttributes(x, selection.getOffset, selection.getLength))
              JDIDebugModel.createLineBreakpoint(resource, tname, lnumber, -1, -1, 0, true, oattributes.getOrElse(null))        
            }
          }
        }
      }
      def findResource(part : IWorkbenchPart) : Option[IResource] = {
        Option(self.getTextEditor(part)).flatMap {
          _.getEditorInput match {
            case e : IFileEditorInput => Some(e.getFile)
            case e : IPathEditorInput => Option(ResourcesPlugin.getWorkspace.getRoot.findMember(e.getPath))
            case _ => None
          }
        }
      }
      def findResource(tpe : IType) : Option[IResource] = {
        Option(BreakpointUtils.getBreakpointResource(tpe))
      }
      def findType(selection : ISelection) : Option[IType] = {
        translateToMembers(part, selection) match {
          case sel: IStructuredSelection => {
            val member =  sel.getFirstElement.asInstanceOf[IMember]        
            val tpe = if(member.getElementType == IJavaElement.TYPE)
              member.asInstanceOf[IType]
            else
              member.getDeclaringType
            Option(tpe)
          }
          case _ => None
        }
      }
      def fqn(tpe : IType) : String = {
        val qtname = createQualifiedTypeName(self, tpe)
        val emptyPackagePrefix = "<empty>." 
        if (qtname startsWith emptyPackagePrefix) qtname.substring(emptyPackagePrefix.length) else qtname
      }
      def findAttributes(tpe: IType, offset : Int, length : Int) = {
        val attributes = new HashMap[AnyRef, AnyRef](10)
        try {
//          val line = document.getLineInformation(lnumber-1)
//          val start = line.getOffset
//          val end = start+line.getLength-1
          BreakpointUtils.addJavaBreakpointAttributesWithMemberDetails(attributes, tpe, offset, offset + length)
        } catch {
          case ble : BadLocationException => JDIDebugUIPlugin.log(ble)
        }
        attributes
      }
    }
    job.setPriority(Job.INTERACTIVE)
    job.setSystem(true)
    job.schedule()
  }

  override def toggleBreakpoints(part : IWorkbenchPart, selection : ISelection) {
    if (IDESettings.classBreakpoint.value) {
      translateToMembers(part, selection) match {
        case sel : IStructuredSelection if sel.getFirstElement.asInstanceOf[IMember].getElementType == IJavaElement.TYPE => {
          toggleClassBreakpoints(part, sel)
        }
        case _ => toggleLineBreakpointsImpl(part, selection)
      }
    } else {
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
