package t1001921 {
  class Ticket1001921 {
    def foo(): Unit = {
      new Bar().bar/*^*/()
    }
  }

  class Bar {
    def bar(b: Int = 2): Unit = {}
  }
}