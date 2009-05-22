/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import scala.collection.JavaConversions._

import org.eclipse.core.filebuffers.FileBuffers
import org.eclipse.core.resources.{ IFile, IMarker, IResource, IWorkspaceRunnable }
import org.eclipse.core.runtime.{ IProgressMonitor }
import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProposal
import org.eclipse.jface.text.{ ITextViewer, Position, TextPresentation }
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.source.Annotation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.{ IWorkbenchPage, PlatformUI }
import org.eclipse.ui.ide.IDE

import scala.tools.eclipse.util.{ EclipseResource, Style } 

object ScalaFile {
  def apply(file : IFile) = new ScalaFile(file)
}

class ScalaFile(val underlying : IFile) {
  import ScalaPlugin.plugin
  
  def project = plugin.projectSafe(underlying.getProject).get
  def viewer : Option[ScalaSourceViewer] = plugin.viewers.get(this)
  def editor : Option[Editor] = viewer.map(_.editor)
  
  override def toString = underlying.toString

  def length = {
    val fs = FileBuffers.getFileStoreAtLocation(underlying.getLocation)
    if (fs != null)
      fs.fetchInfo.getLength.toInt
    else
      -1
  }
  
  def doComplete(offset : Int) : List[ICompletionProposal] = Nil // TODO reinstate
  
  def clearBuildErrors(monitor : IProgressMonitor) =
    underlying.getWorkspace.run(new IWorkspaceRunnable {
      def run(monitor : IProgressMonitor) = underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    }, monitor)
  
  def hasBuildErrors : Boolean =
    underlying.findMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE).exists(_.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR)
  
  def buildError(severity : Int, msg : String, offset : Int, length : Int, monitor : IProgressMonitor) =
    underlying.getWorkspace.run(new IWorkspaceRunnable {
      def run(monitor : IProgressMonitor) = {
        val mrk = underlying.createMarker(plugin.problemMarkerId)
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
          val line = toLine(offset)
          if (!line.isEmpty) 
            mrk.setAttribute(IMarker.LINE_NUMBER, line.get)
        }
      }
    }, monitor)
  
  def toLine(offset : Int) : Option[Int] = None
  
  def Annotation(kind : String, text : String, offset : => Option[Int], length : Int) : Annotation = {
    val a = new Annotation(kind, false, text)
    plugin.asyncUI{
      val model = editor.map(_.getSourceViewer0.getAnnotationModel) getOrElse null
      val offset0 = offset
      if (model != null && offset0.isDefined) {
        model.addAnnotation(a, new Position(offset0.get, length))
      } 
    }
    a
  }
  
  def delete(a : Annotation) : Unit = plugin.asyncUI{
    val model = editor.map(_.getSourceViewer0.getAnnotationModel) getOrElse null
    if (model != null) model.removeAnnotation(a)
  }
  
  def highlight(offset0 : Int, length : Int, style : Style, txt : TextPresentation) : Unit = {
    val viewer = this.viewer
    if (viewer.isEmpty) return
    val sv = viewer.get
    project.highlight(sv, offset0, length, style, txt)
  }

  def invalidate(start : Int, end : Int, txt : PresentationContext) : Unit = {
    txt.invalidate.get(start) match {
      case Some(end0) =>
        if (end > end0) txt.invalidate(start) = end
      case None => txt.invalidate(start) = end
    }
  }

  def createPresentationContext : PresentationContext = new PresentationContext

  def finishPresentationContext(txt : PresentationContext) : Unit = if (!viewer.isEmpty) {
    val viewer = this.viewer.get
    if (viewer.getProjectionAnnotationModel != null) 
      viewer.getProjectionAnnotationModel.replaceAnnotations(txt.remove.toArray,txt.add)
    // highlight
    val i = txt.invalidate.elements
    if (!i.hasNext) return
    var current = i.next
    var toInvalidate = List[(Int,Int)]()
    def flush = {
      toInvalidate = current :: toInvalidate
      current = null
    }
    while (i.hasNext) {
      val (start,end) = i.next
      assert(start > current._1)
      if (false && end >= current._2) current = (current._1, end)
      else if (start <= current._2) {
        if (end > current._2) current = (current._1, end)
      } else {
        flush
        current = (start,end)
      }
    }
    flush
    if (plugin.inUIThread) {
      val i = toInvalidate.elements
      while (i.hasNext) i.next match {
      case (start,end) => viewer.invalidateTextPresentation(start, end - start)
      }
    } else {
      val display = Display.getDefault
      display.asyncExec(new Runnable { // so we happen later.
        def run = toInvalidate.foreach{
        case (start,end) => viewer.invalidateTextPresentation(start, end - start)
      }
      })
    }
  }
    
  def doPresentation : Unit = {
    // TODO reinstate
  }
  
  def isLoaded = plugin.viewers.contains(this)
  
  def doLoad : Unit =
    if (!isLoaded) {
      val wb = PlatformUI.getWorkbench
      val page = wb.getActiveWorkbenchWindow.getActivePage
      val editor = doLoad0(page)
      if(editor.isInstanceOf[Editor] && !isLoaded)
        plugin.logError("can't load: " + this,null)
    }
  
  def doUnload : Unit =
    if(isLoaded)
      plugin.viewers.removeKey(this)
  
  def newError(msg : String) = new Annotation(plugin.ERROR_TYPE, false, msg)
  
  def isAt(a : Annotation, offset : Int) : Boolean = {
    val model = editor.get.getSourceViewer0.getAnnotationModel
    if (model != null) {
      val pos = model.getPosition(a)
      pos != null && pos.getOffset == offset
    } else false
  }
    
  def install(offset : Int, length : Int, a : Annotation) = {
    val sv = editor.get.getSourceViewer0
    if (sv.getAnnotationModel != null)
      (sv.getAnnotationModel.addAnnotation(a, new org.eclipse.jface.text.Position(offset, length)))
  }
    
  def uninstall(a : Annotation) : Unit = {
    if (editor.isEmpty) return
    val sv = editor.get.getSourceViewer0
    if (sv.getAnnotationModel != null) {
      sv.getAnnotationModel.removeAnnotation(a)
      a.markDeleted(true)
    }
  }
  
  def Completion(offset : Int, length : Int, text : String, 
    info : Option[String], image : Option[Image], additional : => Option[String]) = {
    new JavaCompletionProposal(text, offset, length, image getOrElse null, text + info.getOrElse(""), 0) {
      override def apply(viewer : ITextViewer, trigger : Char, stateMask : Int, offset : Int) {
        super.apply(viewer, trigger, stateMask, offset)
      }
    }
  }
  
  def sourcePackage : Option[String] =
    project.sourceFolders.find(_.getLocation.isPrefixOf(underlying.getLocation)) match {
      case Some(fldr) =>
        var path = underlying.getLocation.removeFirstSegments(fldr.getLocation.segmentCount)
        path = path.removeLastSegments(1).removeTrailingSeparator
        Some(path.segments.mkString("", ".", ""))
      case None => None
    }
    
  def doLoad0(page : IWorkbenchPage) = IDE.openEditor(page, underlying, true)
}
