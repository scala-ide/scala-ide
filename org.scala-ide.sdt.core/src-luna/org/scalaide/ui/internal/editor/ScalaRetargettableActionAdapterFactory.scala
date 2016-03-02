package org.scalaide.ui.internal.editor

import org.eclipse.core.runtime.IAdapterFactory
import org.eclipse.debug.ui.actions.IRunToLineTarget
import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget
import org.eclipse.jdt.internal.debug.ui.actions.RunToLineAdapter

class ScalaRetargettableActionAdapterFactory extends IAdapterFactory {
  override def getAdapter(adaptableObject : AnyRef, adapterType : Class[_]) =
    if (adapterType == classOf[IRunToLineTarget])
      new RunToLineAdapter
    else if (adapterType == classOf[IToggleBreakpointsTarget])
      new ScalaToggleBreakpointAdapter
    else
      null

  override def getAdapterList : Array[Class[_]] =
    Array(classOf[IRunToLineTarget], classOf[IToggleBreakpointsTarget])
}
