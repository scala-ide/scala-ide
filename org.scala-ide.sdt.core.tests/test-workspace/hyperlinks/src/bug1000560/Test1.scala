package bug1000560

object Outer {
  object Inner {
  val c = 2
  }
  val a = 1
  val bbb = 2
}

class Test1 {
  import Outer/*^*/.{bbb/*^*/ => c, a/*^*/}

  import Outer/*^*/._
}