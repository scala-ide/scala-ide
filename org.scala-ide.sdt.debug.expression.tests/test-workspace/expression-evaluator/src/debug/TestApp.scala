package debug

trait TestApp extends App {
  val delay = System.getProperty("remoteDebugDelay")
  if (delay != null)
    Thread.sleep(delay.toLong)
}