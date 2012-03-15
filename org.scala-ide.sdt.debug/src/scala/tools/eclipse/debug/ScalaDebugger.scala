package scala.tools.eclipse.debug

import scala.collection.mutable.Buffer
import org.eclipse.debug.core.model.IDebugModelProvider
import org.eclipse.debug.core.{ IDebugEventSetListener, DebugPlugin, DebugEvent }
import org.eclipse.jdt.debug.core.{ IJavaStackFrame, IJavaDebugTarget }
import org.eclipse.jdt.internal.debug.core.model.{ JDIThread, JDIDebugTarget }
import model.ScalaDebugTarget
import org.eclipse.ui.internal.WorkbenchPlugin
import org.eclipse.ui.ISelectionListener
import model.ScalaThread
import org.eclipse.jface.viewers.IStructuredSelection
import model.ScalaStackFrame
import scala.tools.eclipse.ScalaPlugin

object ScalaDebugger extends IDebugEventSetListener with ISelectionListener {

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

  // Members declared in org.eclipse.ui.ISelectionListener

  def selectionChanged(part: org.eclipse.ui.IWorkbenchPart, selection: org.eclipse.jface.viewers.ISelection) {
    currentThread= selection match {
      case structuredSelection: IStructuredSelection =>
        structuredSelection.getFirstElement match {
          case scalaThread: ScalaThread =>
            scalaThread
          case scalaStackFrame: ScalaStackFrame =>
            scalaStackFrame.thread
          case _ =>
            null
        }
      case _ =>
        null
    }
  }

  // ----

  val debugTargets = Buffer[ScalaDebugTarget]()

  var currentThread: ScalaThread = null

  def init() {
    DebugPlugin.getDefault.addDebugEventListener(this)
    if (!ScalaPlugin.plugin.headlessMode) {
      // TODO: really ugly. Need to keep track of current selection per window.
      WorkbenchPlugin.getDefault.getWorkbench.getWorkbenchWindows.apply(0).getSelectionService.addSelectionListener("org.eclipse.debug.ui.DebugView", this)
    }
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