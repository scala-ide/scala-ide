package scala.tools.eclipse.debug.model

import scala.tools.eclipse.debug.ScalaDebugger.modelProvider
import scala.tools.eclipse.debug.ScalaDebugger
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.debug.core.model.{IDebugTarget, ITerminate, DebugElement}
import scala.tools.eclipse.debug.ScalaDebugPlugin

/**
 * Base class for debug elements in the Scala debug model
 * This class is thread safe.
 */
abstract class ScalaDebugElement(debugTarget: ScalaDebugTarget) extends DebugElement(debugTarget) with ITerminate with HasLogger {

  // Members declared in org.eclipse.core.runtime.IAdaptable

  override def getAdapter(adapter: Class[_]): Object = {
    adapter match {
      case ScalaDebugger.classIDebugModelProvider =>
        modelProvider
      case _ =>
        super.getAdapter(adapter)
    }
  }
  
  override def getDebugTarget: ScalaDebugTarget = debugTarget

  // Members declared in org.eclipse.debug.core.model.IDebugElement

  override val getModelIdentifier: String = ScalaDebugPlugin.id

  // Members declared in org.eclipse.debug.core.model.ITerminate

  override def canTerminate: Boolean = debugTarget.canTerminate
  override def isTerminated: Boolean = debugTarget.isTerminated
  override def terminate(): Unit = debugTarget.terminate()

  // ----

}