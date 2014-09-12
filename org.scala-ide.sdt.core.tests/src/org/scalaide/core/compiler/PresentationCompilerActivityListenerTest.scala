/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.core.compiler

import org.eclipse.jface.util.PropertyChangeEvent
import org.junit.Test
import org.junit.Assert._
import org.scalaide.ui.internal.preferences.ResourcesPreferences
import Thread.sleep

import org.scalaide.core.internal.compiler.PresentationCompilerActivityListener

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

  val readClosingEnabledName = "readClosingEnabled"
  val readIgnoreOpenEditorsFunName = "readIgnoreOpenEditors"
  val readMaxIdlenessLengthMillisFunName = "readMaxIdlenessLengthMillis"
  val projectHasOpenEditorsFunName = "projectHasOpenEditors"

  implicit def anyVal2Fun[T <: AnyVal](x: T) = () => x

  def createListener(shutdownFun: () => Unit = (), ignoreOpenEditors: () => Boolean, maxIdlenessLengthMillis: () => Long,
    hasOpenEditors: => Boolean = false, closingEnabled: () => Boolean = true) = {

    new PresentationCompilerActivityListener(projectName = "notImportantHere", hasOpenEditors, shutdownFun) {
      override protected def readClosingEnabled = closingEnabled()
      override protected def readIgnoreOpenEditors = ignoreOpenEditors()
      override protected def readMaxIdlenessLengthMillis = maxIdlenessLengthMillis()

      def firePropertyChangeEvent(property: String = ResourcesPreferences.PRES_COMP_PREFERENCES_CHANGE_MARKER): Unit =
        // only the property name is used by the listener - other values are intentionally read using above methods
        propertyChangeListener.propertyChange(new PropertyChangeEvent( /*source =*/ None, property, /*oldValue =*/ None, /*newValue =*/ None))
    }
  }

  def valuesForAnotherCalls[T](values: T*): () => T = {
    var remainingValues = values

    () => {
      val current = remainingValues.head
      if (remainingValues.length > 1) remainingValues = remainingValues.tail
      current
    }
  }

  @Test
  def checkIfClosingIsEnabledDuringEachStart(): Unit = {
    val enabledMock = new MockFun(readClosingEnabledName, true)
    val listener = createListener(ignoreOpenEditors = false, maxIdlenessLengthMillis = 500, closingEnabled = enabledMock)

    listener.start()

    enabledMock mustHaveNumberOfInvocationsEqual 1

    listener.stop()
    listener.start()

    enabledMock mustHaveNumberOfInvocationsEqual 2

    listener.stop()
  }

  @Test
  def loadPreferencesAlwaysWhenStartingTask(): Unit = {
    val ignoreMock = new MockFun(readIgnoreOpenEditorsFunName, false)
    val millisMock = new MockFun(readMaxIdlenessLengthMillisFunName, 50L)
    val listener = createListener(ignoreOpenEditors = ignoreMock, maxIdlenessLengthMillis = millisMock,
      closingEnabled = valuesForAnotherCalls( /* loaded during another starts*/ true, false, true))

    listener.start()

    ignoreMock mustHaveNumberOfInvocationsEqual 1
    millisMock mustHaveNumberOfInvocationsEqual 1

    listener.stop()
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
  def doNotCloseWhenClosingIsDisabled(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = 50, closingEnabled = false)

    listener.start()
    sleep(100)
    shutdownMock mustHaveNumberOfInvocationsEqual 0
    listener.stop()
  }

  @Test
  def closeRegardlessOfExistingOpenEditors(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = 50, hasOpenEditors = true)

    listener.start()
    sleep(150)

    shutdownMock mustHaveNumberOfInvocationsEqual 1
    listener.stop()
  }

  @Test
  def doNotCloseDueToOpenEditors(): Unit = {
    val shutdownMock = new MockShutdownFun
    val hasOpenEditorsMock = new MockFun(projectHasOpenEditorsFunName, true)
    val listener = createListener(shutdownMock, ignoreOpenEditors = false, maxIdlenessLengthMillis = 50, hasOpenEditors = hasOpenEditorsMock())

    listener.start()
    sleep(140)

    hasOpenEditorsMock mustHaveNumberOfInvocationsEqual 2 // all checks performed so far
    shutdownMock mustHaveNumberOfInvocationsEqual 0
    listener.stop()
  }

  @Test
  def closeInTheCaseOfLackOfOpenEditors(): Unit = {
    val shutdownMock = new MockShutdownFun
    val hasOpenEditorsMock = new MockFun(projectHasOpenEditorsFunName, false)
    val listener = createListener(shutdownMock, ignoreOpenEditors = false, maxIdlenessLengthMillis = 50, hasOpenEditors = hasOpenEditorsMock())

    listener.start()
    sleep(90)

    hasOpenEditorsMock mustHaveNumberOfInvocationsEqual 1
    shutdownMock mustHaveNumberOfInvocationsEqual 1
    listener.stop()
  }

  @Test
  def takeActivityIntoAccount(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = 150)

    listener.start()
    sleep(100)

    listener.noteActivity()
    sleep(100)

    shutdownMock mustHaveNumberOfInvocationsEqual 0

    sleep(100)

    shutdownMock mustHaveNumberOfInvocationsEqual 1
    listener.stop()
  }

  @Test
  def stopListener(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = 100)

    listener.start()
    listener.stop()
    sleep(150)

    shutdownMock mustHaveNumberOfInvocationsEqual 0
  }

  @Test
  def enablingClosingViaPreferences(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = 50,
      closingEnabled = valuesForAnotherCalls( /*initial value used during start*/ false, /*it will be used after another call when property will be changed*/ true))

    listener.start()
    sleep(60)
    listener.firePropertyChangeEvent()
    sleep(10)

    shutdownMock mustHaveNumberOfInvocationsEqual 1
    listener.stop()
  }

  @Test
  def disablingClosingViaPreferences(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = 50, closingEnabled = valuesForAnotherCalls(true, false))

    listener.start()
    listener.firePropertyChangeEvent()
    sleep(90)

    shutdownMock mustHaveNumberOfInvocationsEqual 0
    listener.stop()
  }

  @Test
  def changingIdlenessLengthToLongerViaPreferences(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = valuesForAnotherCalls(50, 500000))

    listener.start()
    listener.firePropertyChangeEvent()
    sleep(90)

    shutdownMock mustHaveNumberOfInvocationsEqual 0
    listener.stop()
  }

  @Test
  def changingIdlenessLengthToShorterViaPreferences(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = valuesForAnotherCalls(500000, 50))

    listener.start()
    sleep(50)
    listener.firePropertyChangeEvent()
    sleep(10)

    shutdownMock mustHaveNumberOfInvocationsEqual 1
    listener.stop()
  }

  @Test
  def enablingIgnoreOpenEditorsViaPreferences(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = valuesForAnotherCalls(false, true), maxIdlenessLengthMillis = 50, hasOpenEditors = true)

    listener.start()
    listener.firePropertyChangeEvent()
    sleep(90)

    shutdownMock mustHaveNumberOfInvocationsEqual 1
    listener.stop()
  }

  @Test
  def disablingIgnoreOpenEditorsViaPreferences(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = valuesForAnotherCalls(true, false), maxIdlenessLengthMillis = 50, hasOpenEditors = true)

    listener.start()
    listener.firePropertyChangeEvent()
    sleep(90)

    shutdownMock mustHaveNumberOfInvocationsEqual 0
    listener.stop()
  }

  @Test
  def changingManyPreferencesAtOnce(): Unit = {
    val shutdownMock = new MockShutdownFun
    val readIgnoreOpenEditors = valuesForAnotherCalls(false, true, false)

    val listener = createListener(shutdownMock, readIgnoreOpenEditors, maxIdlenessLengthMillis = valuesForAnotherCalls(50000, 20), hasOpenEditors = true,
      closingEnabled = valuesForAnotherCalls( /*start*/ true, /*first change*/ false, /*second change*/ true))

    listener.start()
    sleep(40)

    // first change:
    // enable ignoring open editors
    // decrease max length to lower than current inactivity duration
    // but disable closing
    listener.firePropertyChangeEvent()
    sleep(20)

    shutdownMock mustHaveNumberOfInvocationsEqual 0

    // ignore editors property wasn't even read (as killer task is disabled)
    assertEquals(s"Wrong value of property ${ResourcesPreferences.PRES_COMP_CLOSE_REGARDLESS_OF_EDITORS}", true, readIgnoreOpenEditors())

    // second change:
    // enable closing
    // but disable ignoring open editors
    listener.firePropertyChangeEvent()
    sleep(20)

    shutdownMock mustHaveNumberOfInvocationsEqual 0

    listener.stop()
  }

  @Test
  def ignoreOtherEvents(): Unit = {
    val shutdownMock = new MockShutdownFun
    val listener = createListener(shutdownMock, ignoreOpenEditors = true, maxIdlenessLengthMillis = 10, hasOpenEditors = true, closingEnabled = valuesForAnotherCalls(false, true))

    listener.start()
    sleep(20)

    listener.firePropertyChangeEvent("Something unrelated")
    listener.firePropertyChangeEvent(ResourcesPreferences.PRES_COMP_CLOSE_UNUSED)
    listener.firePropertyChangeEvent(ResourcesPreferences.PRES_COMP_MAX_IDLENESS_LENGTH)
    listener.firePropertyChangeEvent(ResourcesPreferences.PRES_COMP_CLOSE_REGARDLESS_OF_EDITORS)
    sleep(20)

    shutdownMock mustHaveNumberOfInvocationsEqual 0 // events have been ignored - nothing changed

    listener.stop()
  }
}
