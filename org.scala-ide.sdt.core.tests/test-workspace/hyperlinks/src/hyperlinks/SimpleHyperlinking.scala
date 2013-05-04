package hyperlinks

/** This test uses the ^ as a marker for where hyperlinking should be
 *  automatically tested.
 */

class SimpleHyperlinking {
  type Tpe[T] = Set/*^*/[T]

  def foo(xs: Tpe/*^*/[Int]) = {
    val arr = Array/*^*/(1, 2, 3)
    val sum = xs.sum/*^*/
    val x: String/*^*/ = "Hello, world"
    val Some/*^*/(x): Option/*^*/[Int] = Some(10)
    classOf[String/*^*/]
  }
}