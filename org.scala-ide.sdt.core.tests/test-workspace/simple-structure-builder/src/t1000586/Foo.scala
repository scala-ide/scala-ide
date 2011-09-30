package t1000586

class Foo {
  def getFoos: Array[Foo] = Array()
  def getFoos2: Option[Array[Foo]] = Some(getFoos)
}