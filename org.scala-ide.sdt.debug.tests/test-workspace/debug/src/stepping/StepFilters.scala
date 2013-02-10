package stepping

class StepFilters {
  var mutableVar = "var"

  val immutableVal = "val"

  def foo(a: String, b: String) {}

  def mainTest() {
    mutableVar  // line 11

    immutableVal

    mutableVar = immutableVal

    foo(mutableVar, immutableVal)

    fors(); bridges()
  }

  def fors() {
    val lst = List("one", "two", "three")

    for (n <- lst) {      // line 25
      debug.Helper.noop(immutableVal)
      println(n)
    }
  }

  def bridges() {
    val c: Base[Int] = new Concrete

    c.base(10)  // line 34
    println(c.base(10))

    2 + c.base(10)
    debug.Helper.noop(null)
  }

}

class Base[T] {
  def base(x: T): Int = 0
}

class Concrete extends Base[Int] {
  override def base(x: Int): Int = {
    println(x) // line 49
    x
  }
}


object StepFilters {

  def main(args: Array[String]) {
    new StepFilters().mainTest()
  }

}
