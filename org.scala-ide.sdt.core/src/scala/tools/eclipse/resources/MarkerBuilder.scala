package scala.tools.eclipse.resources

import scala.tools.eclipse.util.EclipseUtils.workspaceRunnableIn

import collection.mutable.ListBuffer
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource

/**
 * Generic builder for creating resource's markers.
 *
 * Markers are a general mechanism for associating notes and meta-data with resources.
 *
 * [[MarkerBuilder]] is a "use and trash" builder for creating a marker in a resource. Once the
 * marker is created via [[MarkerBuilder.createIn(resource)]], the builder is no longer usable.
 * Attempting to use the builder to create the same marker twice will result in a [[java.lang.IllegalStateException]]
 * be thrown.
 *
 * Be aware that [[MarkerBuilder]] is not thread-safe. Though, creation of markers is performed within
 * a [[org.eclipse.core.resources.IWorkspaceRunnable]]. This is an important requirement as resources'
 * changes need to be executed in a thread-safe manner.
 *
 * @param markerType A unique identifier for the created marker. Mind that a marker `X` can be a subtype
 *                   of a marker `Y`. See [[org.eclipse.core.resources.IMarker]] for more information.
 *
 * For creating a new marker builder, either extends [[MarkerBuilder]] or use its companion object.
 *
 * Example: Create a new marker builder using [[MarkerBuilder]] companion object
 *
 * {{{
 *   object BuildProblemMarkerBuilder {
 *     def apply(): MarkerBuilder = MarkerBuilder("org.scala-ide.sdt.core.problem")
 *   }
 *
 *   // this will create an error marker in the `project` resource.
 *   BuildProblemMarkerBuilder().severity(IMarker.SEVERITY_ERROR).message("compilation errors").createIn(project)
 * }}}
 */
class MarkerBuilder protected (markerType: String) { self =>
  private type AttributeUpdate = IMarker => Unit
  private val attributeUpdates: ListBuffer[AttributeUpdate] = new ListBuffer[AttributeUpdate]

  /** When 'marker != null' it implies that 'this' builder has already been used.*/
  private var marker: IMarker = null

  /** Creates a marker in the passed */
  def createIn(resource: IResource): IMarker = {
    createMarkerInWorkspaceAndApply(resource) { marker =>
      if (marker != null)
        throw new IllegalStateException("This builder has already been used for creating a marker in " + resource.getName)

      try attributeUpdates.foreach(_(marker))
      finally self.marker = marker
    }
    marker
  }

  /** Seth the marker's severity to [IMarker.SEVERITY_ERROR].*/
  def error(): this.type = severity(IMarker.SEVERITY_ERROR)

  /** Seth the marker's severity to [IMarker.SEVERITY_WARNING].*/
  def warning(): this.type = severity(IMarker.SEVERITY_WARNING)

  /** Seth the marker's severity to [IMarker.SEVERITY_INFO].*/
  def info(): this.type = severity(IMarker.SEVERITY_INFO)

  /** Set the marker's severity state.*/
  def severity(severity: Int): this.type = {
    require(
      Set(IMarker.SEVERITY_ERROR, IMarker.SEVERITY_WARNING, IMarker.SEVERITY_INFO) contains severity,
      "Found severity %d, expected one of: [IMarker.SEVERITY_ERROR, IMarker.SEVERITY_WARNING, IMarker.SEVERITY_INFO]".format(severity))
    doOnCreation(_.setAttribute(IMarker.SEVERITY, severity))
    this
  }

  /** The text message displayed by the marker. Note that the passed message is truncated to 21000 chars.*/
  def message(msg: String): this.type = {
    doOnCreation(_.setAttribute(IMarker.MESSAGE, shorten(msg)))
    this
  }

  /** The source region where the marker is located.*/
  def region(offset: Int, length: Int): this.type = {
    if (offset > -1) doOnCreation { marker =>
      marker.setAttribute(IMarker.CHAR_START, offset)
      marker.setAttribute(IMarker.CHAR_END, offset + math.max(length, 1))
    }
    this
  }

  /** The source line where the marker is located.*/
  def line(number: Int): this.type = {
    if (number > -1) doOnCreation(_.setAttribute(IMarker.LINE_NUMBER, number))
    this
  }

  /** Shorten the passed 'msg' to 21000 chars if necessary.*/
  private def shorten(msg: String): String = {
    // Marker attribute values are limited to <= 65535 bytes and setAttribute will assert if they
    // exceed this. To guard against this we trim to <= 21000 characters ... see
    // org.eclipse.core.internal.resources.MarkerInfo.checkValidAttribute for justification
    // of this arbitrary looking number
    val maxMarkerLen = 21000
    val trimmedMsg = msg.take(maxMarkerLen)

    trimmedMsg.map {
      case '\n' | '\r' => ' '
      case c           => c
    }
  }

  private def doOnCreation(action: AttributeUpdate): Unit = attributeUpdates append action

  private def createMarkerInWorkspaceAndApply(resource: IResource)(f: IMarker => Unit): Unit = workspaceRunnableIn(resource.getWorkspace) { _ =>
    val marker = resource.createMarker(markerType)
    f(marker)
  }

  override def toString: String = "Marker builder for " + markerType
}

object MarkerBuilder {
  def apply(markerType: String): MarkerBuilder = new MarkerBuilder(markerType)
}