package scala.tools.eclipse.debug.model
import scala.tools.eclipse.debug.JDIUtil

import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.VirtualMachine
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.ClassPrepareRequest
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.StepRequest
import com.sun.jdi.request.ThreadDeathRequest
import com.sun.jdi.request.ThreadStartRequest

/**
 * Utility methods used to create JDI request.
 * This object doesn't use any internal field, and is thread safe.
 */
object JdiRequestFactory {

  /**
   * Create a breakpoint on the first instruction of the method, on the given thread
   */
  def createMethodEntryBreakpoint(method: Method, thread: ScalaThread): BreakpointRequest = {
    import scala.collection.JavaConverters._

    val breakpointRequest = thread.getDebugTarget.virtualMachine.eventRequestManager.createBreakpointRequest(method.location)
    breakpointRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
    breakpointRequest.addThreadFilter(thread.threadRef)

    breakpointRequest
  }

  /**
   * create a step request on the given thread
   */
  def createStepRequest(size: Int, depth: Int, thread: ScalaThread): StepRequest = {
    val stepOverRequest = thread.getDebugTarget.virtualMachine.eventRequestManager.createStepRequest(thread.threadRef, size, depth)
    stepOverRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
    stepOverRequest
  }

  /**
   * create a class prepare request for the pattern
   */
  def createClassPrepareRequest(typeNamePattern: String, debugTarget: ScalaDebugTarget): ClassPrepareRequest = {
    val classPrepareRequest = debugTarget.virtualMachine.eventRequestManager.createClassPrepareRequest
    classPrepareRequest.addClassFilter(typeNamePattern)
    classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
    classPrepareRequest
  }

  /**
   * create a line breakpoint at the given line, if available
   */
  def createBreakpointRequest(referenceType: ReferenceType, lineNumber: Int, debugTarget: ScalaDebugTarget): Option[BreakpointRequest] = {
    import scala.collection.JavaConverters._
    val locations = JDIUtil.referenceTypeToLocations(referenceType)
    // TODO: is it possible to have the same line number in multiple locations? need test case
    val line = locations.find(_.lineNumber == lineNumber)
    line.map {
      l =>
        val breakpointRequest = debugTarget.virtualMachine.eventRequestManager.createBreakpointRequest(l)
        breakpointRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
        breakpointRequest
    }
  }

  /**
   * Create thread start request
   */
  def createThreadStartRequest(virtualMachine: VirtualMachine): ThreadStartRequest = {
    val threadStartRequest = virtualMachine.eventRequestManager.createThreadStartRequest
    threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE)
    threadStartRequest
  }

  /**
   * Create thread death request
   */
  def createThreadDeathRequest(virtualMachine: VirtualMachine): ThreadDeathRequest = {
    val threadStartRequest = virtualMachine.eventRequestManager.createThreadDeathRequest
    threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE)
    threadStartRequest
  }

}