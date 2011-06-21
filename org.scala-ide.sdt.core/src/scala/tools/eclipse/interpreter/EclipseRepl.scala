package scala.tools.eclipse
package interpreter

import scala.tools.nsc.interpreter._
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.util.Position
import scala.collection.mutable
import scala.tools.eclipse.util.SWTUtils

import scala.actors.Actor

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
  def stopRepl(project: ScalaProject, flush: Boolean = true) {
    val repl = projectToReplMap.remove(project).get
    if (flush) repl.close
  }

  def relaunchRepl(project: ScalaProject) {
    val repl = projectToReplMap.get(project).get
    repl.resetCompiler
    repl.replay
  }  
  
  def replayRepl(project: ScalaProject) {
    val repl = projectToReplMap.get(project).get
    repl.replay
  }
}   

class EclipseRepl(project: ScalaProject, settings: Settings, replView: ReplConsoleView) {
  
  import Actor._
  
  private val eventQueue = actor {
    loop { receive {
      case code: String => 
        Console.withOut(ViewOutputStream) { 
          intp interpret code 
        }
        ViewOutputStream.flush
    }}
  }
  
  private def addToQueue(code: String) {
    eventQueue ! code
  }
  
  val replayList = new mutable.ListBuffer[String]
  
  var intp = createCompiler()
  
  private def createCompiler(): IMain = new IMain(settings, new PrintWriter(ViewOutputStream))  
  private def resetCompiler = {
    intp.close
    intp = createCompiler() 
  } 
    
  def interpret(code: String) {
    replayList += code
    replView displayCode code 
    addToQueue(code)
  } 
  
  def close = intp.close
    
  def replay {
    intp.reset
    replView displayCode replayList.mkString("\n") 
    replayList foreach { addToQueue(_) }
  }
  
  object ViewOutputStream extends java.io.ByteArrayOutputStream { self =>
    override def flush() {      
      SWTUtils.asyncExec { 
        replView displayOutput self.toString 
        self.reset
      } 
    }
  }
}
