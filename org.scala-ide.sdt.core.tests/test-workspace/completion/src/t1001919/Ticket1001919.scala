package t1001919

object Ticket1001919 {
  def doNothingWith(that: Any): Unit = {}
  def doNothingWith(that: String): Unit = {}
}

class Ticket1001919 {
  import Ticket1001919.doNo /*!*/
}