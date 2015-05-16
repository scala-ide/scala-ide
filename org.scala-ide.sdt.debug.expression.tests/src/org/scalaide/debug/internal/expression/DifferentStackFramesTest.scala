/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import org.eclipse.jdt.debug.core.IJavaBreakpoint
import org.eclipse.jface.viewers.StructuredSelection
import org.junit.Assert._
import org.junit.Test
import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.debug.internal.expression.TestValues.DifferentStackFramesTestCase
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.debug.internal.model.ScalaThread

class DifferentStackFramesTest extends BaseIntegrationTest(DifferentStackFramesTest) {

  private def changeThread(name: String) {
    val newThread = ScalaDebugger.currentThread.getDebugTarget.getThreads
      .filter(_.getName == name).head.asInstanceOf[ScalaThread]
    ScalaDebugger.updateCurrentThread(new StructuredSelection(newThread))
    assertTrue(s"Thread $name is not suspended", ScalaDebugger.currentThread.isSuspended)
  }

  private def changeFrame(index: Int) {
    val currentThread = ScalaDebugger.currentThread
    val newFrame = ScalaStackFrame(currentThread, currentThread.threadRef.frame(index), index)
    ScalaDebugger.updateCurrentThread(new StructuredSelection(newFrame))
  }

  @Test
  def testFrameAccess() {
    /* Frames:
    0: recFunction(0)
    1: recFunction(1)
    2: recFunction(2)
    3: recFunction(3)
    4: compute()
    ...
     */
    eval("input", 0, Names.Java.primitives.int)
    changeFrame(1)
    eval("input", 1, Names.Java.primitives.int)
    changeFrame(2)
    eval("input", 2, Names.Java.primitives.int)
    changeFrame(4)
    eval("input", 5, Names.Java.primitives.int)

    changeFrame(0)
    eval("input", 0, Names.Java.primitives.int)
  }

  @Test
  def testThreadAccess() {
    changeThread(DifferentStackFramesTestCase.demonThreadName)

    ExpressionManager.compute("input") match {
      case EvaluationFailure(errorMessage) => assertTrue(s"Error message differs, got: $errorMessage",
        errorMessage.contains("is not suspended as a result of JDI event."))
      case other => fail(s"Expected `not at breakpoint` message, got: $other")
    }

    changeThread(DifferentStackFramesTestCase.mainThread)
    eval("input", 0, Names.Java.primitives.int)

    companion.session.disconnect()
  }
}

object DifferentStackFramesTest extends BaseIntegrationTestCompanion(DifferentStackFramesTestCase) {

  override protected val suspensionPolicy: Int = IJavaBreakpoint.SUSPEND_VM
}
