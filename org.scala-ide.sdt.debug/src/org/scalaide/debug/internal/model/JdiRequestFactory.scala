/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.model

import org.scalaide.debug.internal.JDIUtil
import org.scalaide.logging.HasLogger
import org.scalaide.util.Utils.jdiSynchronized

import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.VirtualMachine
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.ClassPrepareRequest
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.StepRequest
import com.sun.jdi.request.ThreadDeathRequest
import com.sun.jdi.request.ThreadStartRequest

/** Utility methods used to create JDI request.
 *  This object doesn't use any internal field, and is thread safe.
 */
object JdiRequestFactory extends HasLogger {

  /** Create a breakpoint on the first instruction of the method, on the given thread
   */
  def createMethodEntryBreakpoint(method: Method, thread: ScalaThread): BreakpointRequest = jdiSynchronized {
    val breakpointRequest = createMethodEntryBreakpoint(method, thread.getDebugTarget)
    breakpointRequest.addThreadFilter(thread.threadRef)

    breakpointRequest
  }

  def createMethodEntryBreakpoint(method: Method, debugTarget: ScalaDebugTarget): BreakpointRequest = {
    val breakpointRequest = debugTarget.virtualMachine.eventRequestManager.createBreakpointRequest(method.location)
    breakpointRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)

    breakpointRequest
  }

  /** create a step request on the given thread
   */
  def createStepRequest(size: Int, depth: Int, thread: ScalaThread): StepRequest = jdiSynchronized {
    val stepOverRequest = thread.getDebugTarget.virtualMachine.eventRequestManager.createStepRequest(thread.threadRef, size, depth)
    stepOverRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
    stepOverRequest
  }

  /** create a class prepare request for the pattern
   */
  def createClassPrepareRequest(typeNamePattern: String, debugTarget: ScalaDebugTarget): ClassPrepareRequest = jdiSynchronized {
    val classPrepareRequest = debugTarget.virtualMachine.eventRequestManager.createClassPrepareRequest
    classPrepareRequest.addClassFilter(typeNamePattern)
    classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
    classPrepareRequest
  }

  /** create a line breakpoint at the given line, if available
   */
  def createBreakpointRequest(referenceType: ReferenceType, lineNumber: Int, debugTarget: ScalaDebugTarget, suspendPolicy: Int): Option[BreakpointRequest] =
    jdiSynchronized {
      val locations = JDIUtil.referenceTypeToLocations(referenceType)
      // TODO: is it possible to have the same line number in multiple locations? need test case
      // see #1001370
      val line = locations.find(_.lineNumber == lineNumber)
      line.map {
        l =>
          val breakpointRequest = debugTarget.virtualMachine.eventRequestManager.createBreakpointRequest(l)
          breakpointRequest.setSuspendPolicy(suspendPolicy)
          breakpointRequest
      }
    }

  /** Create thread start request
   */
  def createThreadStartRequest(virtualMachine: VirtualMachine): ThreadStartRequest = jdiSynchronized {
    val threadStartRequest = virtualMachine.eventRequestManager.createThreadStartRequest
    threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE)
    threadStartRequest
  }

  /** Create thread death request
   */
  def createThreadDeathRequest(virtualMachine: VirtualMachine): ThreadDeathRequest = jdiSynchronized {
    val threadStartRequest = virtualMachine.eventRequestManager.createThreadDeathRequest
    threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE)
    threadStartRequest
  }

}
