package scala.tools.eclipse.buildmanager

import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IMarker
import scala.tools.eclipse.resources.MarkerFactory
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject

/** Factory for creating markers used to report build problems (i.e., compilation errors). */
object BuildProblemMarker extends MarkerFactory(ScalaPlugin.plugin.problemMarkerId) {
  /** Create a marker indicating an error state for the passed Scala `project`. */
  def create(project: ScalaProject, e: Throwable): Unit =
    create(project.underlying, "Error in Scala compiler: " + e.getMessage)

  /** Create a marker indicating an error state for the passed `resource`. */
  def create(resource: IResource, msg: String): Unit =
    create(resource, IMarker.SEVERITY_ERROR, msg)
}