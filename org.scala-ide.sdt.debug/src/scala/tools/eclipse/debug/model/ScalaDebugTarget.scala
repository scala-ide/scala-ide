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
  def getThreads(): Array[org.eclipse.debug.core.model.IThread] = threads.toArray
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
        threads += new ScalaThread(this, threadStartEvent.thread)
      case threadDeathEvent: ThreadDeathEvent =>
        threads --= threads.find(_.thread == threadDeathEvent.thread) // TODO: use immutable list
      case _ =>
        ???
    }
    suspendVote
  }

  // ---

  var running: Boolean = true

  val threads = {
    import scala.collection.JavaConverters._
    javaTarget.getVM.allThreads.asScala.map(new ScalaThread(this, _))
  }

  fireCreationEvent

  def javaThreadSuspended(thread: JDIThread, eventDetail: Int) {
    threads.find(_.thread == thread.getUnderlyingThread).get.suspendedFromJava(eventDetail)
  }

  def terminatedFromJava() {
    threads.clear
    running = false
    fireTerminateEvent
  }

  def findAnonFunction(refType: ReferenceType): Option[Method] = {
    import scala.collection.JavaConverters._
    val methods = refType.methods.asScala.filter(method => method.name.startsWith("apply"))

    // TODO: using isBridge was not working with List[Int]. Should check if we can use it by default with some extra checks when it fails.
    //      methods.find(!_.isBridge)

    methods.size match {
      case 3 =>
        // method with primitive parameter
        methods.find(_.name.startsWith("apply$")).orElse({
          // method with primitive return type (with specialization in 2.10.0)
          methods.find(!_.signature.startsWith("(Ljava/lang/Object;)"))
        })
      case 2 =>
        methods.find(_.signature != "(Ljava/lang/Object;)Ljava/lang/Object;")
      case 1 =>
        methods.headOption
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