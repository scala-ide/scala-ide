package t1000524_neg.opttest.scala

class OptTest {
  def getOpt1[T](opt: Option[T]): T = opt.get
}

object OptTest {
  def getOpt1[T](opt: Option[T]): T = opt.get
}