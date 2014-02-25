package org.scalaide.debug.internal.breakpoints

import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.core.resources.IMarker

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
}

object RichBreakpoint {
  implicit def richBreakpoint(bp: IBreakpoint): RichBreakpoint = new RichBreakpoint(bp)
}