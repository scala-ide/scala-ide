package org.scalaide.debug.internal.async

import java.util.UUID

import scala.collection.JavaConverters

import org.eclipse.debug.core.DebugEvent
import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.debug.internal.async.AsyncUtils.AsyncProgramPointKey
import org.scalaide.debug.internal.async.AsyncUtils.RequestOwnerKey
import org.scalaide.debug.internal.async.AsyncUtils.installMethodBreakpoint
import org.scalaide.debug.internal.async.AsyncUtils.isAsyncProgramPoint
import org.scalaide.debug.internal.model.JdiRequestFactory
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.model.ScalaThread
import org.scalaide.logging.HasLogger

import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import com.sun.jdi.ThreadReference
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.Event
import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.StepRequest

case class StepMessageOut(debugTarget: ScalaDebugTarget, thread: ScalaThread) extends HasLogger {
  private val StepMessageOut = "StepMessageOut" + UUID.randomUUID.toString
  private val IsSearchingReceiveMethodKey = "search"
  private var watchedMessage: Option[ObjectReference] = None
  private var watchedActor: String = _
  val programSends = List(
    AsyncProgramPoint("akka.actor.RepointableActorRef", "$bang", 0),
    AsyncProgramPoint("akka.actor.LocalActorRef", "$bang", 0),
    AsyncProgramPoint("scala.actors.InternalReplyReactor$class", "$bang", 1))
  val programReceives = List(
    AsyncProgramPoint("akka.actor.ActorCell", "receiveMessage", 0))
  private var receiveRequest: Option[EventRequest] = None
  private var stepRequests = Set[EventRequest]()
  private var steps = 0
  private val depth = thread.getScalaStackFrames.size
  private val line = thread.getTopStackFrame().getLineNumber()
  private val Halt = true
  private val Continue = false

  def step(): Unit = {
    subordinate.start()
    subordinate.establishRequestToStopInTellMethod()
    thread.resumeFromScala(DebugEvent.CLIENT_REQUEST)
  }

  object subordinate extends BaseDebuggerActor {
    import scala.collection.JavaConverters._
    private def isReceiveHandler(loc: com.sun.jdi.Location): Boolean =
      loc.method().name().contains("applyOrElse")

    override protected def behavior = {
      case breakpointEvent: BreakpointEvent if isSearchingReceiveMethod(breakpointEvent) =>
        val topFrame = breakpointEvent.thread().frame(0)
        val args = topFrame.getArgumentValues()
        logger.debug(s"receive intercepted: topFrame arguments: $args")
        val app = breakpointEvent.request().getProperty(AsyncProgramPointKey).asInstanceOf[AsyncProgramPoint]
        val msg = Option(args.get(app.paramIdx).asInstanceOf[ObjectReference])
        if (watchedMessage == msg && watchedActor == actorPathValue(path(self(topFrame.thisObject())), breakpointEvent.thread)) {
          logger.debug(s"MESSAGE IN! $msg")
          val targetThread = debugTarget.getScalaThread(breakpointEvent.thread())
          targetThread foreach { thread =>
            deleteReceiveRequest()
            establishRequestToStopInReceiveMethod(thread)
          }
        }
        reply(Continue)

      case stepEvent: StepEvent if isSearchingReceiveMethod(stepEvent) && isReceiveHandler(stepEvent.location) =>
        terminate()
        logger.debug(s"Suspending thread ${stepEvent.thread.name()}")
        // most likely the breakpoint was hit on a different thread than the one we started with, so we find it here
        debugTarget.getScalaThread(stepEvent.thread()).foreach(_.suspendedFromScala(DebugEvent.BREAKPOINT))
        reply(Halt)

      case stepEvent: StepEvent if isSearchingReceiveMethod(stepEvent) && steps >= 20 =>
        terminate()
        logger.debug(s"Giving up on stepping after 15 steps")
        reply(Continue)

      case stepEvent: StepEvent if isSearchingTellMethod(stepEvent) =>
        val decision = if (stepEvent.thread().frameCount() == depth && stepEvent.location().lineNumber() > line) {
          logger.debug(s"Sending method not found. Leaving...")
          terminate()
          debugTarget.getScalaThread(stepEvent.thread()).foreach(_.suspendedFromScala(DebugEvent.BREAKPOINT))
          Halt
        } else {
          val foundApp = programSends.find { isAsyncProgramPoint(_)(stepEvent.location.method) }
          foundApp.map { app =>
            deleteAllRequests()
            val topFrame = stepEvent.thread().frame(0)
            val args = topFrame.getArgumentValues()
            logger.debug(s"MESSAGE OUT intercepted: topFrame arguments: $args")
            watchedMessage = Option(args.get(app.paramIdx).asInstanceOf[ObjectReference])
            watchedActor = actorPathValue(path(topFrame.thisObject()), thread.threadRef)
            establishRequestToStopInReceiveMessageMethod()
          }
          Continue
        }
        reply(decision)

      case stepEvent: StepEvent if isSearchingReceiveMethod(stepEvent) =>
        steps += 1
        logger.debug(s"Step $steps: ${stepEvent.location.declaringType}.${stepEvent.location.method}")
        reply(Continue)

      case _ =>
        reply(Continue)
    }

    private[async] def establishRequestToStopInTellMethod(): Unit = {
      val stepIntoReq = JdiRequestFactory.createStepRequest(StepRequest.STEP_MIN, StepRequest.STEP_INTO, thread)
      stepIntoReq.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
      stepIntoReq.putProperty(AsyncProgramPointKey, programReceives)
      stepIntoReq.putProperty(RequestOwnerKey, StepMessageOut)
      stepIntoReq.enable()
      stepRequests = Set(stepIntoReq)
      debugTarget.eventDispatcher.setActorFor(this, stepIntoReq)
    }

    private def establishRequestToStopInReceiveMessageMethod(): Unit = {
      receiveRequest = programReceives.flatMap(installMethodBreakpoint(debugTarget, _, this, Some(StepMessageOut))).headOption
      receiveRequest.foreach { _.putProperty(IsSearchingReceiveMethodKey, true) }
    }

    private def establishRequestToStopInReceiveMethod(thread: ScalaThread): Unit = {
      val stepReq = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_INTO, thread)
      stepReq.putProperty(RequestOwnerKey, StepMessageOut)
      stepReq.putProperty(IsSearchingReceiveMethodKey, true)
      stepReq.enable()
      debugTarget.eventDispatcher.setActorFor(this, stepReq)
      stepRequests = Set(stepReq)
      steps = 0
    }

    private def isOwning(event: Event) = event.request.getProperty(RequestOwnerKey) == StepMessageOut

    private def isSearchingReceiveMethod(event: Event) = event.request.getProperty(IsSearchingReceiveMethodKey) == true && isOwning(event)

    private def isSearchingTellMethod(event: Event) = event.request.getProperty(IsSearchingReceiveMethodKey) != true && isOwning(event)

    private def actorPathValue(actorPathObjRef: ObjectReference, threadRef: ThreadReference): String = {
      val toStringMethod = actorPathObjRef.referenceType().methodsByName("toString", "()Ljava/lang/String;").asScala.head
      actorPathObjRef.invokeMethod(threadRef, toStringMethod, Nil.asJava, ObjectReference.INVOKE_SINGLE_THREADED).asInstanceOf[StringReference].value()
    }

    private def path(actorRef: ObjectReference): ObjectReference = {
      val pathField: Field = actorRef.referenceType().fieldByName("path")
      actorRef.getValue(pathField).asInstanceOf[ObjectReference]
    }

    private def self(actorCell: ObjectReference): ObjectReference = {
      val selfField = actorCell.referenceType().fieldByName("self")
      actorCell.getValue(selfField).asInstanceOf[ObjectReference]
    }

    private def deleteRequests(reqs: Set[EventRequest]): Unit = {
      val eventDispatcher = debugTarget.eventDispatcher
      val eventRequestManager = debugTarget.virtualMachine.eventRequestManager
      for (request <- reqs) {
        request.disable()
        eventDispatcher.unsetActorFor(request)
        eventRequestManager.deleteEventRequest(request)
      }
    }

    private def deleteAllRequests(): Unit = {
      deleteReceiveRequest()
      deleteStepRequests()
    }

    private def deleteStepRequests(): Unit = {
      deleteRequests(stepRequests)
      stepRequests = Set()
    }

    private def deleteReceiveRequest(): Unit = {
      deleteRequests(receiveRequest.toSet)
      receiveRequest = None
    }

    private def terminate(): Unit = {
      deleteAllRequests()
      poison()
    }
  }
}
