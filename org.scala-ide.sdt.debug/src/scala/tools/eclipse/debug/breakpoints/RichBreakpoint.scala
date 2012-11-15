package scala.tools.eclipse.debug.breakpoints

import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.core.resources.IMarker
import scala.tools.eclipse.debug.BreakpointSupport

/** A decorater class for platform breakpoints, as used from Scala. */
class RichBreakpoint(bp: IBreakpoint) {
  /** Return the typename of this breakpoint, or the empty string if unknown. */
  def typeName: String = {
    bp.getMarker.getAttribute(BreakpointSupport.ATTR_TYPE_NAME, "")
  }

  /** Return the line number of this breakpoint, or -1 if unknown  */
  def lineNumber: Int = {
    bp.getMarker.getAttribute(IMarker.LINE_NUMBER, -1)
  }

  /** Return true if the VM request attribute of this breakpoint is enabled */
  def vmRequestEnabled: Boolean = 
    bp.getMarker().getAttribute(BreakpointSupport.ATTR_VM_REQUESTS_ENABLED, false)

  /** Set the value of the VM request attribute.
   * 
   *  This method only sets the marker attribute. It does not have any effect on the
   *  vm requests used by this breakpoint.
   */
  def setVmRequestEnabled(value: Boolean) =
    bp.getMarker().setAttribute(BreakpointSupport.ATTR_VM_REQUESTS_ENABLED, value)
}

object RichBreakpoint {
  implicit def richBreakpoint(bp: IBreakpoint): RichBreakpoint = new RichBreakpoint(bp)
}