package t1000711

object Nested {
  trait A {
    trait A
    class B
    object C
  }
  
  class B {
    trait A
    class B
    object C
  }
  
  object C {
    trait A
    class B
    object C
  }
}