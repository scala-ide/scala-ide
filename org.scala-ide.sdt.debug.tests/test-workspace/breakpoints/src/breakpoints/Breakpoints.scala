package breakpoints

import Helper._

class Breakpoints {

  def simple1(): Unit = {
    var x = 0

    while (x < 4) {
      noop(None)
      x += 1
      noop(None)
    }

    x = 0
  }

  def fors(): Unit = {
    for (i <- 1 to 3) {
      noop(None)
      noop(None)
    }

    for (i <- 1 to 3) {
      noop(None)
      noop(None)
    }
  }

  def mainTest(): Unit = {
    println("in mainTest")
    simple1()
    fors()
  }
}

object Breakpoints {

  def main(args: Array[String]): Unit = {
    new Breakpoints().mainTest
  }

}

object Helper {

  def noop(a: Any): Unit = {
  }

  def ret[B](a: B): B= {
     a
  }

}