/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.core.resources.{ IResource, IFile, IMarker, IWorkspaceRunnable }
import org.eclipse.core.runtime.IProgressMonitor
import scala.tools.nsc.interactive.{BuildManager, RefinedBuildManager}
import scala.tools.eclipse.util.{ EclipseResource, FileUtils }
import scala.tools.eclipse.util.IDESettings
import scala.tools.eclipse.util.Defensive

trait EclipseBuildManager extends BuildManager {
  def build(addedOrUpdated: Set[IFile], removed: Set[IFile])(implicit monitor: IProgressMonitor = null): Unit
  var depFile: IFile
  var hasErrors = false
  //def invalidateAfterLoad: Boolean
  def clean(implicit monitor: IProgressMonitor): Unit
  def project : ScalaProject
  
  def buildError(severity : Int, msg : String) = Defensive.askRunInJob("build error"){
        val mrk = project.underlying.createMarker(ScalaPlugin.plugin.problemMarkerId)
        mrk.setAttribute(IMarker.SEVERITY, severity)
        val string = msg.map{
          case '\n' => ' '
          case '\r' => ' '
          case c => c
        }.mkString("","","")
        mrk.setAttribute(IMarker.MESSAGE , msg)
  }

  def buildError(file : IFile, severity : Int, msg : String, offset : Int, length : Int, line : Int)(implicit monitor: IProgressMonitor) {
    if (IDESettings.ignoreErrorOnJavaFile.value && file.getFileExtension == "java") {
      return
    }
    FileUtils.buildError(file, severity, msg, offset, length, line, monitor)
  }  

  def clearBuildErrors(implicit monitor : IProgressMonitor) = Defensive.askRunInJob("build error"){
    project.underlying.deleteMarkers(ScalaPlugin.plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
  }
}