package t1000524_1.opttest.scala

import java.util.List

object OptTest {
  def getOpt1[T <: Object](opt: Option[T]): T = opt.get

  def getOpt2[T <: Comparable[T]](opt: Option[T]): T = opt.get

  def getOpt3[T <: Comparable[T], S <: Comparable[S]](opt: Option[T], xs: List[S]): T = opt.get
}
