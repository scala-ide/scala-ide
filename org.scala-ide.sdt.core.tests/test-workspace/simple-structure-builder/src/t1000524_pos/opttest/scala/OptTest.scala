package t1000524_pos.opttest.scala

object OptTest {
  def getOpt1[T](opt: Option[T]): T = opt.get
}