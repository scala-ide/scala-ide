package scala.tools.eclipse.debug

import scala.collection.mutable.Buffer
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.debug.core.{IDebugEventSetListener, DebugPlugin, DebugEvent}
import org.eclipse.debug.core.model.{IDebugModelProvider, DebugElement}
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector
import org.eclipse.jdt.debug.core.{IJavaStackFrame, IJavaDebugTarget}
import org.eclipse.jdt.internal.debug.core.model.{JDIThread, JDIDebugTarget}
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.ui.{PlatformUI, ISelectionListener}

import model.{ScalaThread, ScalaStackFrame, ScalaDebugTarget}

object EclipseDebugEvent {
  def unapply(event: DebugEvent): Option[(Int, DebugElement)] = {
    event.getSource match {
      case debugElement: DebugElement =>
        Some(event.getKind, debugElement)
      case _ =>
        None
    }
  }
}

object ScalaDebugger extends IDebugEventSetListener with ISelectionListener with HasLogger {

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
    events.foreach(event =>
      event match {
        case EclipseDebugEvent(DebugEvent.CREATE, target: JDIDebugTarget) =>
          if (ScalaDebugPlugin.plugin.getPreferenceStore.getBoolean(DebugPreferencePage.P_ENABLE)) {
            javaDebugTargetCreated(target)
          }
        case EclipseDebugEvent(DebugEvent.SUSPEND, thread: JDIThread) =>
          javaThreadSuspended(thread, event.getDetail)
        case EclipseDebugEvent(DebugEvent.TERMINATE, target: JDIDebugTarget) =>
          javaDebugTargetTerminated(target)
        case _ =>
      })
  }

  // Members declared in org.eclipse.ui.ISelectionListener

  def selectionChanged(part: org.eclipse.ui.IWorkbenchPart, selection: org.eclipse.jface.viewers.ISelection) {
    currentThread = selection match {
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
      PlatformUI.getWorkbench.getWorkbenchWindows.apply(0).getSelectionService.addSelectionListener("org.eclipse.debug.ui.DebugView", this)
    }
  }

  private def javaDebugTargetCreated(target: JDIDebugTarget) {
    val scalaTarget = ScalaDebugTarget(target)
    debugTargets += scalaTarget
    val launch = target.getLaunch
    launch.removeDebugTarget(target)
    launch.addDebugTarget(scalaTarget)

    // add the Scala participant to provide source file name
    // when debugging application with the Scala debugger
    launch.getSourceLocator match {
      case sourceLookupDirector: ISourceLookupDirector =>
        sourceLookupDirector.addParticipants(Array(ScalaSourceLookupParticipant))
      case sourceLocator =>
        logger.warn("unable to recognize source locator %s, cannot add Scala participant".format(sourceLocator))
    }
  }

  private def javaDebugTargetTerminated(target: JDIDebugTarget) {
    debugTargets.find(target == _.javaTarget).foreach(_.terminatedFromJava())
  }

  private def javaThreadSuspended(thread: JDIThread, eventDetail: Int) {
    debugTargets.find(thread.getDebugTarget == _.javaTarget).foreach(_.javaThreadSuspended(thread, eventDetail))
  }

}
