package scala.tools.eclipse.debug.model

import scala.actors.Actor
import scala.actors.Future
import scala.tools.eclipse.debug.ActorExit
import scala.tools.eclipse.debug.ScalaDebugBreakpointManager
import scala.tools.eclipse.debug.ScalaSourceLookupParticipant
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.core.resources.IMarkerDelta
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.debug.core.model.IDebugTarget
import org.eclipse.debug.core.model.IProcess
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector

import com.sun.jdi.{ ReferenceType, Method, Location }
import com.sun.jdi.ThreadReference
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.{ThreadStartEvent, ThreadDeathEvent}
import com.sun.jdi.event.VMDeathEvent
import com.sun.jdi.event.VMDisconnectEvent
import com.sun.jdi.event.VMStartEvent
import com.sun.jdi.request.{ThreadStartRequest, ThreadDeathRequest}

object ScalaDebugTarget extends HasLogger {

  def apply(virtualMachine: VirtualMachine, launch: ILaunch, process: IProcess): ScalaDebugTarget = {

    val threadStartRequest = JdiRequestFactory.createThreadStartRequest(virtualMachine)

    val threadDeathRequest = JdiRequestFactory.createThreadDeathRequest(virtualMachine)

    val debugTarget = new ScalaDebugTarget(virtualMachine, launch, process) {
      override val eventActor = ScalaDebugTargetActor(threadStartRequest, threadDeathRequest, this)
      override val breakpointManager: ScalaDebugBreakpointManager = ScalaDebugBreakpointManager(this)
      override val eventDispatcher: ScalaJdiEventDispatcher = ScalaJdiEventDispatcher(virtualMachine, eventActor)
    }

    launch.addDebugTarget(debugTarget)

    launch.getSourceLocator match {
      case sourceLookupDirector: ISourceLookupDirector =>
        sourceLookupDirector.addParticipants(Array(ScalaSourceLookupParticipant))
      case sourceLocator =>
        logger.warn("Unable to recognize source locator %s, cannot add Scala participant".format(sourceLocator))
    }

    debugTarget.startJdiEventDispatcher()
    
    debugTarget.fireCreationEvent()

    debugTarget
  }

}

/**
 * A debug target in the Scala debug model.
 * This class is thread safe. Instances have be created through its companion object.
 */
abstract class ScalaDebugTarget private (val virtualMachine: VirtualMachine, launch: ILaunch, process: IProcess) extends ScalaDebugElement(null) with IDebugTarget {

  // Members declared in org.eclipse.debug.core.IBreakpointListener

  override def breakpointAdded(breakponit: IBreakpoint): Unit = ???
  override def breakpointChanged(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = ???
  override def breakpointRemoved(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = ???

  // Members declared in org.eclipse.debug.core.model.IDebugElement

  override def getLaunch: org.eclipse.debug.core.ILaunch = launch

  // Members declared in org.eclipse.debug.core.model.IDebugTarget

  override def getName: String = "Scala Debug Target" // TODO: need better name
  override def getProcess: org.eclipse.debug.core.model.IProcess = process
  override def getThreads: Array[org.eclipse.debug.core.model.IThread] = internalGetThreads.toArray
  override def hasThreads: Boolean = !internalGetThreads.isEmpty
  override def supportsBreakpoint(breakpoint: IBreakpoint): Boolean = ???

  // Members declared in org.eclipse.debug.core.model.IDisconnect

  override def canDisconnect: Boolean = false // TODO: need real logic
  override def disconnect(): Unit = ???
  override def isDisconnected: Boolean = false // TODO: need real logic

  // Members declared in org.eclipse.debug.core.model.IMemoryBlockRetrieval

  override def getMemoryBlock(startAddress: Long, length: Long): org.eclipse.debug.core.model.IMemoryBlock = ???
  override def supportsStorageRetrieval: Boolean = ???

  // Members declared in org.eclipse.debug.core.model.ISuspendResume

  override def canResume: Boolean = false // TODO: need real logic
  override def canSuspend: Boolean = false // TODO: need real logic
  override def isSuspended: Boolean = false // TODO: need real logic
  override def resume(): Unit = ???
  override def suspend(): Unit = ???

  // Members declared in org.eclipse.debug.core.model.ITerminate

  override def canTerminate: Boolean = running // TODO: need real logic
  override def isTerminated: Boolean = !running // TODO: need real logic
  override def terminate(): Unit = virtualMachine.dispose()
  
  // Members declared in scala.tools.eclipse.debug.model.ScalaDebugElement

  override def getDebugTarget: ScalaDebugTarget = this

  // ---

  @volatile
  private var running: Boolean = true

  protected[debug] val eventDispatcher: ScalaJdiEventDispatcher

  protected[debug] val breakpointManager: ScalaDebugBreakpointManager
  private[model] val eventActor: Actor

  /**
   * Initialize the dependent components
   */
  private def startJdiEventDispatcher() = {
    // start the event dispatcher thread
    DebugPlugin.getDefault.asyncExec(new Runnable() {
      def run() {
        val thread = new Thread(eventDispatcher, "Scala debugger JDI event dispatcher")
        thread.setDaemon(true)
        thread.start()
      }
    })
  }

  /**
   * Callback form the actor when the connection with the vm is enabled
   */
  private[model] def vmStarted() {
    breakpointManager.init()
    fireChangeEvent(DebugEvent.CONTENT)
  }

  /**
   * Callback from the actor when the connection with the vm as been lost
   */
  private[model] def vmTerminated() {
    running = false
    eventDispatcher.dispose()
    breakpointManager.dispose()
    fireTerminateEvent()
  }

  /**
   * Callback from the breakpoint manager when a platform breakpoint is hit
   */
  private[debug] def threadSuspended(thread: ThreadReference, eventDetail: Int) {
    eventActor !? ScalaDebugTargetActor.ThreadSuspended(thread, eventDetail)
  }

  /**
   * Return the current list of threads, using the actor system
   */
  private def internalGetThreads(): List[ScalaThread] = {
    if (running) {
      (eventActor !! ScalaDebugTargetActor.GetThreads).asInstanceOf[Future[List[ScalaThread]]]()
    } else {
      Nil
    }
  }
}

private[model] object ScalaDebugTargetActor {
  case class ThreadSuspended(thread: ThreadReference, eventDetail: Int)
  case object GetThreads
  
  def apply(threadStartRequest: ThreadStartRequest, threadDeathRequest: ThreadDeathRequest, debugTarget: ScalaDebugTarget): ScalaDebugTargetActor = {
    val actor= new ScalaDebugTargetActor(threadStartRequest, threadDeathRequest, debugTarget)
    actor.start()
    actor
  }
}

/**
 * Actor used to manage a Scala debug target. It keeps track of the existing threads.
 * This class is thread safe. Instances are not to be created outside of the ScalaDebugTarget object.
 */
private class ScalaDebugTargetActor private (threadStartRequest: ThreadStartRequest, threadDeathRequest: ThreadDeathRequest, debugTarget: ScalaDebugTarget) extends Actor {
  import ScalaDebugTargetActor._

  private var threads = List[ScalaThread]()

  /**
   *  process the debug events
   */
  def act() {
    loop {
      react {
        case _: VMStartEvent =>
          vmStarted()
          reply(false)
        case threadStartEvent: ThreadStartEvent =>
          if (!threads.exists(_.thread == threadStartEvent.thread))
            threads = threads :+ ScalaThread(debugTarget, threadStartEvent.thread)
          reply(false)
        case threadDeathEvent: ThreadDeathEvent =>
          val (removed, remainder) = threads.partition(_.thread eq threadDeathEvent.thread)
          threads = remainder
          removed.foreach(_.terminatedFromScala())
          reply(false)
        case _: VMDeathEvent =>
          vmTerminated()
          reply(false)
        case _: VMDisconnectEvent =>
          vmTerminated()
          reply(false)
        case ThreadSuspended(thread, eventDetail) =>
          // forward the event to the right thread
          threads.find(_.thread == thread).get.suspendedFromScala(eventDetail)
          reply(None)
        case GetThreads =>
          reply(threads)
        case ActorExit =>
          exit()
      }
    }
  }

  private def vmStarted() {
    val eventDispatcher = debugTarget.eventDispatcher
    // enable the thread management requests
    eventDispatcher.setActorFor(this, threadStartRequest)
    threadStartRequest.enable()
    eventDispatcher.setActorFor(this, threadDeathRequest)
    threadDeathRequest.enable()
    // get the current requests
    import scala.collection.JavaConverters._
    threads = debugTarget.virtualMachine.allThreads.asScala.toList.map(ScalaThread(debugTarget, _))
    debugTarget.vmStarted()
  }

  private def vmTerminated() {
    threads.foreach { _.dispose() }
    threads = Nil
    debugTarget.vmTerminated()
    this ! ActorExit
  }
}