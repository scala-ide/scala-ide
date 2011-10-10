package bug1000656

import util.Box
import util.Full

/** Test that hyperlinking works for definitions defined in a dependent project. */
class Client {
  def foo {
    val b: Box[Int] = null 

    val t: b.myInt/*^*/ = 10
    
    val x = Full.apply/*^*/("a") 
  }
}
