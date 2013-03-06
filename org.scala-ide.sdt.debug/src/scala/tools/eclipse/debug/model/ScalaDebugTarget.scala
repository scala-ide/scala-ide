package scala.tools.eclipse.debug.model

import scala.tools.eclipse.debug.BaseDebuggerActor
import scala.tools.eclipse.debug.PoisonPill
import scala.tools.eclipse.debug.ScalaSourceLookupParticipant
import scala.tools.eclipse.debug.breakpoints.ScalaDebugBreakpointManager
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.core.resources.IMarkerDelta
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.debug.core.model.IDebugTarget
import org.eclipse.debug.core.model.IProcess
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector
import org.osgi.framework.Version

import com.sun.jdi.ClassNotLoadedException
import com.sun.jdi.ThreadReference
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.ThreadDeathEvent
import com.sun.jdi.event.ThreadStartEvent
import com.sun.jdi.event.VMDeathEvent
import com.sun.jdi.event.VMDisconnectEvent
import com.sun.jdi.event.VMStartEvent
import com.sun.jdi.request.ThreadDeathRequest
import com.sun.jdi.request.ThreadStartRequest

object ScalaDebugTarget extends HasLogger {

  def apply(virtualMachine: VirtualMachine, launch: ILaunch, process: IProcess, allowDisconnect: Boolean, allowTerminate: Boolean): ScalaDebugTarget = {

    val threadStartRequest = JdiRequestFactory.createThreadStartRequest(virtualMachine)

    val threadDeathRequest = JdiRequestFactory.createThreadDeathRequest(virtualMachine)

    val debugTarget = new ScalaDebugTarget(virtualMachine, launch, process, allowDisconnect, allowTerminate) {
      override val companionActor = ScalaDebugTargetActor(threadStartRequest, threadDeathRequest, this)
      override val breakpointManager: ScalaDebugBreakpointManager = ScalaDebugBreakpointManager(this)
      override val eventDispatcher: ScalaJdiEventDispatcher = ScalaJdiEventDispatcher(virtualMachine, companionActor)
      override val cache: ScalaDebugCache = ScalaDebugCache(this, companionActor)
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

  val versionStringPattern = "version ([^-]*).*".r

}

/**
 * A debug target in the Scala debug model.
 * This class is thread safe. Instances have be created through its companion object.
 */
abstract class ScalaDebugTarget private (val virtualMachine: VirtualMachine, launch: ILaunch, process: IProcess, allowDisconnect: Boolean, allowTerminate: Boolean) extends ScalaDebugElement(null) with IDebugTarget with HasLogger {

  // Members declared in org.eclipse.debug.core.IBreakpointListener

  override def breakpointAdded(breakponit: IBreakpoint): Unit = ???
  override def breakpointChanged(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = ???
  override def breakpointRemoved(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = ???

  // Members declared in org.eclipse.debug.core.model.IDebugElement

  override def getLaunch: org.eclipse.debug.core.ILaunch = launch

  // Members declared in org.eclipse.debug.core.model.IDebugTarget

  override def getName: String = "Scala Debug Target" // TODO: need better name
  override def getProcess: org.eclipse.debug.core.model.IProcess = process
  override def getThreads: Array[org.eclipse.debug.core.model.IThread] = threads.toArray
  override def hasThreads: Boolean = !threads.isEmpty
  override def supportsBreakpoint(breakpoint: IBreakpoint): Boolean = ???

  // Members declared in org.eclipse.debug.core.model.IDisconnect

  override def canDisconnect(): Boolean = allowDisconnect && running
  override def disconnect(): Unit = {
    virtualMachine.dispose()
  }
  override def isDisconnected(): Boolean = !running

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

  override def canTerminate: Boolean = allowTerminate && running
  override def isTerminated: Boolean = !running
  override def terminate(): Unit = {
    virtualMachine.exit(1)
    // manually clean up, as VMDeathEvent and VMDisconnectedEvent are not fired 
    // when abruptly terminating the vM
    vmDisconnected()
    companionActor ! PoisonPill
  }

  // Members declared in scala.tools.eclipse.debug.model.ScalaDebugElement

  override def getDebugTarget: ScalaDebugTarget = this

  // ---

  @volatile
  private var running: Boolean = true
  @volatile
  private var threads = List[ScalaThread]()

  private[debug] val eventDispatcher: ScalaJdiEventDispatcher
  private[debug] val breakpointManager: ScalaDebugBreakpointManager
  private[debug] val companionActor: BaseDebuggerActor
  private[debug] val cache: ScalaDebugCache

  /**
   * Initialize the dependent components
   */
  private def startJdiEventDispatcher() = {
    // start the event dispatcher thread
    DebugPlugin.getDefault.asyncExec(new Runnable() {
      override def run() {
        val thread = new Thread(eventDispatcher, "Scala debugger JDI event dispatcher")
        thread.setDaemon(true)
        thread.start()
      }
    })
  }

  /**
   * Callback from the breakpoint manager when a platform breakpoint is hit
   */
  private[debug] def threadSuspended(thread: ThreadReference, eventDetail: Int) {
    companionActor ! ScalaDebugTargetActor.ThreadSuspended(thread, eventDetail)
  }

  /*
   * Information about the VM being debugged
   */
  
  /** The version of Scala on the VM being debugged.
   * The value is `None` if it has not been initialized yet, or if it is too early to know (scala.Predef is not loaded yet).
   * The value is `Some(Version(0,0,0))` if there was a problem while try fetch the version.
   * Otherwise it contains the value parsed from scala.util.Properties.versionString.
   */
  @volatile
  private var scalaVersion: Option[Version] = None

  private def getScalaVersion(thread: ScalaThread): Option[Version] = {
    if (scalaVersion.isEmpty) {
      scalaVersion = fetchScalaVersion(thread)
    }
    scalaVersion
  }

  /** Attempt to get a value for the Scala version.
   * The possible return values are defined in {{scalaVersion}}.
   */
  private def fetchScalaVersion(thread: ScalaThread): Option[Version] = {
    try {
      val propertiesObject = objectByName("scala.util.Properties", true, thread)
      propertiesObject.fieldValue("versionString") match {
        case s: ScalaStringReference =>
          s.underlying.value() match {
            case ScalaDebugTarget.versionStringPattern(version) =>
              Some(new Version(version))
            case _ =>
              logger.warn("Unable to parse Properties.versionString")
              Some(new Version(0, 0, 0))

          }
        case v =>
          logger.warn("Properties.versionString returned an unexpected value: '%s'".format(v))
          Some(new Version(0, 0, 0))
      }

    } catch {
      case e: ClassNotLoadedException if e.getMessage().contains("scala.Predef") =>
        logger.warn("Too early to get Scala version number from the VM: %s".format(e))
        None
      case e: IllegalArgumentException =>
        logger.warn("Failed to parse Scala version number from the VM", e)
        Some(new Version(0, 0, 0))
      case e: Exception =>
        logger.warn("Failed to get Scala version number from the VM: %s".format(e))
        Some(new Version(0, 0, 0))
    }
  }

  /** Return true if the version is >= 2.9.0 and < 3.0.0
   */
  def is2_9Compatible(thread: ScalaThread): Boolean = {
    getScalaVersion(thread).exists(v => v.getMajor == 2 && v.getMinor >= 9)
  }

  /** Return true if the version is >= 2.10.0 and < 3.0.0
   */
  def is2_10Compatible(thread: ScalaThread): Boolean = {
    getScalaVersion(thread).exists(v => v.getMajor == 2 && v.getMinor >= 10)
  }
  
  /*
   * JDI wrapper calls
   */
  /** Return a reference to the object with the given name in the debugged VM.
   * 
   * @param objectName the name of the object, as defined in code (without '$').
   * @param tryForceLoad indicate if it should try to forceLoad the type if it is not loaded yet.
   * @param thread the thread to use to if a force load is needed. Can be `null` if tryForceLoad is `false`.
   * 
   * @throws ClassNotLoadedException if the class was not loaded yet.
   * @throws IllegalArgumentException if there is no object of the given name.
   */
  def objectByName(objectName: String, tryForceLoad: Boolean, thread: ScalaThread): ScalaObjectReference = {
    val moduleClassName = objectName + '$'
    try classByName(moduleClassName, tryForceLoad: Boolean, thread: ScalaThread).fieldValue("MODULE$").asInstanceOf[ScalaObjectReference]
    catch {
      case e: RuntimeException => targetRequestFailed("Exception while retrieving module debug element `" + moduleClassName + "`", e)
    }
  }

  /** Return a reference to the type with the given name in the debugged VM.
   * 
   * @param objectName the name of the object, as defined in code (without '$').
   * @param tryForceLoad indicate if it should try to forceLoad the type if it is not loaded yet.
   * @param thread the thread to use to if a force load is needed. Can be `null` if tryForceLoad is `false`.
   * 
   * @throws ClassNotLoadedException if the class was not loaded yet.
   */
  private def classByName(typeName: String, tryForceLoad: Boolean, thread: ScalaThread): ScalaReferenceType = {
    import scala.collection.JavaConverters._
    // TODO: need toList?
    virtualMachine.classesByName(typeName).asScala.toList match {
      case t :: _ =>
        ScalaType(t, this)
      case Nil =>
        if (tryForceLoad) {
          forceLoad(typeName, thread)
        } else {
          throw new ClassNotLoadedException(typeName, "No force load requested")
        }
    }
  }
  
  /** Attempt to force load a type, by finding the classloader of `scala.Predef` and calling `loadClass` on it.
   */
  private def forceLoad(typeName: String, thread: ScalaThread): ScalaReferenceType = {
    val predef = objectByName("scala.Predef", false, null)
    val classLoader = getClassLoader(predef, thread)
    classLoader.invokeMethod("loadClass", thread, ScalaValue(typeName, this))
    val entities = virtualMachine.classesByName(typeName)
      if (entities.isEmpty()) {
        throw new ClassNotLoadedException(typeName, "Unable to force load")
      } else {
        ScalaType(entities.get(0), this)
      }
  }
  
  /** Return the classloader of the given object.
   */
  private def getClassLoader(instance: ScalaObjectReference, thread: ScalaThread): ScalaObjectReference = {
    val typeClassLoader= instance.underlying.referenceType().classLoader()
    if (typeClassLoader == null) {
      // JDI returns null for classLoader() if the classloader is the boot classloader.
      // Fetch the boot classloader by using ClassLoader.getSystemClassLoader()
      val classLoaderClass= classByName("java.lang.ClassLoader", false, null).asInstanceOf[ScalaClassType]
      classLoaderClass.invokeMethod("getSystemClassLoader", thread).asInstanceOf[ScalaObjectReference]
      } else {
        new ScalaObjectReference(typeClassLoader, this)
      }
  }

  /*
   * Methods used by the companion actor to update this object internal states
   * FOR THE COMPANION ACTOR ONLY.
   */

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
  private[model] def vmDisconnected() {
    running = false
    eventDispatcher.dispose()
    breakpointManager.dispose()
    cache.dispose()
    disposeThreads()
    fireTerminateEvent()
  }

  private def disposeThreads() {
     threads.foreach { _.dispose() }
     threads = Nil
  }

  /**
   * Add a thread to the list of threads.
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def addThread(thread: ThreadReference) {
    if (!threads.exists(_.threadRef eq thread))
      threads = threads :+ ScalaThread(this, thread)
  }

  /**
   * Remove a thread from the list of threads
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def removeThread(thread: ThreadReference) {
    val (removed, remainder) = threads.partition(_.threadRef eq thread)
    threads = remainder
    removed.foreach(_.terminatedFromScala())
  }

  /**
   * Set the initial list of threads.
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def initializeThreads(t: List[ThreadReference]) {
    threads = t.map(ScalaThread(this, _))
  }
  
  /**
   * Return the current list of threads
   */
  private[model] def getScalaThreads: List[ScalaThread] = threads
  
}

private[model] object ScalaDebugTargetActor {
  case class ThreadSuspended(thread: ThreadReference, eventDetail: Int)

  def apply(threadStartRequest: ThreadStartRequest, threadDeathRequest: ThreadDeathRequest, debugTarget: ScalaDebugTarget): ScalaDebugTargetActor = {
    val actor = new ScalaDebugTargetActor(threadStartRequest, threadDeathRequest, debugTarget)
    actor.start()
    actor
  }
}

/**
 * Actor used to manage a Scala debug target. It keeps track of the existing threads.
 * This class is thread safe. Instances are not to be created outside of the ScalaDebugTarget object.
 *
 * The `ScalaDebugTargetActor` is linked to both the `ScalaJdiEventDispatcherActor and the
 * `ScalaDebugBreakpointManagerActor`, this implies that if any of the three actors terminates (independently
 * of the reason), all other actors will also be terminated (an `Exit` message will be sent to each of the
 * linked actors).
 */
private class ScalaDebugTargetActor private (threadStartRequest: ThreadStartRequest, threadDeathRequest: ThreadDeathRequest, debugTarget: ScalaDebugTarget) extends BaseDebuggerActor {
  import ScalaDebugTargetActor._

  override protected def behavior = {
    case _: VMStartEvent =>
      vmStarted()
      reply(false)
    case threadStartEvent: ThreadStartEvent =>
      debugTarget.addThread(threadStartEvent.thread)
      reply(false)
    case threadDeathEvent: ThreadDeathEvent =>
      debugTarget.removeThread(threadDeathEvent.thread)
      reply(false)
    case _: VMDeathEvent | _: VMDisconnectEvent =>
      vmDisconnected()
      reply(false)
    case ThreadSuspended(thread, eventDetail) =>
      // forward the event to the right thread
      debugTarget.getScalaThreads.find(_.threadRef eq thread).get.suspendedFromScala(eventDetail)
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
    debugTarget.initializeThreads(debugTarget.virtualMachine.allThreads.asScala.toList)
    debugTarget.vmStarted()
  }

  override protected def preExit(): Unit = {
    debugTarget.vmDisconnected()
  }

  private def vmDisconnected(): Unit = poison()
}
