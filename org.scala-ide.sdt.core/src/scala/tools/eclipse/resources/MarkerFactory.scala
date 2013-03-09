package scala.tools.eclipse.resources

import scala.tools.eclipse.util.EclipseUtils.workspaceRunnableIn

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IProgressMonitor

object MarkerFactory {
  trait Position {
    def isDefined: Boolean = false
    def offset: Int = throw new UnsupportedOperationException("Position.offset")
    def length: Int = throw new UnsupportedOperationException("Position.length")
    def line: Int = throw new UnsupportedOperationException("Position.line")
  }
  case object NoPosition extends Position
  case class RegionPosition(override val offset: Int, override val length: Int, override val line: Int) extends Position {
    override def isDefined: Boolean = true
  }
}

/** Generic factory for creating resource's markers.
  *
  * Markers are a general mechanism for associating notes and meta-data with resources.
  *
  * @param markerType A unique identifier for the created marker. Mind that a marker `X` can be a subtype of a marker `Y`.
  *                   See [[org.eclipse.core.resources.IMarker]] for more information.
  *
  * Example:
  *
  * {{{ class BuildProblemMarker extends MarkerFactory("org.scala-ide.sdt.core.problem") }}}
  */
abstract class MarkerFactory(markerType: String) {
  /** Create marker without a source position in the Problem view.
    * @param resource The resource to use to create the marker (hence, the marker will be associated to the passed resource)
    * @param severity Indicates the marker's error state. Its value can be one of:
    *                  [IMarker.SEVERITY_ERROR, IMarker.SEVERITY_WARNING, IMarker.SEVERITY_INFO]
    * @param msg      The text message displayed by the marker. Note, the passed message is truncated to 21000 chars.
    */
  def create(resource: IResource, severity: Int, msg: String): Unit =
    create(resource, severity, msg, MarkerFactory.NoPosition)

  /** Create marker with a source position in the Problem view.
    * @param resource The resource to use to create the marker (hence, the marker will be associated to the passed resource)
    * @param severity Indicates the marker's error state. Its value can be one of:
    *                 [IMarker.SEVERITY_ERROR, IMarker.SEVERITY_WARNING, IMarker.SEVERITY_INFO]
    * @param msg      The text message displayed by the marker. Note, the passed message is truncated to 21000 chars.
    * @param pos      The source position for the marker.
    */
  def create(resource: IResource, severity: Int, msg: String, pos: MarkerFactory.Position): Unit = {
    val marker = resource.createMarker(markerType)
    update(marker, severity, msg)
    setPos(marker, pos)
  }

  private def update(marker: IMarker, severity: Int, msg: String): IMarker = {
    marker.setAttribute(IMarker.SEVERITY, severity)
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

    marker.setAttribute(IMarker.MESSAGE, attrValue)
    marker
  }

  private def setPos(marker: IMarker, position: MarkerFactory.Position): IMarker = {
    if (position.isDefined) {
      marker.setAttribute(IMarker.CHAR_START, position.offset)
      marker.setAttribute(IMarker.CHAR_END, position.offset + math.max(position.length, 1))
      marker.setAttribute(IMarker.LINE_NUMBER, position.line)
    }
    marker
  }
}