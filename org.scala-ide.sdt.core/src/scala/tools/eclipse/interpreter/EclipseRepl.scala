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
  
  /** Stop the execution of the repl associated with the project. The repl is expected to be in `projectToReplMap` */
  def stopRepl(project: ScalaProject) {
    val repl = projectToReplMap.remove(project).get
    repl.close
  }
  
  def relaunchRepl(project: ScalaProject) {
    val repl = projectToReplMap.get(project).get
    repl.replay
  }
}

class EclipseRepl(project: ScalaProject, settings: Settings) extends IMain(settings) {
  
  val replayList = new mutable.ListBuffer[String]
  
  private def getReplView: ReplConsoleView = {
    val viewPart = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getActivePage.showView(
        "org.scala-ide.sdt.core.consoleView", project.underlying.getName, 
        IWorkbenchPage.VIEW_VISIBLE)
    val replView = viewPart.asInstanceOf[ReplConsoleView]
    replView setScalaProject project
    replView
  }
  
  override def interpret(code: String): Results.Result = {
    replayList += code
    getReplView displayCode code
    super.interpret(code)
  }
    
  def replay {
    reset
    getReplView.displayCode(replayList.mkString("\n"))
    replayList foreach { super.interpret(_) }
  }
  
  override lazy val reporter = new ConsoleReporter(settings, null, new PrintWriter(ViewOutputStream)) 

  object ViewOutputStream extends java.io.ByteArrayOutputStream { self =>
    override def flush() {      
      getReplView.displayOutput(self.toString)
      self.reset
    }
  }
}
