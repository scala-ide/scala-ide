/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package util
import scala.util.control.ControlThrowable
import org.eclipse.core.runtime.jobs.Job


/**
 * Defensive is an helper 
 * * to "instrument" code
 * * to quick fix code (NPE)
 * * to gracefully failed (avoid exception to break the full dataflow)
 * WITHOUT forgot there is something to fix (later).
 * 
 * It's more code flow safe than exception, or assert
 * It's a complementary guardian to other tools like try { ... } catch {...}
 * 
 * Usage :
 * <br/> replace : <code>if (x != null) { ... }</code>
 * <br/> by : <code>if (Defensive.notNul(x , "x")) { ... }</code>
 *
 * A good practice can be to link (as comment) to a ticket's number (from issue tracker, or to a file in the project,
 * may be under the directory <project>/issues).
 */
object Defensive {
  private def log(format : String, args : Seq[Any]) {
    //TODO log in Eclipse Error Log
    System.err.println("ScalaPlugin--Defensive--" + Thread.currentThread().getName() + "--:" + format.format(args : _*))
    Thread.dumpStack()
  }
  def notNull(o : AnyRef, format : String, args : Any*) : Boolean = {
    val back = o != null
    if (!back) {
      log("isNull " + format, args)
    }
    back
  }
  
  def notEmpty(s : String, format : String, args : Any*) : Boolean = {
    val back = (s != null && s.trim().length() > 0)
    if (!back) {
      log("isEmpty " + format, args)
    }
    back
  }
  
  def notEmpty(a : Array[Char], format : String, args : Any*) : Boolean = {
    val back = (a != null && a.length > 0);
    if (!back) {
      log("isEmpty " + format, args)
    }
    back
  }
  
  def check(assertion : Boolean , format : String, args : Any*) : Boolean = {
    if (!assertion) {
      log("assertion failed " + format, args)
    }
    assertion
  }
  
  def tryOrLog(f : => Unit) = {
    try {
      f
    } catch {
      case ce : ControlThrowable => {
        ScalaPlugin.plugin.logInfo("log only for tracking", Some(ce))
        throw ce
      }
      case t => ScalaPlugin.plugin.logError(t)
    }
  }
  
  def tryOrLog[T](default : => T)(f : => T) = {
    try {
      f
    } catch {
      case ce : ControlThrowable => {
        ScalaPlugin.plugin.logInfo("log only for tracking", Some(ce))
        throw ce
      }
      case t => {
        ScalaPlugin.plugin.logError(t)
        default
      }
    }
  }
  
  def askRunOutOfMain(label : String, priority : Int = Job.INTERACTIVE)(f : => Unit) = {
    (Thread.currentThread.getName != "main") match {
      case true => Defensive.tryOrLog(f)
      case false => askRunInJob(label, priority)(f)
    }
  }

  def askRunInJob(label : String, priority : Int = Job.INTERACTIVE)(f : => Unit) = {
    import org.eclipse.core.runtime.IStatus
    import org.eclipse.core.runtime.IProgressMonitor
    import org.eclipse.core.runtime.Status
    
    val job = new Job(label) {
      def run(monitor: IProgressMonitor): IStatus = {
        Defensive.tryOrLog(f)
        Status.OK_STATUS
      }
    }

    job.setSystem(true)
    job.setPriority(priority)
    job.schedule()
  }
}
