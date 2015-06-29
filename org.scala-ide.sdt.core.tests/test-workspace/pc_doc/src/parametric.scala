class T[A] {
  /**
   * @todo implement me
   */
  def foo(): Unit
}

class U {
  val x = new T[String]()
  x./*s*/foo/*e*/

}
