class ReferredClass/*ref*/ {}

class ReferringClass {
  def foo = {
    println(new ReferredClass().toString);
  }

  def bar = {
    println(new ReferredClass().toString);
  }
}