/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import scala.collection.JavaConversions._

import org.eclipse.core.filebuffers.FileBuffers
import org.eclipse.core.resources.{ IFile, IMarker, IResource, IWorkspaceRunnable }
import org.eclipse.core.runtime.{ IProgressMonitor }
import org.eclipse.jface.text.{ ITextViewer, Position, TextPresentation }
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.{ IWorkbenchPage, PlatformUI }
import org.eclipse.ui.ide.IDE

import scala.tools.eclipse.ScalaPlugin

object FileUtils {
  import ScalaPlugin.plugin
  
  def length(file : IFile) = {
    val fs = FileBuffers.getFileStoreAtLocation(file.getLocation)
    if (fs != null)
      fs.fetchInfo.getLength.toInt
    else
      -1
  }
  
  def clearBuildErrors(file : IFile, monitor : IProgressMonitor) =
    file.getWorkspace.run(new IWorkspaceRunnable {
      def run(monitor : IProgressMonitor) = file.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    }, monitor)
  
  def hasBuildErrors(file : IFile) : Boolean =
    file.findMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE).exists(_.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR)
  
  def buildError(file : IFile, severity : Int, msg : String, offset : Int, length : Int, line : Int, monitor : IProgressMonitor) =
    file.getWorkspace.run(new IWorkspaceRunnable {
      def run(monitor : IProgressMonitor) = {
        val mrk = file.createMarker(plugin.problemMarkerId)
        mrk.setAttribute(IMarker.SEVERITY, severity)
        val string = msg.map{
          case '\n' => ' '
          case '\r' => ' '
          case c => c
        }.mkString("","","")
        
        mrk.setAttribute(IMarker.MESSAGE , msg)
        if (offset != -1) {
          mrk.setAttribute(IMarker.CHAR_START, offset)
          mrk.setAttribute(IMarker.CHAR_END  , offset + length)
          mrk.setAttribute(IMarker.LINE_NUMBER, line)
        }
      }
    }, monitor)
}
