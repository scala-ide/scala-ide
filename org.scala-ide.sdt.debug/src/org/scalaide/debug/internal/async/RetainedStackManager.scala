package org.scalaide.debug.internal.async

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Success
import scala.util.Try

import org.scalaide.debug.internal.JdiEventReceiver
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.debug.internal.model.ClassPrepareListener
import org.scalaide.debug.internal.model.JdiRequestFactory
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.model.ScalaValue
import org.scalaide.debug.internal.preferences.AsyncDebuggerPreferencePage
import org.scalaide.logging.HasLogger

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.StackFrame
import com.sun.jdi.ThreadReference
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.Event
import com.sun.jdi.request.BreakpointRequest

/**
 * Installs breakpoints in key places and collect stack frames.
 */
class RetainedStackManager(debugTarget: ScalaDebugTarget) extends HasLogger {
  import org.scalaide.debug.internal.launching.ScalaDebuggerConfiguration._
  import scala.concurrent.ExecutionContext.Implicits.global

  final val MaxEntries = 20000
  private val stackFrames: mutable.Map[ObjectReference, AsyncStackTraces] = {
    import scala.collection.JavaConverters._
    new ConcurrentLinkedHashMap.Builder[ObjectReference, AsyncStackTraces]()
      .maximumWeightedCapacity(MaxEntries)
      .build()
      .asScala
  }
  private val breakpointRequests: mutable.Set[BreakpointRequest] = {
    import scala.collection.JavaConverters._
    Collections.newSetFromMap(new ConcurrentHashMap[BreakpointRequest, java.lang.Boolean]).asScala
  }
  private val messageOrdinal = new AtomicInteger

  object subordinate extends JdiEventReceiver with ClassPrepareListener {
    import scala.concurrent.ExecutionContext.Implicits.global
    override protected def innerHandle: PartialFunction[Event, StaySuspended] = {
      // JDI event triggered when a breakpoint is hit
      case breakpointEvent: BreakpointEvent =>
        appHit(breakpointEvent.thread, breakpointEvent.request().getProperty("app").asInstanceOf[AsyncProgramPoint], messageOrdinal.getAndIncrement)
        false // don't suspend this thread
    }

    override def notify(event: ClassPrepareEvent): Future[Unit] = Future {
      // JDI event triggered when a class has been loaded
      val refType = event.referenceType()
      // find the right app to install
      programPoints.find(_.className == refType.name()) foreach { installMethodBreakpoint(refType, _) }
    }
  }

  private def appHit(thread: ThreadReference, app: AsyncProgramPoint, messageOrdinal: => Int): Unit = {
    val topFrame = thread.frame(0)
    val args = topFrame.getArgumentValues()
    val body = args.get(app.paramIdx)
    try {
      val frames = thread.frames().asScala.toList
      addAsyncStackFrame(body.asInstanceOf[ObjectReference], mkStackTrace(frames, messageOrdinal), messageOrdinal)
    } catch {
      case t: Throwable => logger.error("Don't break here, allow it to fail later.", t)
    }
  }

  private def addAsyncStackFrame(key: ObjectReference, asyncStackTrace: AsyncStackTrace, ordinal: Int): Unit = {
    stackFrames.get(key) match {
      case Some(asyncStackTraces) =>
        stackFrames.put(key, asyncStackTraces.add(ordinal, asyncStackTrace))
      case None =>
        stackFrames.put(key, AsyncStackTraces(List(AsyncStackTraceItem(asyncStackTrace, ordinal))))
    }
  }

  private def mkStackTrace(frames: Seq[StackFrame], ordinal: Int): AsyncStackTrace = {
    import collection.JavaConverters._

    val asyncFrames = frames.map { frame =>
      Try {
        val names = frame.visibleVariables()
        val values = frame.getValues(names)
        val locals = values.asScala.map {
          case (lvar, lval) => AsyncLocalVariable(lvar.name(), ScalaValue(lval, debugTarget), ordinal)(debugTarget)
        }
        val location = frame.location()
        AsyncStackFrame(locals.toSeq, Location(location.sourceName, location.declaringType.name, location.lineNumber))(debugTarget)
      }
    } collect {
      case Success(asyncStackTrace) => asyncStackTrace
    }

    AsyncStackTrace(asyncFrames)
  }

  private def installMethodBreakpoint(tpe: ReferenceType, app: AsyncProgramPoint): Unit = {
    val method = AsyncUtils.findAsyncProgramPoint(app, tpe)
    method.foreach { meth =>
      val req = JdiRequestFactory.createMethodEntryBreakpoint(method.get, debugTarget)
      debugTarget.eventDispatcher.register(subordinate, req)
      req.putProperty("app", app)
      req.enable()
      breakpointRequests += req
      logger.debug(s"Installed method breakpoint for ${method.get.declaringType()}.${method.get.name}")
    }
  }

  private val programPoints = {
    val app = ScalaDebugPlugin.plugin.getPreferenceStore.getString(AsyncDebuggerPreferencePage.AsyncProgramPoints)
    app.split(AsyncDebuggerPreferencePage.DataDelimiter).map(_.split(",")).map {
      case Array(className, methodName, paramIdx) ⇒ AsyncProgramPoint(className, methodName, paramIdx.toInt)
    }.toList
  }

  /** Return the saved stackframes for the given future body (if any). */
  def getStackFrameForFuture(future: ObjectReference, messageOrdinal: Int): Option[AsyncStackTrace] =
    stackFrames.get(future).flatMap(_(messageOrdinal))

  def start(): Unit = if (debugTarget.getLaunch.getLaunchConfiguration.getAttribute(LaunchWithAsyncDebugger, false)) {
    programPoints foreach {
      case app @ AsyncProgramPoint(clazz, _, _) ⇒
        val refType = debugTarget.virtualMachine.classesByName(clazz).asScala
        if (!refType.isEmpty)
          installMethodBreakpoint(refType(0), app)
        else
          // in case it's not been loaded yet
          debugTarget.cache.addClassPrepareEventListener(subordinate, clazz)
    }
  }

  def dispose(): Unit = Future {
    breakpointRequests.foreach { br =>
      debugTarget.eventDispatcher.unregister(br)
      br.disable()
    }
    breakpointRequests.clear()
    stackFrames.clear()
  }
}

object RetainedStackManager {
  val OrdinalNotSet = -1
}

private case class AsyncStackTraceItem(asyncStackTrace: AsyncStackTrace, ordinal: Int)

private case class AsyncStackTraces(indexedAsyncStackTraces: List[AsyncStackTraceItem]) {
  import RetainedStackManager._
  type IndexedAsyncStackTrace = (AsyncStackTrace, Int)

  def add(uniqueOrdinal: Int, asyncStackTrace: AsyncStackTrace): AsyncStackTraces =
    AsyncStackTraces(indexedAsyncStackTraces :+ AsyncStackTraceItem(asyncStackTrace, uniqueOrdinal))

  def apply(index: Int): Option[AsyncStackTrace] =
    if (indexedAsyncStackTraces.tail.isEmpty)
      indexedAsyncStackTraces.headOption.map(_.asyncStackTrace)
    else {
      if (OrdinalNotSet == index)
        indexedAsyncStackTraces.lastOption.map(_.asyncStackTrace)
      else {
        val previousTraces = indexedAsyncStackTraces.collect {
          case item @ AsyncStackTraceItem(_, ordinal) if ordinal < index => item
        }
        if (previousTraces.nonEmpty)
          Option(previousTraces.maxBy(_.ordinal).asyncStackTrace)
        else
          None
      }
    }
}
