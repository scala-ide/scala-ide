package scala.tools.eclipse.buildmanager

import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IMarker
import scala.tools.eclipse.resources.MarkerFactory
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.resources.MarkerFactory.{ Position, NoPosition, RegionPosition }

import scala.reflect.internal.util.{ Position => ScalacPosition }

/** Factory for creating markers used to report build problems (i.e., compilation errors). */
object BuildProblemMarker extends MarkerFactory(ScalaPlugin.plugin.problemMarkerId) {
  /** Create a marker indicating an error state for the passed Scala `project`. */
  def create(project: ScalaProject, e: Throwable): Unit =
    create(project.underlying, "Error in Scala compiler: " + e.getMessage)

  /** Create a marker indicating an error state for the passed `resource`. */
  def create(resource: IResource, msg: String): Unit =
    create(resource, IMarker.SEVERITY_ERROR, msg)

  /** Create marker with a source position in the Problem view.
   *  @param resource The resource to use to create the marker (hence, the marker will be associated to the passed resource)
   *  @param severity Indicates the marker's error state. Its value can be one of:
   *                 [IMarker.SEVERITY_ERROR, IMarker.SEVERITY_WARNING, IMarker.SEVERITY_INFO]
   *  @param msg      The text message displayed by the marker. Note, the passed message is truncated to 21000 chars.
   *  @param pos      The source position for the marker.
   */
  def create(resource: IResource, severity: Int, msg: String, pos: ScalacPosition): Unit =
    create(resource, severity, msg, position(pos))

  private def position(pos: ScalacPosition): Position = {
    if (pos.isDefined) {
      val source = pos.source
      val length = source.identifier(pos).map(_.length).getOrElse(0)
      RegionPosition(pos.point, length, pos.line)
    } else NoPosition
  }
}
