package scala.tools.eclipse.debug.evaluation

import scala.collection.JavaConverters.setAsJavaSetConverter
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.debug.model.ScalaStackFrame
import scala.tools.eclipse.debug.model.ScalaThread
import scala.tools.eclipse.launching.ScalaLaunchDelegate
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.IViewSite
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.part.ViewPart
import scala.tools.eclipse.interpreter.InterpreterConsoleView


// TODO: it would be nice if a console could get notifications whenever a breakpoint stops and when a thread dies, so that the user doesn't have to manually select the frame.
class EvaluationConsoleView extends ViewPart with InterpreterConsoleView with ISelectionListener with HasLogger {

  private var currentEvaluationEngine: Option[ScalaEvaluationEngine] = None

  override def init(partSite: IViewSite) = {
    super.init(partSite)

    if (!ScalaPlugin.plugin.headlessMode) {
      // TODO: really ugly. Need to keep track of current selection per window.
      PlatformUI.getWorkbench.getWorkbenchWindows.apply(0).getSelectionService.addSelectionListener("org.eclipse.debug.ui.DebugView", this)
    }
  }

  override def createPartControl(parent: Composite) {
    createInterpreterPartControl(parent)
    setPartName("Scala Evaluation Console")
  }

  override def selectionChanged(part: org.eclipse.ui.IWorkbenchPart, selection: org.eclipse.jface.viewers.ISelection) {
    def bindStackFrame(evalEngine: ScalaEvaluationEngine, stackFrame: ScalaStackFrame, scalaProject: ScalaProject): Unit = {
      val bindings = ScalaEvaluationEngine.yieldStackFrameBindings(Option(stackFrame), scalaProject)
      for (b <- bindings)
        evalEngine.bind(b.name, b.value, true)(b.tpe)
    }

    def getScalaLaunchDelegate(thread: ScalaThread): Option[ScalaLaunchDelegate] = {
      val launch = thread.getDebugTarget.getLaunch
      val launchDelegate = launch.getLaunchConfiguration().getPreferredDelegate(Set(launch.getLaunchMode()).asJava)
      launchDelegate.getDelegate() match {
        case sld: ScalaLaunchDelegate => Some(sld)
        case _ => None
      }
    }

    def makeEvalEngine(stackFrame: ScalaStackFrame): Option[ScalaEvaluationEngine] = {
      getScalaLaunchDelegate(stackFrame.thread) match {
        case Some(sld) =>
          val evalEngine = {
            currentEvaluationEngine match {
              case Some(e) if !e.isStale && e.thread.threadRef.uniqueID() == stackFrame.thread.threadRef.uniqueID() => e
              case _ => new ScalaEvaluationEngine(sld.classpath, stackFrame.thread.getDebugTarget, stackFrame.thread)
            }
          }

          evalEngine.resetRepl()
          bindStackFrame(evalEngine, stackFrame, sld.scalaProject)
          Some(evalEngine)
        case _ => None
      }
    }

    currentEvaluationEngine = selection match {
      case structuredSelection: IStructuredSelection =>
        structuredSelection.getFirstElement() match {
          case scalaThread: ScalaThread =>
            makeEvalEngine(scalaThread.getTopScalaStackFrame)
          case scalaStackFrame: ScalaStackFrame =>
            makeEvalEngine(scalaStackFrame)
          case _ => None
        }
      case _ => None
    }
  }

  override def evaluate(expression: String): Unit = {
    displayCode(expression)
    currentEvaluationEngine match {
      case Some(evalEngine) => evalEngine.execute(expression, true, Nil) match {
        case Some(result) =>
          displayOutput(result)
        case _ => displayError("Failed to evaluate expression.")
      }
      case _ => displayError("Unable to evaluate expression.")
    }
  }
}