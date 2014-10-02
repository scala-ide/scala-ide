package org.scalaide.core.internal.quickfix

import org.eclipse.ui.IMarkerResolutionGenerator
import org.eclipse.ui.IMarkerResolution
import org.eclipse.core.resources.IMarker
import org.scalaide.core.internal.project.Nature
import org.scalaide.util.Utils

class NoScalaLibraryMarkerResolver extends IMarkerResolutionGenerator {
  def getResolutions(marker: IMarker): Array[IMarkerResolution] = {
    val addScalaLibrary = new IMarkerResolution() {
      def getLabel: String = "Add Scala Library to Classpath"
      def run(marker: IMarker): Unit = {
        Utils.tryExecute(Nature.addScalaLibAndSave(marker.getResource.getProject))
      }
    }

    if (marker.getAttribute(IMarker.MESSAGE, "").startsWith("Unable to find a scala library.")) {
      Array(addScalaLibrary)
    } else
      Array()
  }
}