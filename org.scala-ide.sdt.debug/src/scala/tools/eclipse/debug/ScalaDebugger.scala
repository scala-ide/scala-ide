package scala.tools.eclipse.debug

import scala.collection.mutable.Buffer

import org.eclipse.debug.core.model.IDebugModelProvider
import org.eclipse.debug.core.{IDebugEventSetListener, DebugPlugin, DebugEvent}
import org.eclipse.jdt.debug.core.{IJavaStackFrame, IJavaDebugTarget}
import org.eclipse.jdt.internal.debug.core.model.{JDIThread, JDIDebugTarget}

import model.ScalaDebugTarget

object ScalaDebugger extends IDebugEventSetListener {

  val classIDebugModelProvider = classOf[IDebugModelProvider]
  val classIJavaDebugTarget = classOf[IJavaDebugTarget]
  val classIJavaStackFrame = classOf[IJavaStackFrame]

  val modelProvider = new IDebugModelProvider {
    def getModelIdentifiers() = {
      Array(modelId)
    }
  }

  val modelId = "org.scala-ide.sdt.debug"

  // Members declared in org.eclipse.debug.core.IDebugEventSetListener

  def handleDebugEvents(events: Array[DebugEvent]) {
    events.foreach(event => {
      event.getKind match {
        case DebugEvent.CREATE =>
          event.getSource match {
            case target: JDIDebugTarget =>
              if (ScalaDebugPlugin.plugin.getPreferenceStore.getBoolean(DebugPreferencePage.P_ENABLE)) {
                javaDebugTargetCreated(target)
              }
            case _ =>
          }
        case DebugEvent.SUSPEND =>
          event.getSource match {
            case thread: JDIThread =>
              javaThreadSuspended(thread, event.getDetail)
            case _ =>
          }
        case DebugEvent.TERMINATE =>
          event.getSource match {
            case target: JDIDebugTarget =>
              javaDebugTargetTerminated(target)
            case _ =>
          }
        case _ =>
      }
    })
  }

  // ----

  val debugTargets = Buffer[ScalaDebugTarget]()

  def init() {
    DebugPlugin.getDefault.addDebugEventListener(this)
  }

  private def javaDebugTargetCreated(target: JDIDebugTarget) {
    val scalaTarget = ScalaDebugTarget(target)
    debugTargets += scalaTarget
    val launch = target.getLaunch
    launch.removeDebugTarget(target)
    launch.addDebugTarget(scalaTarget)

    // TODO: do that in a better place
    launch.setSourceLocator(new ScalaSourceLocator(launch))
  }

  private def javaDebugTargetTerminated(target: JDIDebugTarget) {
    debugTargets.find(target == _.javaTarget).foreach(_.terminatedFromJava())
  }

  private def javaThreadSuspended(thread: JDIThread, eventDetail: Int) {
    debugTargets.find(thread.getDebugTarget == _.javaTarget).foreach(_.javaThreadSuspended(thread, eventDetail))
  }

}

class ScalaDebugger