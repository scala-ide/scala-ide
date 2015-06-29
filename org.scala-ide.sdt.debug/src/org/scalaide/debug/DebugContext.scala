package org.scalaide.debug

import org.eclipse.debug.core.model.IBreakpoint
import org.scalaide.debug.internal.model.ScalaDebugTarget

trait DebugContext

case class BreakpointContext(breakpoint: IBreakpoint, debugTarget: ScalaDebugTarget) extends DebugContext
