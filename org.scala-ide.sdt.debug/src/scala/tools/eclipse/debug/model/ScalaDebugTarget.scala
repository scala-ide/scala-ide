package scala.tools.eclipse.debug.model

import scala.Option.option2Iterable
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.tools.eclipse.debug.ScalaDebugger
import org.eclipse.debug.core.model.IDebugTarget
import org.eclipse.jdt.internal.debug.core.model.{ JDIThread, JDIDebugTarget }
import org.eclipse.jdt.internal.debug.core.IJDIEventListener
import com.sun.jdi.event.{ ThreadStartEvent, ThreadDeathEvent, EventSet, Event }
import com.sun.jdi.request.{ ThreadStartRequest, ThreadDeathRequest, EventRequest }
import com.sun.jdi.{ ReferenceType, Method, Location }
import scala.actors.Actor

object ScalaDebugTarget {

  def apply(javaTarget: JDIDebugTarget): ScalaDebugTarget = {

    val virtualMachine = javaTarget.getVM

    val threadStartRequest = virtualMachine.eventRequestManager.createThreadStartRequest
    threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE)

    val threadDeathRequest = virtualMachine.eventRequestManager.createThreadDeathRequest
    threadDeathRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE)

    val target = new ScalaDebugTarget(javaTarget, threadStartRequest, threadDeathRequest)

    // enable the requests
    javaTarget.addJDIEventListener(target, threadStartRequest)
    threadStartRequest.enable
    javaTarget.addJDIEventListener(target, threadDeathRequest)
    threadDeathRequest.enable

    target
  }

}

class ScalaDebugTarget(val javaTarget: JDIDebugTarget, threadStartRequest: ThreadStartRequest, threadDeathRequest: ThreadDeathRequest) extends ScalaDebugElement(null) with IDebugTarget with IJDIEventListener {

  // Members declared in org.eclipse.core.runtime.IAdaptable

  override def getAdapter(adapter: Class[_]): Object = {
    adapter match {
      case ScalaDebugger.classIJavaDebugTarget =>
        null
      case ScalaDebugger.classIJavaStackFrame =>
        null
      case _ =>
        super.getAdapter(adapter)
    }
  }

  // Members declared in org.eclipse.debug.core.IBreakpointListener

  def breakpointAdded(x$1: org.eclipse.debug.core.model.IBreakpoint): Unit = ???
  def breakpointChanged(x$1: org.eclipse.debug.core.model.IBreakpoint, x$2: org.eclipse.core.resources.IMarkerDelta): Unit = ???
  def breakpointRemoved(x$1: org.eclipse.debug.core.model.IBreakpoint, x$2: org.eclipse.core.resources.IMarkerDelta): Unit = ???

  // Members declared in org.eclipse.debug.core.model.IDebugElement

  override def getDebugTarget(): org.eclipse.debug.core.model.IDebugTarget = this
  override def getLaunch(): org.eclipse.debug.core.ILaunch = javaTarget.getLaunch

  // Members declared in org.eclipse.debug.core.model.IDebugTarget

  def getName(): String = "Scala Debug Target" // TODO: need better name
  def getProcess(): org.eclipse.debug.core.model.IProcess = javaTarget.getProcess
  def getThreads(): Array[org.eclipse.debug.core.model.IThread] = threads.toArray // TODO: handle through the actor?
  def hasThreads(): Boolean = !threads.isEmpty
  def supportsBreakpoint(x$1: org.eclipse.debug.core.model.IBreakpoint): Boolean = ???

  // Members declared in org.eclipse.debug.core.model.IDisconnect

  def canDisconnect(): Boolean = false // TODO: need real logic
  def disconnect(): Unit = ???
  def isDisconnected(): Boolean = false // TODO: need real logic

  // Members declared in org.eclipse.debug.core.model.IMemoryBlockRetrieval

  def getMemoryBlock(x$1: Long, x$2: Long): org.eclipse.debug.core.model.IMemoryBlock = ???
  def supportsStorageRetrieval(): Boolean = ???

  // Members declared in org.eclipse.debug.core.model.ISuspendResume

  def canResume(): Boolean = false // TODO: need real logic
  def canSuspend(): Boolean = false // TODO: need real logic
  def isSuspended(): Boolean = false // TODO: need real logic
  def resume(): Unit = ???
  def suspend(): Unit = ???

  // Members declared in org.eclipse.debug.core.model.ITerminate

  override def canTerminate(): Boolean = running // TODO: need real logic
  override def isTerminated(): Boolean = !running // TODO: need real logic
  override def terminate(): Unit = javaTarget.terminate

  // Members declared in org.eclipse.jdt.internal.debug.core.IJDIEventListener

  def eventSetComplete(event: Event, target: JDIDebugTarget, suspend: Boolean, eventSet: EventSet): Unit = {
    // nothing to do
  }

  def handleEvent(event: Event, target: JDIDebugTarget, suspendVote: Boolean, eventSet: EventSet): Boolean = {
    event match {
      case threadStartEvent: ThreadStartEvent =>
        actor !? threadStartEvent
      case threadDeathEvent: ThreadDeathEvent =>
        actor !? threadDeathEvent
      case _ =>
        ???
    }
    suspendVote
  }
  
  // event handling actor
  
  case object TerminatedFromJava
  
  case class ThreadSuspendedFromJava(thread: JDIThread, eventDetail: Int)
  
  class EventActor extends Actor {

    start

    def act() {
      loop {
        react {
          case threadStartEvent: ThreadStartEvent =>
            if (!threads.exists(_.thread == threadStartEvent.thread))
              threads = threads :+ new ScalaThread(ScalaDebugTarget.this, threadStartEvent.thread)
            reply(this)
          case threadDeathEvent: ThreadDeathEvent =>
            val (removed, remainder) = threads.partition(_.thread == threadDeathEvent.thread)
            threads = remainder
            removed.foreach(_.terminatedFromScala)
            reply(this)
          case ThreadSuspendedFromJava(thread, eventDetail) =>
            threads.find(_.thread == thread.getUnderlyingThread).get.suspendedFromJava(eventDetail)
            reply(this)
          case TerminatedFromJava =>
            threads = Nil
            running = false
            fireTerminateEvent
            exit
        }
      }
    }
  }

  // ---

  var running: Boolean = true
  
  val actor = new EventActor

  var threads = {
    import scala.collection.JavaConverters._
    javaTarget.getVM.allThreads.asScala.toList.map(new ScalaThread(this, _))
  }

  fireCreationEvent

  def javaThreadSuspended(thread: JDIThread, eventDetail: Int) {
    actor !? ThreadSuspendedFromJava(thread, eventDetail)
  }

  def terminatedFromJava() {
    actor ! TerminatedFromJava
  }

  /**
   * Return the method containing the actual code of the anon func, if it is contained 
   * in the given range, <code>None</code> otherwise.
   */
  def anonFunctionsInRange(refType: ReferenceType, range: Range): Option[Method] = {
    findAnonFunction(refType).filter(method => range.contains(method.location.lineNumber))
  }
  
  /**
   * Return the method containing the actual code of the anon func.
   * Return <code>None</code> if no method can be identified has being it.
   */
  def findAnonFunction(refType: ReferenceType): Option[Method] = {
    // TODO: check super type at some point
    import scala.collection.JavaConverters._
    val methods = refType.methods.asScala.filter(method => !method.isBridge && method.name.startsWith("apply"))

    methods.size match {
      case 1 =>
        // one non bridge apply method, just use it
        methods.headOption
      case 2 =>
        // this is more complex.
        // the compiler may have 'forgotten' to flag the 'apply' as a bridge method,
        // or both the 'apply' and the 'apply$__$sp' contains the actual code
        
        // if the 'apply' and the 'apply$__$sp' contains the same code, we are in the optimization case, the 'apply' method
        // will be used, otherwise, the 'apply$__$sp" will be used.
        val applyMethod= methods.find(_.name == "apply")
        val applySpMethod= methods.find(_.name.startsWith("apply$"))
        if (applyMethod.isDefined) {
          if (applySpMethod.isDefined) {
            if (applyMethod.get.bytecodes.sameElements(applySpMethod.get.bytecodes)) {
              applyMethod
            } else {
              applySpMethod
            }
          } else {
            applyMethod
          }
        } else {
          applySpMethod
        }
      case _ =>
        None
    }
  }

  def isValidLocation(location: Location): Boolean = {
    val typeName = location.declaringType.name
    // TODO: use better pattern matching
    // TODO: check for bridge methods?
    if (typeName.startsWith("scala.collection") || typeName.startsWith("scala.runtime") || typeName.equals("java.lang.ClassLoader"))
      false
    else if (typeName.contains("$$anonfun$")) {
      findAnonFunction(location.declaringType).exists(_ == location.method)
    } else
      true
  }

  def shouldNotStepInto(location: Location): Boolean = {
    location.method.isConstructor || location.declaringType.name.equals("java.lang.ClassLoader")
  }

}