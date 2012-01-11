package ticket_1000772

class ForTesting {
  def method(param1: String, param2: String)(secondSectionParam1: Int)(implicit x: Int) {
  }
}

class Foo {

  def bar {
    val x: ForTesting = null

    x.m /*!*/
  }
}
