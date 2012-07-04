package scala.tools.eclipse.debug.model

import scala.tools.eclipse.debug.ScalaDebugger.{modelProvider, modelId}
import scala.tools.eclipse.debug.ScalaDebugger
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.debug.core.model.{ITerminate, DebugElement}

class ScalaDebugElement(val debugTarget: ScalaDebugTarget) extends DebugElement(debugTarget) with ITerminate with HasLogger {

  // Members declared in org.eclipse.core.runtime.IAdaptable

  override def getAdapter(adapter: Class[_]): Object = {
    adapter match {
      case ScalaDebugger.classIDebugModelProvider =>
        modelProvider
      case _ =>
        super.getAdapter(adapter)
    }
  }

  // Members declared in org.eclipse.debug.core.model.IDebugElement

  def getModelIdentifier(): String = modelId

  // Members declared in org.eclipse.debug.core.model.ITerminate

  def canTerminate(): Boolean = debugTarget.canTerminate
  def isTerminated(): Boolean = debugTarget.isTerminated
  def terminate(): Unit = debugTarget.terminate

  // ----

}