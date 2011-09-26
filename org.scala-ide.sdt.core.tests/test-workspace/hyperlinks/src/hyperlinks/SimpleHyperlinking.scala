package hyperlinks

/** This test uses the ^ as a marker for where hyperlinking should be 
 *  automatically tested. 
 */

class SimpleHyperlinking {
  type Tpe[T] = List/*^*/[T]
  
  def foo(xs: Tpe/*^*/[Int]) = {
    val arr = Array/*^*/(1, 2, 3)
    val sum = xs.sum/*^*/
    val x: String/*^*/ = "Hello, world"
  }
}