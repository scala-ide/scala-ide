/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/license.html
 */
package org.scalaide.core.compiler

import org.junit.Test
import org.junit.Assert._

class PresentationCompilerActivityListenerTest {

  class MockFun[T](realFunctionName: String, x: => T) extends Function0[T] {
    var numberOfInvocations = 0

    override def apply(): T = {
      numberOfInvocations += 1
      x
    }

    def mustHaveNumberOfInvocationsEqual(expected: Int): Unit =
      assertEquals(s"Incorrect number of invocations of $realFunctionName function", expected, numberOfInvocations)
  }

  class MockShutdownFun extends MockFun("shutdownPresentationCompiler", ())

  val readIgnoreOpenEditorsFunName = "readIgnoreOpenEditors"
  val readMaxIdlenessLengthMillisFunName = "readMaxIdlenessLengthMillis"
  val projectHasOpenEditorsFunName = "projectHasOpenEditors"

  implicit def anyVal2Fun[T <: AnyVal](x: T) = () => x

  def createListener(shutdownFun: () => Unit = (), ignoreOpenEditors: () => Boolean, maxIdlenessLengthMillis: () => Long, hasOpenEditors: () => Boolean = false) =
    new PresentationCompilerActivityListener(projectName = "notImportantHere", hasOpenEditors, shutdownFun) {
      override protected def readIgnoreOpenEditors = ignoreOpenEditors()
      override protected def readMaxIdlenessLengthMillis = maxIdlenessLengthMillis()
    }

  @Test
  def loadPreferencesDuringEachStart(): Unit = {
    val ignoreMock = new MockFun(readIgnoreOpenEditorsFunName, false)
    val millisMock = new MockFun(readMaxIdlenessLengthMillisFunName, 100L)
    val listener = createListener(ignoreOpenEditors = ignoreMock, maxIdlenessLengthMillis = millisMock)

    listener.start()

    ignoreMock mustHaveNumberOfInvocationsEqual 1
    millisMock mustHaveNumberOfInvocationsEqual 1

    listener.stop()
    listener.start()

    ignoreMock mustHaveNumberOfInvocationsEqual 2
    millisMock mustHaveNumberOfInvocationsEqual 2

    listener.stop()
  }

  @Test
  def closeRegardlessOfExistingOpenEditors(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = 100, hasOpenEditors = true)

    listener.start()
    Thread.sleep(300)

    shutdownMock mustHaveNumberOfInvocationsEqual 1
    listener.stop()
  }

  @Test
  def doNotCloseDueToOpenEditors(): Unit = {
    val shutdownMock = new MockShutdownFun
    val hasOpenEditorsMock = new MockFun(projectHasOpenEditorsFunName, true)
    val listener = createListener(shutdownMock, ignoreOpenEditors = false, maxIdlenessLengthMillis = 100, hasOpenEditorsMock)

    listener.start()
    Thread.sleep(290)

    hasOpenEditorsMock mustHaveNumberOfInvocationsEqual 2 // all checks performed so far
    shutdownMock mustHaveNumberOfInvocationsEqual 0
    listener.stop()
  }

  @Test
  def closeInTheCaseOfLackOfOpenEditors(): Unit = {
    val shutdownMock = new MockShutdownFun
    val hasOpenEditorsMock = new MockFun(projectHasOpenEditorsFunName, false)
    val listener = createListener(shutdownMock, ignoreOpenEditors = false, maxIdlenessLengthMillis = 100, hasOpenEditorsMock)

    listener.start()
    Thread.sleep(300)

    hasOpenEditorsMock mustHaveNumberOfInvocationsEqual 1
    shutdownMock mustHaveNumberOfInvocationsEqual 1
    listener.stop()
  }

  @Test
  def takeActivityIntoAccount(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = 300)

    listener.start()
    Thread.sleep(200)

    listener.noteActivity()
    Thread.sleep(200)

    shutdownMock mustHaveNumberOfInvocationsEqual 0

    Thread.sleep(200)

    shutdownMock mustHaveNumberOfInvocationsEqual 1
    listener.stop()
  }

  @Test
  def stopListener(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = 300)

    listener.start()
    listener.stop()
    Thread.sleep(500)

    shutdownMock mustHaveNumberOfInvocationsEqual 0
  }
}
