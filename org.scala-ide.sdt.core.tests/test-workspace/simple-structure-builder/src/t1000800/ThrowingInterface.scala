package t1000800

trait ThrowingInterface {
  @throws(classOf[Exception])
  def throwingMethod()
}