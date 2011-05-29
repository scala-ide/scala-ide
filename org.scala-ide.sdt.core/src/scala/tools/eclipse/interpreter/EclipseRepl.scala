package scala.tools.eclipse
package interpreter

import scala.tools.nsc.interpreter._
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.util.Position
import scala.collection.mutable

import java.io.PrintWriter
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.IWorkbenchPage

object EclipseRepl {
  private val projectToReplMap = new mutable.HashMap[ScalaProject, EclipseRepl]   
  
  def replForProject(project: ScalaProject): EclipseRepl = {
    projectToReplMap.getOrElseUpdate(project, {
      val settings = new Settings
      project.initialize(settings, _ => true)
      new EclipseRepl(project, settings)      
    })
  }
}

class EclipseRepl(project: ScalaProject, settings: Settings) extends IMain(settings) {
  object ReplReporter extends ConsoleReporter(settings, null, new PrintWriter(NullOutputStream)) {
    override def printMessage(msg: String) = {
      getReplView.displayOutput(msg)      
    }
  }
  
  private def getReplView: ReplConsoleView = {
    val viewPart = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getActivePage.showView(
        "org.scala-ide.sdt.core.consoleView", project.underlying.getName, 
        IWorkbenchPage.VIEW_VISIBLE)
    val replView = viewPart.asInstanceOf[ReplConsoleView]
    replView.projectName = project.underlying.getName
    replView
  }
  
  override def interpret(code: String): Results.Result = {
    getReplView.displayCode(code)
    super.interpret(code)
  }
  
  override lazy val reporter = ReplReporter
}

object NullOutputStream extends OutputStream {
  def write(b: Int) { }
}