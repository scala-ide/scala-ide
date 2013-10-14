package t1001125

object Ticket1001125 {
  def doNothingWith(that: Any): Unit = {}
}

class Ticket1001125 {
  import Ticket1001125.doNo /*!*/
}