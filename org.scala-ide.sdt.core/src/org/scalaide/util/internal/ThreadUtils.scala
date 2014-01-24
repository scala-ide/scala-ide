package scala.tools.eclipse.util

import java.util.concurrent.locks.Lock

object ThreadUtils {

  def withLock[T](lock: Lock)(f: => T): T = {
    lock.lock()
    try
      f
    finally
      lock.unlock()
  }

}