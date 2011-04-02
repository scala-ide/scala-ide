package scala.tools.eclipse.semicolon

import java.util.Date
import java.util.concurrent.locks._
import scala.tools.eclipse.util.SWTUtils._
import scala.tools.eclipse.util.ThreadUtils._

object TypingDelayHelper {

  val DELAY = 300 // milliseconds

}

/**
 * Provides callbacks after no typing has occurred for a period.
 */
class TypingDelayHelper {

  import TypingDelayHelper._

  private val lock = new ReentrantLock

  private val condition = lock.newCondition

  private var active = true

  /**
   * A callback and a time to fire it.
   */
  private var nextScheduledEventOpt: Option[(Date, () => Any)] = None

  private object SchedulerThread extends Thread {

    setName(classOf[TypingDelayHelper].getSimpleName)

    override def run = loop()

  }

  SchedulerThread.start()

  /**
   * Schedule a callback on the UI thread (clearing any existing scheduled callback)
   */
  def scheduleCallback(f: => Any) = withLock(lock) {
    val timeToFireEvent = new Date(System.currentTimeMillis + DELAY)
    nextScheduledEventOpt = Some(timeToFireEvent, () => f)
    condition.signal()
    SchedulerThread.interrupt()
  }

  def stop() = withLock(lock) {
    nextScheduledEventOpt = None
    active = false
    condition.signal()
    SchedulerThread.interrupt()
  }

  private def loop() =
    while (active) {
      val timeToSleep = withLock(lock) {
        while (active && nextScheduledEventOpt == None)
          try
            condition.await()
          catch {
            case _: InterruptedException =>
          }
        if (active) {
          val (nextScheduledTime, callback) = nextScheduledEventOpt.get
          val now = new Date
          if (now.before(nextScheduledTime)) {
            nextScheduledTime.getTime - now.getTime
          } else {
            asyncExec(callback())
            nextScheduledEventOpt = None
            0
          }
        } else
          0
      }
      try
        Thread.sleep(timeToSleep)
      catch {
        case _: InterruptedException =>
      }

    }

}