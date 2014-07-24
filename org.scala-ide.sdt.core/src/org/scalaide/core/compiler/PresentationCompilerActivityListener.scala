/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/license.html
 */
package org.scalaide.core.compiler

import java.util.Timer
import java.util.TimerTask

import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.scalaide.core.ScalaPlugin
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.preferences.ScalaPreferences

/**
 * Tracks activity of ScalaPresentationCompiler and shuts it down if it's unused sufficiently long
 * and there are no open files in editor which are related to it
 * @param projectName name shown in logs
 * @param projectHasOpenEditors function checking whether there are currently open editors for this project
 * @param shutdownPresentationCompiler function which should be invoked, when SPC should be closed
 */
class PresentationCompilerActivityListener(projectName: String, projectHasOpenEditors: () => Boolean, shutdownPresentationCompiler: () => Unit)
  extends HasLogger {

  import PresentationCompilerActivityListener.prefStore

  /**
   * Timestamp that indicates last time when related presentation compiler was used
   */
  @volatile private var pcLastActivityTime: Long = 0

  private var maxIdlenessLengthMillis: Long = 0 // when == 0, killer task should be turned off - and vice versa

  /**
   * Wheter idle presentation compiler should be closed even if there are open editors for related project
   */
  @volatile private var ignoreOpenEditors: Boolean = false

  private var timer: Timer = null

  /**
   * Checks length of inactivity and shuts down presentation compiler when it's needed
   */
  private var killerTask: TimerTask = null

  private val taskLock = new Object

  private lazy val propertyChangeListener = new KillerTaskPropertyChangeListener

  private class KillerTaskPropertyChangeListener extends IPropertyChangeListener {

    def propertyChange(event: PropertyChangeEvent): Unit = {
      if (event.getProperty.equals(ScalaPreferences.PRES_COMP_MAX_IDLENESS_LENGTH))
        taskLock.synchronized {
          updateKillerTask()
        }
      else if (event.getProperty.equals(ScalaPreferences.PRES_COMP_CLOSE_REGARDLESS_OF_EDITORS))
        ignoreOpenEditors = event.getNewValue().asInstanceOf[Boolean]
    }
  }

  private class PresentationCompilerKillerTask(killAfterMillis: Long) extends TimerTask {

    override def run(): Unit =
      try {
        if (!isTimeToBeKilled) {
          val delay = remainingDelayToNextCheck(killAfterMillis)
          scheduleNextCheck(delay)
        } else if (!ignoreOpenEditors && projectHasOpenEditors()) {
          scheduleNextCheck(delay = killAfterMillis)
        } else {
          shutdownPresentationCompiler()
        }
      } catch {
        case e: Throwable => logger.error(s"Unexpected error occurred during running presentation compiler killer task for project $projectName", e)
      }

    private def isTimeToBeKilled = pcLastActivityTime + killAfterMillis <= System.currentTimeMillis()

    private def scheduleNextCheck(delay: Long): Unit = timer.schedule(new PresentationCompilerKillerTask(killAfterMillis), delay)
  }

  def start(): Unit = taskLock.synchronized {
    if (timer == null) {
      logger.debug(s"Starting PresentationCompilerActivityListener for project $projectName")
      noteActivity()
      ignoreOpenEditors = readIgnoreOpenEditors
      timer = new Timer( /*isDaemon =*/ true)
      updateKillerTask()
      prefStore.addPropertyChangeListener(propertyChangeListener)
    }
  }

  def stop(): Unit = taskLock.synchronized {
    if (timer != null) {
      logger.debug(s"Stopping PresentationCompilerActivityListener for project $projectName")
      // we don't need it, as preferences anyway will be updated during another start
      prefStore.removePropertyChangeListener(propertyChangeListener)

      stopKillerTask()
      timer.cancel()
      timer = null
      maxIdlenessLengthMillis = 0
    }
  }

  def noteActivity(): Unit = { pcLastActivityTime = System.currentTimeMillis() }

  // to make this class testable
  protected def readIgnoreOpenEditors = PresentationCompilerActivityListener.shouldCloseRegardlessOfOpenEditors
  protected def readMaxIdlenessLengthMillis = PresentationCompilerActivityListener.currentMaxIdlenessLengthMillis

  private def updateKillerTask(): Unit =
    if (timer != null) {
      val newMaxIdlenessLengthMillis = readMaxIdlenessLengthMillis
      val maxLengthChanged = maxIdlenessLengthMillis != newMaxIdlenessLengthMillis
      maxIdlenessLengthMillis = newMaxIdlenessLengthMillis

      if (maxLengthChanged) {
        stopKillerTask()
        if (newMaxIdlenessLengthMillis != 0)
          runKillerTask(newMaxIdlenessLengthMillis)
      }
    }

  private def runKillerTask(killAfterMillis: Long): Unit = {
    killerTask = new PresentationCompilerKillerTask(killAfterMillis)
    val checkDelay = remainingDelayToNextCheck(killAfterMillis)
    timer.schedule(killerTask, checkDelay)
  }

  private def stopKillerTask(): Unit =
    if (killerTask != null) {
      killerTask.cancel()
      killerTask = null
    }

  private def remainingDelayToNextCheck(killAfterMillis: Long) = math.max(0, killAfterMillis + pcLastActivityTime - System.currentTimeMillis())
}

object PresentationCompilerActivityListener {
  private val prefStore = ScalaPlugin.prefStore

  private def currentMaxIdlenessLengthMillis: Long = prefStore.getInt(ScalaPreferences.PRES_COMP_MAX_IDLENESS_LENGTH) * 60000L

  private def shouldCloseRegardlessOfOpenEditors: Boolean = prefStore.getBoolean(ScalaPreferences.PRES_COMP_CLOSE_REGARDLESS_OF_EDITORS)
}
