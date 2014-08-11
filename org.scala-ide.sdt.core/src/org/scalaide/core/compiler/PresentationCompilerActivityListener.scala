/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.core.compiler

import java.util.Timer
import java.util.TimerTask

import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.scalaide.core.ScalaPlugin
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.preferences.ResourcesPreferences

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

  private var started = false

  /**
   * Timestamp that indicates last time when related presentation compiler was used
   */
  @volatile private var pcLastActivityTime: Long = 0

  private var closingEnabled: Boolean = true

  private var timer: Timer = null

  /**
   * Checks length of inactivity and shuts down presentation compiler when it's needed
   */
  private var killerTask: TimerTask = null

  private val lock = new Object

  protected val propertyChangeListener = new KillerTaskPropertyChangeListener

  protected class KillerTaskPropertyChangeListener extends IPropertyChangeListener {

    override def propertyChange(event: PropertyChangeEvent): Unit = event.getProperty match {
      case ResourcesPreferences.PRES_COMP_PREFERENCES_CHANGE_MARKER => lock.synchronized {
        val prevClosingEnabled = closingEnabled
        closingEnabled = readClosingEnabled

        if (prevClosingEnabled != closingEnabled)
          onClosingEnabledChanged()
        else
          onKillerPropertiesChanged()
      }
      case _ =>
    }

    private def onClosingEnabledChanged(): Unit =
      if (closingEnabled)
        startTimer()
      else
        stopTimer()

    private def onKillerPropertiesChanged(): Unit =
      if (closingEnabled) updateKillerTask()
  }

  private class PresentationCompilerKillerTask(killAfterMillis: Long, ignoreOpenEditors: Boolean) extends TimerTask {

    override def run(): Unit = lock.synchronized {
      if (killerTask == this) { // otherwise killer task has been closed / replaced in meantime (this task couldn't be canceled because it started execution) so we don't need this one anymore
        try {
          if (!isTimeToBeKilled) {
            val delay = remainingDelayToNextCheck(killAfterMillis)
            scheduleNextCheck(delay)
          } else if (!ignoreOpenEditors && projectHasOpenEditors())
            scheduleNextCheck(delay = killAfterMillis)
          else
            shutdownPresentationCompiler()
        } catch {
          case e: Throwable => logger.error(s"Unexpected error occurred during running presentation compiler killer task for project $projectName", e)
        }
      }
    }

    private def isTimeToBeKilled = pcLastActivityTime + killAfterMillis <= System.currentTimeMillis()

    private def scheduleNextCheck(delay: Long): Unit = {
      killerTask = new PresentationCompilerKillerTask(killAfterMillis, ignoreOpenEditors)
      timer.schedule(killerTask, delay)
    }
  }

  def start(): Unit = lock.synchronized {
    if (!started) {
      logger.debug(s"Starting PresentationCompilerActivityListener for project $projectName")
      noteActivity()
      closingEnabled = readClosingEnabled
      if (closingEnabled) startTimer()
      prefStore.addPropertyChangeListener(propertyChangeListener)
      started = true
    }
  }

  def stop(): Unit = lock.synchronized {
    if (started) {
      logger.debug(s"Stopping PresentationCompilerActivityListener for project $projectName")
      // we don't need it, as preferences anyway will be updated during another start
      prefStore.removePropertyChangeListener(propertyChangeListener)

      stopTimer()
      started = false
    }
  }

  def noteActivity(): Unit = { pcLastActivityTime = System.currentTimeMillis() }

  // to make this class testable
  protected def readClosingEnabled = PresentationCompilerActivityListener.closingEnabled
  protected def readIgnoreOpenEditors = PresentationCompilerActivityListener.shouldCloseRegardlessOfOpenEditors
  protected def readMaxIdlenessLengthMillis = PresentationCompilerActivityListener.currentMaxIdlenessLengthMillis

  private def startTimer(): Unit =
    if (timer == null) {
      timer = new Timer( /*isDaemon =*/ true)
      startKillerTask()
    }

  private def stopTimer(): Unit =
    if (timer != null) {
      stopKillerTask()
      timer.cancel()
      timer = null
    }

  private def updateKillerTask(): Unit =
    if (timer != null) {
      stopKillerTask()
      startKillerTask()
    }

  private def stopKillerTask(): Unit =
    if (killerTask != null) {
      killerTask.cancel()
      killerTask = null
    }

  private def startKillerTask(): Unit = {
    val maxIdlenessLengthMillis = readMaxIdlenessLengthMillis
    killerTask = new PresentationCompilerKillerTask(maxIdlenessLengthMillis, readIgnoreOpenEditors)
    val checkDelay = remainingDelayToNextCheck(maxIdlenessLengthMillis)
    timer.schedule(killerTask, checkDelay)
  }

  private def remainingDelayToNextCheck(killAfterMillis: Long) = math.max(0, killAfterMillis + pcLastActivityTime - System.currentTimeMillis())
}

object PresentationCompilerActivityListener {
  private val prefStore = ScalaPlugin.prefStore

  private def closingEnabled: Boolean = prefStore.getBoolean(ResourcesPreferences.PRES_COMP_CLOSE_UNUSED)

  private def currentMaxIdlenessLengthMillis: Long = prefStore.getInt(ResourcesPreferences.PRES_COMP_MAX_IDLENESS_LENGTH) * 1000L

  private def shouldCloseRegardlessOfOpenEditors: Boolean = prefStore.getBoolean(ResourcesPreferences.PRES_COMP_CLOSE_REGARDLESS_OF_EDITORS)
}
