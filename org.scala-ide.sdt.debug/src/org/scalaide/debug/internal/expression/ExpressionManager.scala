/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import scala.collection.JavaConversions.asScalaBuffer
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.IDebugEventSetListener
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaLineBreakpoint
import org.eclipse.jdt.internal.debug.core.model.JDIThread
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.logging.HasLogger
import com.sun.jdi.ThreadReference
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.ObjectReference
import org.scalaide.debug.internal.expression.context.JdiContext

/**
 * Main entry point to expression evaluation.
 *
 * It's initialized in `ScalaDebugger.init()` method and registers itself as debug event listener.
 */
object ExpressionManager
  extends ExpressionManagerDebugEventListener
  with HasLogger

/**
 * Implementation of Expression manager based on evaluator.
 */
trait ExpressionManager {
  self: HasLogger =>

  /**
   * Expression evaluator that should be used.
   * It's set to `Some` only when debug is running and thread is suspended.
   *
   * @param thread thread to run debug session with when evaluating breakpoint condition
   */
  protected def currentEvaluator: Option[JdiExpressionEvaluator]

  /**
   * Debugging session to which current evaluator is related
   */
  protected def currentSession: Option[DebuggingSession]

  /**
   * Thread suspended on breakpoint. If debug is not running or breakpoint is not hit, it's None.
   */
  protected def currentThread: Option[ThreadReference]

  /**
   * Create expression evaluator
   *
   * @param thread thread to run debug session with when evaluating breakpoint condition
   */
  protected def createEvaluator(currentSession: DebuggingSession, thread: ThreadReference): JdiExpressionEvaluator

  /**
   * Returns the highest frame from the debugging stack frames.
   */
  def currentStackFrame() = currentThread match {
    case Some(thread) if thread.frameCount() > 0 => Option(thread.frame(0)) // frame sometimes can be null (defensive programming)
    case _ => None
  }

  /**
   * Computes an expression and runs appropriate callback.
   *
   * @param exp expression to evaluate
   * @param resultCallback side-effecting function that is called when expression is correctly evaluated
   * @param errorCallback side-effecting function that is called when expression ends with exception
   */
  def compute(exp: String, resultCallback: (ObjectReference, String) => Unit, errorCallback: String => Unit): Unit = {
    def prettify(msg: String) = s"<<< $msg >>>"
    val debugNotRunning = prettify("Expression evaluation works only when debug is running and jvm is suspended")
    val errorDuringEval = prettify("Expression evaluation failed")
    val emptyCode = prettify("Expression is empty")

    def show(proxy: JdiProxy): String = proxy.context.show(proxy)

    def computeInEvaluator(evaluator: JdiExpressionEvaluator): Unit = {
      evaluator(exp) match {
        case Success(result) =>
          resultCallback(result.underlying, show(result))
        case Failure(exception) =>
          val errorMessage = s"$errorDuringEval\n${exception.getMessage}"
          logger.error(errorMessage, exception)
          errorCallback(errorMessage)
      }
    }

    if (exp.isEmpty) {
      errorCallback(emptyCode)
    } else {
      currentEvaluator
        .map(computeInEvaluator)
        .getOrElse(errorCallback(debugNotRunning))
    }
  }

  /**
   * Checks for given breakpoint if VM should be suspended.
   *
   * @param event jdi breakpoint event
   * @param breakpoint eclipse breakpoint
   * @return Success(should vm be suspended) or Failure(reason why evaluation failed)
   */
  def shouldSuspendVM(event: BreakpointEvent, breakpoint: IBreakpoint): Try[Boolean] = {
    breakpoint match {
      case lineBreakpoint: JavaLineBreakpoint =>
        val condition = lineBreakpoint.getCondition
        getCondition(lineBreakpoint) match {
          case Some(condition) =>
            evaluateCondition(condition, event)
              .getOrElse(Failure(new IllegalArgumentException("No debugging session!")))
          case None =>
            Success(true)
        }
      case other =>
        Failure(new IllegalArgumentException(s"Unknown breakpoint type: $other"))
    }
  }

  private def evaluateCondition(condition: String, event: BreakpointEvent): Option[Try[Boolean]] = {
    val thread = event.thread()
    val location = event.location()

    currentSession.map {
      session =>
        val evaluator = createEvaluator(session, thread)
        val context = evaluator.createContext()
        session.conditionManager.checkCondition(condition, location)(evaluator.compileExpression(context))(_.apply(context))
    }
  }

  private def getCondition(lineBreakpoint: JavaLineBreakpoint): Option[String] = {
    val condition = lineBreakpoint.getCondition
    if (lineBreakpoint.hasCondition && condition != null && !condition.trim.isEmpty)
      Some(condition)
    else
      None
  }
}

case class DebuggingSession(debugTarget: ScalaDebugTarget, conditionManager: ConditionManager)

/**
 * Debug event listener for expression manager. It tracks current `ScalaDebugTarget` and `ThreadReference`
 * during debug and creates JdiExpressionEvaluator based on them.
 */
trait ExpressionManagerDebugEventListener
  extends ExpressionManager
  with IDebugEventSetListener {
  self: HasLogger =>

  /** Holds current Debug target, it's set up when debug starts and cleared after it ends */
  private var _currentSession: Option[DebuggingSession] = None

  /** Holds current Thread reference, it's set up when VM suspends on breakpoint and cleared on resume */
  private var _currentThread: Option[ThreadReference] = None

  override def handleDebugEvents(events: Array[DebugEvent]): Unit = events.foreach {
    event =>
      event.getKind match {
        case DebugEvent.CREATE => onCreate(event)
        case DebugEvent.SUSPEND | DebugEvent.BREAKPOINT => onScalaSuspend(event)
        case DebugEvent.RESUME => onResume()
        case DebugEvent.TERMINATE => onEnd(event)
        case other => throw new RuntimeException(s"Bad debug event value: $other")
      }
  }

  /**
   * Expression evaluator that should be used.
   * It's set to `Some` only when debug is running and thread is suspended.
   *
   * @param thread thread to run debug session with when evaluating breakpoint condition
   */
  protected override def currentSession: Option[DebuggingSession] = _currentSession

  protected override def currentThread: Option[ThreadReference] = _currentThread

  /** Builds expression evaluator based on current thread and debug target */
  protected override def currentEvaluator: Option[JdiExpressionEvaluator] = for {
    session <- currentSession
    thread <- _currentThread
  } yield createEvaluator(session, thread)

  /** Creates expression evaluator */
  protected override def createEvaluator(currentSession: DebuggingSession, thread: ThreadReference): JdiExpressionEvaluator =
    new JdiExpressionEvaluator(currentSession.debugTarget, thread)

  /** Clear thread from current evaluator */
  private def clearThread(): Unit = _currentThread = None

  /** Clear target from current evaluator */
  private def clearThreadAndTarget(): Unit = {
    _currentSession = None
    _currentThread = None
  }

  private def onCreate(event: DebugEvent): Unit = event.getSource match {
    case scala: ScalaDebugTarget =>
      logger.info("Create Scala Debugging")
      _currentSession = Some(DebuggingSession(scala, new ConditionManager))
    case _ =>
      logger.info("Create Debugging")
  }

  private def threadFrom(event: DebugEvent): Option[ThreadReference] = {
    val source = event.getSource

    source match {
      case jdiThread: JDIThread =>
        Some(jdiThread.getUnderlyingThread)
      case scala if isScalaThread(source) =>
        val jdiThread = callPrivateMethod[ThreadReference](source, methodName = "threadRef")
        logger.info("Suspend Debugging Scala: " + jdiThread)
        Some(jdiThread)
      case _ => None
    }
  }

  private def callPrivateMethod[R](callTarget: AnyRef, methodName: String): R = {
    val method = callTarget.getClass.getMethod(methodName)
    method.setAccessible(true)
    method.invoke(callTarget).asInstanceOf[R]
  }

  private def isScalaThread(source: AnyRef): Boolean = {
    source.getClass.getName.contains("ScalaThread")
  }

  private def onScalaSuspend(event: DebugEvent): Unit = {
    logger.info("Suspend Debugging " + event)
    threadFrom(event).map {
      thread =>
        _currentThread = Some(thread)
    }.getOrElse {
      clearThread()
    }
  }

  private def onResume(): Unit = {
    logger.info("Resume Debugging")
    clearThread()
  }

  private def onEnd(event: DebugEvent): Unit = {
    logger.info("End Debugging")
    threadFrom(event).foreach {
      thread =>
        val jvm = thread.virtualMachine()
        if (isJvmFinished(jvm)) {
          jvm.dispose()
        }
    }
    clearThreadAndTarget()
  }

  private def isJvmFinished(jvm: VirtualMachine): Boolean = {
    Try {
      val threads = jvm.allThreads().filter(_.frameCount > 0).map(_.name).toSet
      // 'Finalizer' is standard JVM thread and 'Reference Handler' is JDI thread
      threads == Set("Finalizer", "Reference Handler")
    }.getOrElse(true)
  }
}
