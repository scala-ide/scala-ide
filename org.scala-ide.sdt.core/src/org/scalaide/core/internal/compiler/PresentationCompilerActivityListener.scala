/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.core.internal.compiler

import java.util.Timer
import java.util.TimerTask
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.scalaide.core.IScalaPlugin
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.preferences.ResourcesPreferences
import scala.collection.mutable.Subscriber
import org.scalaide.core.internal.compiler.PresentationCompilerActivity
import org.scalaide.core.internal.compiler.PresentationCompilerProxy
import org.scalaide.core.internal.compiler._

/**
 * Tracks activity of ScalaPresentationCompiler and shuts it down if it's unused sufficiently long
 * and there are no open files in editor which are related to it
 * @param projectName name shown in logs
 * @param projectHasOpenEditors checks whether there are currently open editors for this project
 * @param shutdownPresentationCompiler function which should be invoked, when SPC should be closed
 */
class PresentationCompilerActivityListener(projectName: String, projectHasOpenEditors: => Boolean, shutdownPresentationCompiler: () => Unit)
  extends Subscriber[PresentationCompilerActivity, PresentationCompilerProxy] with HasLogger {

  import PresentationCompilerActivityListener.prefStore
  import PresentationCompilerActivityListener.timer

  private var started = false

  /**
   * Timestamp that indicates last time when related presentation compiler was used
   */
  @volatile private var pcLastActivityTime = 0L

  private var closingEnabled = true
  private var maxIdlenessLengthMillis = 120000L
  private var ignoreOpenEditors = false

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
        startKillerTask()
      else
        stopKillerTask()

    private def onKillerPropertiesChanged(): Unit =
      if (closingEnabled) updateKillerTask()
  }

  private class PresentationCompilerKillerTask extends TimerTask {

    override def run(): Unit = lock.synchronized {
      if (killerTask == this) { // otherwise killer task has been closed / replaced in meantime (this task couldn't be canceled because it started execution) so we don't need this one anymore
        try {
          if (!isTimeToBeKilled)
            scheduleNextCheck(remainingDelayToNextCheck)
          else if (!ignoreOpenEditors && projectHasOpenEditors)
            scheduleNextCheck(delay = maxIdlenessLengthMillis)
          else {
            logger.info(s"Presentation compiler for project $projectName will be shut down due to inactivity")
            shutdownPresentationCompiler()
          }
        } catch {
          case e: Throwable => logger.error(s"Unexpected error occurred during running presentation compiler killer task for project $projectName", e)
        }
      }
    }

    private def isTimeToBeKilled = pcLastActivityTime + maxIdlenessLengthMillis <= System.currentTimeMillis()
  }

  def start(): Unit = lock.synchronized {
    if (!started) {
      logger.debug(s"Starting PresentationCompilerActivityListener for project $projectName")
      noteActivity()
      closingEnabled = readClosingEnabled
      if (closingEnabled) startKillerTask()
      prefStore.addPropertyChangeListener(propertyChangeListener)
      started = true
    }
  }

  def stop(): Unit = lock.synchronized {
    if (started) {
      logger.debug(s"Stopping PresentationCompilerActivityListener for project $projectName")
      // we don't need it, as preferences anyway will be updated during another start
      prefStore.removePropertyChangeListener(propertyChangeListener)

      stopKillerTask()
      started = false
    }
  }

  def noteActivity(): Unit = { pcLastActivityTime = System.currentTimeMillis() }

  // to make this class testable
  protected def readClosingEnabled = PresentationCompilerActivityListener.closingEnabled
  protected def readIgnoreOpenEditors = PresentationCompilerActivityListener.shouldCloseRegardlessOfOpenEditors
  protected def readMaxIdlenessLengthMillis = PresentationCompilerActivityListener.currentMaxIdlenessLengthMillis

  private def updateKillerTask(): Unit = {
      stopKillerTask()
      startKillerTask()
    }

  private def stopKillerTask(): Unit =
    if (killerTask != null) {
      killerTask.cancel()
      killerTask = null
    }

  private def startKillerTask(): Unit = {
    maxIdlenessLengthMillis = readMaxIdlenessLengthMillis
    ignoreOpenEditors = readIgnoreOpenEditors
    scheduleNextCheck(remainingDelayToNextCheck)
  }

  private def scheduleNextCheck(delay: Long): Unit = {
    killerTask = new PresentationCompilerKillerTask
    timer.schedule(killerTask, delay)
  }

  private def remainingDelayToNextCheck = math.max(0, maxIdlenessLengthMillis + pcLastActivityTime - System.currentTimeMillis())

  def notify(pub: PresentationCompilerProxy, event: PresentationCompilerActivity): Unit = event match {
    case Shutdown => stop()
    case Start    => start()
    case Activity => noteActivity()
  }
}

object PresentationCompilerActivityListener {

  private val timer: Timer = new Timer( /*isDaemon =*/ true)

  private val prefStore = IScalaPlugin().getPreferenceStore()

  private def closingEnabled: Boolean = prefStore.getBoolean(ResourcesPreferences.PRES_COMP_CLOSE_UNUSED)

  private def currentMaxIdlenessLengthMillis: Long = prefStore.getInt(ResourcesPreferences.PRES_COMP_MAX_IDLENESS_LENGTH) * 1000L

  private def shouldCloseRegardlessOfOpenEditors: Boolean = prefStore.getBoolean(ResourcesPreferences.PRES_COMP_CLOSE_REGARDLESS_OF_EDITORS)
}
