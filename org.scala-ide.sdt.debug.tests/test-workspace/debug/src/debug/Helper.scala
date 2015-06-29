package debug

object Helper {

  def noop(a: Any): Unit = {
  }

  def ret[B](a: B): B= {
     a
  }

}