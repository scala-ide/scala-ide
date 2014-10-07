package org.scalaide.core.internal.quickfix

import org.eclipse.core.resources.IMarker
import org.eclipse.ui.IMarkerResolution
import org.eclipse.ui.IMarkerResolutionGenerator
import org.scalaide.core.internal.project.Nature
import org.scalaide.util.eclipse.EclipseUtils

class NoScalaLibraryMarkerResolver extends IMarkerResolutionGenerator {
  override def getResolutions(marker: IMarker): Array[IMarkerResolution] = {
    val addScalaLibrary = new IMarkerResolution() {
      override def getLabel: String = "Add Scala Library to Classpath"
      override def run(marker: IMarker): Unit = {
        EclipseUtils.withSafeRunner("Error occurred while adding Scala library to classpath") {
          Nature.addScalaLibAndSave(marker.getResource.getProject)
        }
      }
    }

    if (marker.getAttribute(IMarker.MESSAGE, "").startsWith("Unable to find a scala library.")) {
      Array(addScalaLibrary)
    } else
      Array()
  }
}
