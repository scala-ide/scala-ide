package org.scalaide.debug.internal.model

import org.scalaide.core.IScalaPlugin
import org.scalaide.debug.internal.ScalaSourceLookupParticipant
import org.scalaide.debug.internal.breakpoints.ScalaDebugBreakpointManager
import org.scalaide.debug.internal.hcr.ClassFileResource
import org.scalaide.debug.internal.hcr.HotCodeReplaceExecutor
import org.scalaide.debug.internal.hcr.ScalaHotCodeReplaceManager
import org.scalaide.debug.internal.hcr.ui.HotCodeReplaceListener
import org.scalaide.debug.internal.preferences.HotCodeReplacePreferences
import org.scalaide.logging.HasLogger
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
import java.util.concurrent.atomic.AtomicBoolean
import com.sun.jdi.event.Event
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.scalaide.debug.internal.JdiDebugTargetEventReceiver
import java.util.concurrent.atomic.AtomicReference
import org.scalaide.debug.internal.JdiEventReceiver
import org.scalaide.debug.internal.JdiDebugTargetEventReceiver

object ScalaDebugTarget extends HasLogger {

  def apply(virtualMachine: VirtualMachine,
    launch: ILaunch,
    process: IProcess,
    allowDisconnect: Boolean,
    allowTerminate: Boolean,
    classPath: Option[Seq[String]] = None): ScalaDebugTarget = {

    val threadStartRequest = JdiRequestFactory.createThreadStartRequest(virtualMachine)

    val threadDeathRequest = JdiRequestFactory.createThreadDeathRequest(virtualMachine)

    val debugTarget = new ScalaDebugTarget(virtualMachine, launch, process, allowDisconnect, allowTerminate, classPath) {
      override val subordinate = ScalaDebugTargetSubordinate(threadStartRequest, threadDeathRequest, this)
      override val breakpointManager: ScalaDebugBreakpointManager = ScalaDebugBreakpointManager(this)
      override val hcrManager: Option[ScalaHotCodeReplaceManager] = ScalaHotCodeReplaceManager.create(subordinate)
      override val eventDispatcher: ScalaJdiEventDispatcher = ScalaJdiEventDispatcher(virtualMachine, this)
      override val cache: ScalaDebugCache = ScalaDebugCache(this)
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
abstract class ScalaDebugTarget private (val virtualMachine: VirtualMachine,
  launch: ILaunch, process: IProcess, allowDisconnect: Boolean,
  allowTerminate: Boolean, val classPath: Option[Seq[String]])
    extends ScalaDebugElement(null) with JdiDebugTargetEventReceiver with IDebugTarget with HasLogger {

  // Members declared in org.eclipse.debug.core.IBreakpointListener

  override def breakpointAdded(breakponit: IBreakpoint): Unit = ???

  override def breakpointChanged(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = ???

  override def breakpointRemoved(breakpoint: IBreakpoint, delta: IMarkerDelta): Unit = ???

  // Members declared in org.eclipse.debug.core.model.IDebugElement

  override def getLaunch: org.eclipse.debug.core.ILaunch = launch

  // Members declared in org.eclipse.debug.core.model.IDebugTarget

  // TODO: need better name
  override def getName: String = "Scala Debug Target"

  override def getProcess: org.eclipse.debug.core.model.IProcess = process

  override def getThreads: Array[org.eclipse.debug.core.model.IThread] = threads.get.toArray

  override def hasThreads: Boolean = !threads.get.isEmpty

  override def supportsBreakpoint(breakpoint: IBreakpoint): Boolean = ???

  // Members declared in org.eclipse.debug.core.model.IDisconnect

  override def canDisconnect(): Boolean = allowDisconnect && running.get

  override def disconnect(): Unit = {
    virtualMachine.dispose()
    vmDisconnected()
  }

  override def isDisconnected(): Boolean = !running.get

  // Members declared in org.eclipse.debug.core.model.IMemoryBlockRetrieval

  override def getMemoryBlock(startAddress: Long, length: Long): org.eclipse.debug.core.model.IMemoryBlock = ???

  override def supportsStorageRetrieval: Boolean = ???

  // Members declared in org.eclipse.debug.core.model.ISuspendResume

  // TODO: need real logic
  override def canResume: Boolean = false

  // TODO: need real logic
  override def canSuspend: Boolean = false

  // TODO: need real logic
  override def isSuspended: Boolean = false

  override def resume(): Unit = ???

  override def suspend(): Unit = ???

  // Members declared in org.eclipse.debug.core.model.ITerminate

  override def canTerminate: Boolean = allowTerminate && running.get

  override def isTerminated: Boolean = !running.get

  override def terminate(): Unit = {
    virtualMachine.exit(1)
    // manually clean up, as VMDeathEvent and VMDisconnectedEvent are not fired
    // when abruptly terminating the vM
    vmDisconnected()
  }

  override def getDebugTarget: ScalaDebugTarget = this

  override protected def innerHandle = subordinate.innerHandle

  override def dispose()(implicit ec: ExecutionContext): Future[Unit] = Future(terminate())
  // ---

  private val running: AtomicBoolean = new AtomicBoolean(true)
  private val threads: AtomicReference[List[ScalaThread]] = new AtomicReference(Nil)

  private[internal] val isPerformingHotCodeReplace: AtomicBoolean = new AtomicBoolean

  private[debug] val eventDispatcher: ScalaJdiEventDispatcher
  private[debug] val breakpointManager: ScalaDebugBreakpointManager
  private[debug] val hcrManager: Option[ScalaHotCodeReplaceManager]
  private[debug] val subordinate: ScalaDebugTargetSubordinate
  private[debug] val cache: ScalaDebugCache

  /**
   * Initialize the dependent components
   */
  private def startJdiEventDispatcher() = {
    eventDispatcher.run()
  }

  /**
   * Callback from the breakpoint manager when a platform breakpoint is hit
   */
  private[debug] def threadSuspended(thread: ThreadReference, eventDetail: Int): Unit = {
    subordinate.threadSuspended(thread, eventDetail)
  }

  /*
   * Information about the VM being debugged
   */

  /**
   * The version of Scala on the VM being debugged.
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

  /**
   * Attempt to get a value for the Scala version.
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

  /**
   * Return true if the version is >= 2.9.0 and < 3.0.0
   */
  def is2_9Compatible(thread: ScalaThread): Boolean = {
    getScalaVersion(thread).exists(v => v.getMajor == 2 && v.getMinor >= 9)
  }

  /**
   * Return true if the version is >= 2.10.0 and < 3.0.0
   */
  def is2_10Compatible(thread: ScalaThread): Boolean = {
    getScalaVersion(thread).exists(v => v.getMajor == 2 && v.getMinor >= 10)
  }

  /*
   * JDI wrapper calls
   */
  /**
   * Return a reference to the object with the given name in the debugged VM.
   *
   * @param objectName the name of the object, as defined in code (without '$').
   * @param tryForceLoad indicate if it should try to forceLoad the type if it is not loaded yet.
   * @param thread the thread to use to if a force load is needed. Can be `null` if tryForceLoad is `false`.
   *
   * @throws ClassNotLoadedException if the class was not loaded yet.
   * @throws IllegalArgumentException if there is no object of the given name.
   * @throws DebugException
   */
  def objectByName(objectName: String, tryForceLoad: Boolean, thread: ScalaThread): ScalaObjectReference = {
    val moduleClassName = objectName + '$'
    wrapJDIException("Exception while retrieving module debug element `" + moduleClassName + "`") {
      classByName(moduleClassName, tryForceLoad, thread).fieldValue("MODULE$").asInstanceOf[ScalaObjectReference]
    }
  }

  /**
   * Return a reference to the type with the given name in the debugged VM.
   *
   * @param objectName the name of the object, as defined in code (without '$').
   * @param tryForceLoad indicate if it should try to forceLoad the type if it is not loaded yet.
   * @param thread the thread to use to if a force load is needed. Can be `null` if tryForceLoad is `false`.
   *
   * @throws ClassNotLoadedException if the class was not loaded yet.
   */
  private def classByName(typeName: String, tryForceLoad: Boolean, thread: ScalaThread): ScalaReferenceType = {
    import scala.collection.JavaConverters._
    virtualMachine.classesByName(typeName).asScala.toList match {
      case t :: _ =>
        ScalaType(t, this)
      case Nil =>
        if (tryForceLoad) {
          forceLoad(typeName, thread)
        } else {
          throw new ClassNotLoadedException(typeName, "No force load requested for " + typeName)
        }
    }
  }

  /**
   * Attempt to force load a type, by finding the classloader of `scala.Predef` and calling `loadClass` on it.
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

  /**
   * Return the classloader of the given object.
   */
  private def getClassLoader(instance: ScalaObjectReference, thread: ScalaThread): ScalaObjectReference = {
    val typeClassLoader = instance.underlying.referenceType().classLoader()
    if (typeClassLoader == null) {
      // JDI returns null for classLoader() if the classloader is the boot classloader.
      // Fetch the boot classloader by using ClassLoader.getSystemClassLoader()
      val classLoaderClass = classByName("java.lang.ClassLoader", false, null).asInstanceOf[ScalaClassType]
      classLoaderClass.invokeMethod("getSystemClassLoader", thread).asInstanceOf[ScalaObjectReference]
    } else {
      new ScalaObjectReference(typeClassLoader, this)
    }
  }

  /**
   * Called when attaching to a remote VM. Makes the companion actor run the initialization
   *  protocol (listen to the event queue, etc.)
   *
   *  @note This method has no effect if the actor was already initialized
   */
  def attached(): Unit =
    subordinate.attachedToVm()

  /*
   * Methods used by the companion actor to update this object internal states
   * FOR THE COMPANION ACTOR ONLY.
   */

  /**
   * Callback form the actor when the connection with the vm is enabled.
   *
   *  This method initializes the debug target object:
   *   - retrieves the initial list of threads and creates the corresponding debug elements.
   *   - initializes the breakpoint manager
   *   - fires a change event
   */
  private[model] def vmStarted(): Unit = {
    // get the current requests
    import scala.collection.JavaConverters._
    initializeThreads(virtualMachine.allThreads.asScala.toList)
    breakpointManager.init()
    hcrManager.foreach(_.init())
    fireChangeEvent(DebugEvent.CONTENT)
  }

  /**
   * Callback from the actor when the connection with the vm as been lost
   */
  private[model] def vmDisconnected(): Unit = {
    if (running.getAndSet(false)) {
      eventDispatcher.dispose()
      breakpointManager.dispose()
      subordinate.removeSubscriptions()
      hcrManager.foreach(_.dispose())
      cache.dispose()
      disposeThreads()
      fireTerminateEvent()
    }
  }

  private def disposeThreads(): Unit = {
    val previousThreads = threads.getAndSet(Nil)
    previousThreads.foreach {
      _.dispose()
    }
  }

  /**
   * Add a thread to the list of threads.
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def addThread(thread: ThreadReference): Unit = {
    // TODO: replace with getAndUpdate when java 1.8
    var prev, next: List[ScalaThread] = Nil
    do {
      prev = threads.get
      next = if (prev.exists(_.threadRef eq thread)) prev else prev :+ ScalaThread(this, thread)
    } while (!threads.compareAndSet(prev, next))
  }

  /**
   * Remove a thread from the list of threads
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def removeThread(thread: ThreadReference): Unit = {
    // TODO: replace with getAndUpdate when java 1.8
    var prev: List[ScalaThread] = Nil
    var next: (List[ScalaThread], List[ScalaThread]) = (Nil, Nil)
    do {
      prev = threads.get
      next = prev.partition(_.threadRef eq thread)
    } while (!threads.compareAndSet(prev, next._2))
    next._1.foreach(_.terminatedFromScala())
  }

  /**
   * Set the initial list of threads.
   * FOR THE COMPANION ACTOR ONLY.
   */
  private[model] def initializeThreads(t: List[ThreadReference]): Unit = {
    threads.getAndSet(t.map(ScalaThread(this, _)))
  }

  /**
   * Refreshes frames of all suspended, non-system threads and optionally drops affected stack frames.
   */
  private[internal] def updateStackFramesAfterHcr(dropAffectedFrames: Boolean): Unit =
    subordinate.updateStackFrameAfterHcr(dropAffectedFrames)

  /**
   * Return the current list of threads
   */
  private[model] def getScalaThreads: List[ScalaThread] = threads.get

  private[model] def canPopFrames: Boolean = running.get && virtualMachine.canPopFrames()

}

private[model] object ScalaDebugTargetSubordinate {
  import scala.concurrent.ExecutionContext.Implicits.global
  def apply(threadStartRequest: ThreadStartRequest, threadDeathRequest: ThreadDeathRequest, debugTarget: ScalaDebugTarget): ScalaDebugTargetSubordinate = {
    val subordinate = new ScalaDebugTargetSubordinate(threadStartRequest, threadDeathRequest, debugTarget)
    if (!IScalaPlugin().headlessMode) subordinate.subscribe(HotCodeReplaceListener)
    subordinate
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
private[model] class ScalaDebugTargetSubordinate private (threadStartRequest: ThreadStartRequest, threadDeathRequest: ThreadDeathRequest, protected val debugTarget: ScalaDebugTarget)(implicit ec: ExecutionContext)
    extends JdiEventReceiver with HotCodeReplaceExecutor {
  /** Is this actor initialized and listening to thread events? */
  private val initialized = new AtomicBoolean

  override protected[model] def innerHandle = vmEventsHandle orElse threadEventsHandle

  private[model] def attachedToVm(): Future[Unit] = Future {
    initialize()
  }

  private[model] def threadSuspended(thread: ThreadReference, eventDetail: Int): Future[Unit] = Future {
    // forward the event to the right thread
    debugTarget.getScalaThreads.find(_.threadRef == thread).foreach(_.suspendedFromScala(eventDetail))
  }

  private[model] def updateStackFrameAfterHcr(dropAffectedFrames: Boolean): Future[Unit] = Future {
    val nonSystemThreads = debugTarget.getScalaThreads.filterNot(_.isSystemThread)
    nonSystemThreads.foreach(_.updateStackFramesAfterHcr(dropAffectedFrames))
  }

  private def threadEventsHandle: PartialFunction[Event, StaySuspended] = {
    case threadStartEvent: ThreadStartEvent =>
      debugTarget.addThread(threadStartEvent.thread)
      false
    case threadDeathEvent: ThreadDeathEvent =>
      debugTarget.removeThread(threadDeathEvent.thread)
      false
  }

  private def vmEventsHandle: PartialFunction[Event, StaySuspended] = {
    case _: VMStartEvent =>
      initialize()
      false
    case _: VMDeathEvent | _: VMDisconnectEvent =>
      exit()
      false
  }

  /**
   * Initialize this debug target actor:
   *
   *   - listen to thread start/death events
   *   - initialize the companion debug target
   */
  private def initialize(): Unit = {
    if (!initialized.getAndSet(true)) {
      val eventDispatcher = debugTarget.eventDispatcher
      // enable the thread management requests
      eventDispatcher.register(this, threadStartRequest)
      threadStartRequest.enable()
      eventDispatcher.register(this, threadDeathRequest)
      threadDeathRequest.enable()
      debugTarget.vmStarted()
    }
  }

  private def exit(): Unit = {
    debugTarget.vmDisconnected()
    // don't have to unregister from event dispatcher. debugTarget makes total cleanse.
  }
}
