/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util


import scala.collection.JavaConversions._

import org.eclipse.core.filebuffers.FileBuffers
import org.eclipse.core.internal.resources.ResourceException
import org.eclipse.core.resources.{ IFile, IMarker, IResource }
import org.eclipse.core.runtime.{ IProgressMonitor }
import org.eclipse.jdt.core.{ IJavaModelMarker, JavaCore }
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.core.builder.JavaBuilder
import org.eclipse.jface.text.{ ITextViewer, Position, TextPresentation }
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.{ IWorkbenchPage, PlatformUI }
import org.eclipse.ui.ide.IDE
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path

import scala.tools.nsc.io.AbstractFile
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.util.EclipseUtils._

object FileUtils {
  import ScalaPlugin.plugin
  
  def toIFile(file: AbstractFile): Option[IFile] = file match {
    case null => None
    case EclipseResource(file: IFile) => Some(file)
    case abstractFile =>
      
      val file = ResourcesPlugin.getWorkspace.getRoot.getFileForLocation(Path.fromOSString(abstractFile.path))
      
      if (file == null || !file.exists) {
        None
      } else {
        Some(file)
      }
  }

  
  def length(file : IFile) = {
    val fs = FileBuffers.getFileStoreAtLocation(file.getLocation)
    if (fs != null)
      fs.fetchInfo.getLength.toInt
    else
      -1
  }
  
  def clearBuildErrors(file : IFile, monitor : IProgressMonitor) =
    try {
      workspaceRunnableIn(file.getWorkspace, monitor)( m => file.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE))
    } catch {
      case _ : ResourceException =>
    }
  
  def clearTasks(file : IFile, monitor : IProgressMonitor) =
    try {
      workspaceRunnableIn(file.getWorkspace, monitor)(m => file.deleteMarkers(IJavaModelMarker.TASK_MARKER, true, IResource.DEPTH_INFINITE))
    } catch {
      case _ : ResourceException =>
    }
  
  def findBuildErrors(file : IResource) : Seq[IMarker] =
    file.findMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)

  def hasBuildErrors(file : IResource) : Boolean =
    file.findMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE).exists(_.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR)
  
  def buildError(file : IFile, severity : Int, msg : String, offset : Int, length : Int, line : Int, monitor : IProgressMonitor) =
    workspaceRunnableIn(file.getWorkspace, monitor) { m =>
      val mrk = file.createMarker(plugin.problemMarkerId)
      mrk.setAttribute(IMarker.SEVERITY, severity)
      
      // Marker attribute values are limited to <= 65535 bytes and setAttribute will assert if they
      // exceed this. To guard against this we trim to <= 21000 characters ... see
      // org.eclipse.core.internal.resources.MarkerInfo.checkValidAttribute for justification
      // of this arbitrary looking number
      val maxMarkerLen = 21000
      val trimmedMsg = msg.take(maxMarkerLen)
      
      val attrValue = trimmedMsg.map {
        case '\n' | '\r' => ' '
        case c => c
      }
      
      mrk.setAttribute(IMarker.MESSAGE , attrValue)

      if (offset != -1) {
        mrk.setAttribute(IMarker.CHAR_START, offset)
        mrk.setAttribute(IMarker.CHAR_END, offset + math.max(length, 1))
        mrk.setAttribute(IMarker.LINE_NUMBER, line)
      }
    }

  def task(file: IFile, tag: String, msg: String, priority: String, offset: Int, length: Int, line: Int, monitor: IProgressMonitor) =
    workspaceRunnableIn(file.getWorkspace, monitor) { m =>
      val mrk = file.createMarker(IJavaModelMarker.TASK_MARKER)
      val values = new Array[AnyRef](taskMarkerAttributeNames.length)

      val prioNum = priority match {
        case JavaCore.COMPILER_TASK_PRIORITY_HIGH => IMarker.PRIORITY_HIGH
        case JavaCore.COMPILER_TASK_PRIORITY_LOW  => IMarker.PRIORITY_LOW
        case _                                    => IMarker.PRIORITY_NORMAL
      }

      values(0) = tag + " " + msg
      values(1) = Integer.valueOf(prioNum)
      values(2) = Integer.valueOf(IProblem.Task)
      values(3) = Integer.valueOf(offset)
      values(4) = Integer.valueOf(offset + length + 1)
      values(5) = Integer.valueOf(line)
      values(6) = java.lang.Boolean.valueOf(false)
      values(7) = JavaBuilder.SOURCE_ID
      mrk.setAttributes(taskMarkerAttributeNames, values);
    }

  private val taskMarkerAttributeNames = Array(
    IMarker.MESSAGE,
    IMarker.PRIORITY,
    IJavaModelMarker.ID,
    IMarker.CHAR_START,
    IMarker.CHAR_END,
    IMarker.LINE_NUMBER,
    IMarker.USER_EDITABLE,
    IMarker.SOURCE_ID
  )
}
