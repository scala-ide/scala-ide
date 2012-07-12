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
import com.sun.jdi.VirtualMachine
import org.eclipse.debug.core.model.IProcess
import org.eclipse.debug.core.DebugPlugin
import com.sun.jdi.request.VMDeathRequest
import org.eclipse.debug.core.ILaunch
import com.sun.jdi.event.VMDeathEvent
import com.sun.jdi.event.VMDisconnectEvent
import scala.tools.eclipse.debug.ScalaDebugBreakpointManager
import com.sun.jdi.ThreadReference
import com.sun.jdi.event.VMStartEvent
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector
import scala.tools.eclipse.debug.ScalaSourceLookupParticipant
import scala.tools.eclipse.logging.HasLogger

object ScalaDebugTarget extends HasLogger {

  def apply(virtualMachine: VirtualMachine, launch: ILaunch, process: IProcess): ScalaDebugTarget = {

    val threadStartRequest = virtualMachine.eventRequestManager.createThreadStartRequest
    threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE)

    val threadDeathRequest = virtualMachine.eventRequestManager.createThreadDeathRequest
    threadDeathRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE)

    val target = new ScalaDebugTarget(virtualMachine, launch, process, threadStartRequest, threadDeathRequest)

    launch.addDebugTarget(target)

    launch.getSourceLocator match {
      case sourceLookupDirector: ISourceLookupDirector =>
        sourceLookupDirector.addParticipants(Array(ScalaSourceLookupParticipant))
      case sourceLocator =>
        logger.warn("unable to recognize source locator %s, cannot add Scala participant".format(sourceLocator))
    }

    target.initialize

    target
  }

}

class ScalaDebugTarget(val virtualMachine: VirtualMachine, launch: ILaunch, process: IProcess, threadStartRequest: ThreadStartRequest, threadDeathRequest: ThreadDeathRequest) extends ScalaDebugElement(null) with IDebugTarget {

  // Members declared in org.eclipse.debug.core.IBreakpointListener

  def breakpointAdded(x$1: org.eclipse.debug.core.model.IBreakpoint): Unit = ???
  def breakpointChanged(x$1: org.eclipse.debug.core.model.IBreakpoint, x$2: org.eclipse.core.resources.IMarkerDelta): Unit = ???
  def breakpointRemoved(x$1: org.eclipse.debug.core.model.IBreakpoint, x$2: org.eclipse.core.resources.IMarkerDelta): Unit = ???

  // Members declared in org.eclipse.debug.core.model.IDebugElement

  override def getDebugTarget(): org.eclipse.debug.core.model.IDebugTarget = this
  override def getLaunch(): org.eclipse.debug.core.ILaunch = launch

  // Members declared in org.eclipse.debug.core.model.IDebugTarget

  def getName(): String = "Scala Debug Target" // TODO: need better name
  def getProcess(): org.eclipse.debug.core.model.IProcess = process
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
  override def terminate(): Unit = {
    virtualMachine.dispose
    terminated
  }

  // event handling actor

  case class ThreadSuspended(thread: ThreadReference, eventDetail: Int)

  val eventActor= new Actor {

    start

    def act() {
      loop {
        react {
          case _: VMStartEvent =>
            started
            reply(false)
          case threadStartEvent: ThreadStartEvent =>
            if (!threads.exists(_.thread == threadStartEvent.thread))
              threads = threads :+ new ScalaThread(ScalaDebugTarget.this, threadStartEvent.thread)
            reply(false)
            fireChangeEvent(DebugEvent.CONTENT)
          case threadDeathEvent: ThreadDeathEvent =>
            val (removed, remainder) = threads.partition(_.thread == threadDeathEvent.thread)
            threads = remainder
            removed.foreach(_.terminatedFromScala)
            reply(false)
            fireChangeEvent(DebugEvent.CONTENT)
          case _: VMDeathEvent =>
            terminated
            reply(false)
            exit
          case _: VMDisconnectEvent =>
            terminated
            reply(false)
            exit
          case ThreadSuspended(thread, eventDetail) =>
            // forward the event to the right thread
            threads.find(_.thread == thread).get.suspendedFromScala(eventDetail)
            reply(None)
        }
      }
    }
  }

  // ---

  var running: Boolean = true

  val eventDispatcher= new ScalaJdiEventDispatcher(virtualMachine, eventActor)

  val breakpointManager = new ScalaDebugBreakpointManager(this)

  var threads = List[ScalaThread]()

  fireCreationEvent

  def initialize = {
    // start the event dispatcher thread
    DebugPlugin.getDefault.asyncExec(new Runnable() {
      def run() {
        val thread = new Thread(eventDispatcher, "Scala debugger JDI event dispatcher")
        thread.setDaemon(true)
        thread.start
      }
    })
  }

  def started() {
    import scala.collection.JavaConverters._
    // enable the thread management requests
    eventDispatcher.setActorFor(eventActor, threadStartRequest)
    threadStartRequest.enable
    eventDispatcher.setActorFor(eventActor, threadDeathRequest)
    threadDeathRequest.enable
    // get the current requests
    threads = virtualMachine.allThreads.asScala.toList.map(new ScalaThread(this, _))

    breakpointManager.init()

    fireChangeEvent(DebugEvent.CONTENT)
  }

  def terminated() {
    running = false
    eventDispatcher.dispose()
    breakpointManager.dispose()
    threads.foreach{_.dispose}
    threads = Nil
    fireTerminateEvent
  }

  def threadSuspended(thread: ThreadReference, eventDetail: Int) {
    eventActor !? ThreadSuspended(thread, eventDetail)
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
        val applyMethod = methods.find(_.name == "apply")
        val applySpMethod = methods.find(_.name.startsWith("apply$"))
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