package org.scalaide.debug.internal

import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupParticipant
import org.scalaide.debug.internal.model.ScalaStackFrame

/**
 * SourceLookupParticipant providing a source names when using the
 * Scala debugger
 */
object ScalaSourceLookupParticipant extends AbstractSourceLookupParticipant {

  def getSourceName(obj: AnyRef): String = {
    obj match {
      case stackFrame: ScalaStackFrame =>
        stackFrame.getSourcePath
      case _ =>
        null
    }
  }

}