package org.scalaide.debug.internal

import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupParticipant
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.debug.internal.async.AsyncStackFrame

/**
 * SourceLookupParticipant providing a source names when using the
 * Scala debugger
 */
object ScalaSourceLookupParticipant extends AbstractSourceLookupParticipant {

  def getSourceName(obj: AnyRef): String = {
    obj match {
      case stackFrame: ScalaStackFrame =>
        stackFrame.getSourcePath
      case sf: AsyncStackFrame =>
        sf.getSourcePath
      case _ =>
        null
    }
  }

}
