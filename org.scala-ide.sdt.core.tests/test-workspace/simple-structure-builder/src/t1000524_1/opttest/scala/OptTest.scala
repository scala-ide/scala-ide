package t1000524_1.opttest.scala

object OptTest {
  def getOpt2[T <: Object](opt: Option[T]): T = opt.get
}