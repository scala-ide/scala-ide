package org.scalaide.debug.internal

import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupParticipant
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.util.Utils.jdiSynchronized

/**
 * SourceLookupParticipant providing a source names when using the
 * Scala debugger
 */
object ScalaSourceLookupParticipant extends AbstractSourceLookupParticipant {

  def getSourceName(obj: AnyRef): String = {
    obj match {
      case stackFrame: ScalaStackFrame =>
         jdiSynchronized { stackFrame.getSourcePath }
      case _ =>
        null
    }
  }

}
