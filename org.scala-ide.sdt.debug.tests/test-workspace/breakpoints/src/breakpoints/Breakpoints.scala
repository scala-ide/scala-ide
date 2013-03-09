package breakpoints

import Helper._

class Breakpoints {

  def simple1() {
    var x = 0

    while (x < 4) {
      noop(None)
      x += 1
      noop(None)
    }

    x = 0
  }

  def fors() {
    for (i <- 1 to 3) {
      noop(None)
      noop(None)
    }

    for (i <- 1 to 3) {
      noop(None)
      noop(None)
    }
  }

  def mainTest() {
    println("in mainTest")
    simple1()
    fors()
  }
}

object Breakpoints {

  def main(args: Array[String]) {
    new Breakpoints().mainTest
  }

}

object Helper {

  def noop(a: Any) {
  }

  def ret[B](a: B): B= {
     a
  }

}