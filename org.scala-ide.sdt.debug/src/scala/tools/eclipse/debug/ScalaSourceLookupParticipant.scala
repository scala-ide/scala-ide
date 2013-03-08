package scala.tools.eclipse.debug

import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupParticipant
import scala.tools.eclipse.debug.model.ScalaStackFrame

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