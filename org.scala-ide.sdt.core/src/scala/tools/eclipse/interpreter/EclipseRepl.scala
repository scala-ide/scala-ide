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
  
  def replForProject(project: ScalaProject, replView: ReplConsoleView): EclipseRepl = {
    projectToReplMap.getOrElseUpdate(project, {
      val settings = new Settings
      project.initialize(settings, _ => true)
      new EclipseRepl(project, settings, replView)      
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

class EclipseRepl(project: ScalaProject, settings: Settings, replView: ReplConsoleView) {
  
  val replayList = new mutable.ListBuffer[String]
  
  val intp = new IMain(settings, new PrintWriter(ViewOutputStream)) 
    
  def interpret(code: String) {
    replayList += code
    replView displayCode code 
    interpretAndRedirect(code)
  }
  
  /**
   * Interpret `code`, while redirecting standard output to the repl view
   */
  private def interpretAndRedirect(code: String) {
    val result = Console.withOut(ViewOutputStream) { intp interpret code }
    ViewOutputStream.flush
  }
  
  def close = intp.close
    
  def replay {
    intp.reset
    replView displayCode replayList.mkString("\n") 
    replayList foreach { interpretAndRedirect(_) }
  }
  
  object ViewOutputStream extends java.io.ByteArrayOutputStream { self =>
    override def flush() {      
      replView displayOutput self.toString 
      self.reset
    }
  }
}
