package org.scalaide.core.internal.quickfix

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IProject
import org.eclipse.ui.IMarkerResolution
import org.eclipse.ui.IMarkerResolutionGenerator
import org.eclipse.ui.internal.dialogs.PropertyDialog
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.scalaide.util.internal.eclipse.SWTUtils
import org.scalaide.util.internal.ui.DisplayThread

class BadScalaVersionMarkerResolver extends IMarkerResolutionGenerator {

 def getResolutions(marker: IMarker):Array[IMarkerResolution] = {
   val resolution = new IMarkerResolution() {
     def getLabel(): String = "Open Project Preferences to set a compatible Scala Installation"
     def run(marker:IMarker): Unit = {
       DisplayThread.asyncExec(PropertyDialog.createDialogOn(SWTUtils.getShell, CompilerSettings.PAGE_ID, marker.getResource().asInstanceOf[IProject]).open())
     }
   }

   Array(resolution)
 }

}