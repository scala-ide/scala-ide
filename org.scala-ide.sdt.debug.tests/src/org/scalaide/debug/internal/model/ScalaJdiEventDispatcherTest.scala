package org.scalaide.debug.internal.model

import org.junit.Test
import scala.concurrent.Future
import org.junit.Assert
import scala.concurrent.ExecutionContext
import scala.concurrent.Await

class ScalaJdiEventDispatcherTest {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._

  @Test def shouldRunDispatchLoopForASecond(): Unit = {
    @volatile var running = true
    @volatile var probe = 0
    val colaborator: () => Future[Unit] = () => Future { probe += 1 }

    val tested = Dispatch(running)(colaborator)

    Thread.sleep(1000)
    running = false
    Await.ready(tested, 100 millis)
    Assert.assertTrue(probe > 1)
  }

  @Test def shouldStopToRunDispatchLoop(): Unit = {
    val running = false
    @volatile var probe = 0
    val colaborator: () => Future[Unit] = () => Future { probe += 1 }

    val tested = Dispatch(running)(colaborator)

    Thread.sleep(1000)
    Await.ready(tested, 100 millis)
    Assert.assertTrue(probe == 0)
  }

  @Test def shouldRecoverAfterException(): Unit = {
    @volatile var running = true
    @volatile var probe = 0
    @volatile var recovered = 0
    val throwingColaborator: () => Future[Unit] = () => Future { throw new Exception }
    val colaborator: () => Future[Unit] = () => Future { probe += 1 }

    val tested = Dispatch(running)(throwingColaborator) recoverWith {
      case any: Exception =>
        recovered += 1
        Dispatch(running)(colaborator)
    }

    Thread.sleep(1000)
    running = false
    Await.ready(tested, 100 millis)
    Assert.assertTrue(probe > 1)
    Assert.assertTrue(recovered == 1)
  }
}
